package com.ddakta.matching.algorithm

import com.ddakta.matching.client.LocationServiceClient
import com.ddakta.matching.config.MatchingProperties
import com.ddakta.matching.domain.entity.MatchingRequest
import com.ddakta.matching.domain.entity.Ride
import com.ddakta.matching.domain.vo.Location
import com.ddakta.matching.dto.internal.AvailableDriver
import com.ddakta.matching.domain.repository.MatchingRequestRepository
import com.ddakta.matching.domain.repository.RideRepository
import com.ddakta.matching.service.DriverCallService
import mu.KotlinLogging
import org.redisson.api.RedissonClient
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * 배치 매칭 프로세서
 *
 * 대량의 매칭 요청을 효율적으로 처리하기 위한 배치 처리 시스템입니다.
 * 주요 장점:
 * 1. API 호출 최적화: 여러 요청을 한 번에 처리하여 location-service 호출을 줄입니다
 * 2. 전역 최적화: 개별 매칭보다 전체적으로 더 나은 매칭 결과를 도출합니다
 * 3. 처리량 향상: 배치 처리로 시스템 처리량을 크게 향상시킵니다
 *
 * 동작 방식:
 * - 설정된 간격(기본 1초)마다 대기 중인 매칭 요청을 수집
 * - 헝가리안 알고리즘을 사용하여 최적 매칭 계산
 * - 분산 환경에서의 중복 처리 방지를 위해 Redisson 분산 락 사용
 */
@Component
class BatchMatchingProcessor(
    private val matchingRequestRepository: MatchingRequestRepository,
    private val rideRepository: RideRepository,
    private val locationServiceClient: LocationServiceClient,
    private val hungarianAlgorithm: HungarianAlgorithm,
    private val matchingScoreCalculator: MatchingScoreCalculator,
    private val driverCallService: DriverCallService,
    private val redissonClient: RedissonClient,
    private val matchingProperties: MatchingProperties
) {

    private val logger = KotlinLogging.logger {}
    private val processingLock = "matching:batch:lock"

    /**
     * 배치 매칭 처리 메인 메서드
     *
     * @Scheduled: application.yml의 matching.batch.interval 값에 따라 주기적으로 실행
     * @Transactional: 전체 배치 처리를 하나의 트랜잭션으로 처리하여 일관성 보장
     *
     * 처리 단계:
     * 1. Redisson 분산 락 획득 (중복 처리 방지)
     * 2. 대기 중인 매칭 요청 조회
     * 3. 각 요청에 대한 근처 드라이버 정보 수집
     * 4. 비용 행렬 생성 및 헝가리안 알고리즘 실행
     * 5. 최적 매칭에 따른 드라이버 호출
     * 6. 결과 처리 및 상태 업데이트
     */
    @Scheduled(fixedDelayString = "\${matching.batch.interval}")
    @Transactional
    fun processBatch() {
        val lock = redissonClient.getLock(processingLock)
        // 5초 동안 락을 기다리고, 60초 동안 락을 유지합니다.
        val lockAcquired = lock.tryLock(5, 60, TimeUnit.SECONDS)

        if (!lockAcquired) {
            logger.debug { "Failed to acquire batch processing lock, another process is running." }
            return
        }

        try {
            val batchId = UUID.randomUUID()
            val startTime = System.currentTimeMillis()

            val requests = fetchPendingRequests()
            if (requests.isEmpty()) {
                return
            }

            logger.info { "Processing batch $batchId with ${requests.size} requests" }

            markRequestsAsProcessing(requests, batchId)

            val requestsWithData = prepareMatchingData(requests)
            if (requestsWithData.isEmpty()) {
                logger.warn { "No valid requests with data to process" }
                return
            }

            val allDrivers = requestsWithData
                .flatMap { it.nearbyDrivers }
                .distinctBy { it.driverId }

            if (allDrivers.isEmpty()) {
                logger.warn { "No available drivers found for batch $batchId" }
                failAllRequests(requestsWithData.map { it.request }, "No available drivers")
                return
            }

            val costMatrix = matchingScoreCalculator.calculateCostMatrix(
                requestsWithData.map { it.request to it.pickupLocation },
                allDrivers
            )

            val assignments = hungarianAlgorithm.findOptimalMatching(costMatrix)

            processMatchingResults(requestsWithData, allDrivers, assignments)

            val processingTime = System.currentTimeMillis() - startTime
            logger.info { "Batch $batchId processed in ${processingTime}ms" }

        } catch (e: Exception) {
            logger.error(e) { "Error processing matching batch" }
        } finally {
            if (lock.isHeldByCurrentThread) {
                lock.unlock()
            }
        }
    }

    private fun fetchPendingRequests(): List<MatchingRequest> {
        return matchingRequestRepository.findPendingRequests(
            maxAge = LocalDateTime.now().minusSeconds(10),
            limit = matchingProperties.batch.size
        )
    }

    private fun markRequestsAsProcessing(requests: List<MatchingRequest>, batchId: UUID) {
        requests.forEach { request ->
            request.startProcessing(batchId)
        }
        matchingRequestRepository.saveAll(requests)
    }

    private fun prepareMatchingData(requests: List<MatchingRequest>): List<RequestWithData> {
        return requests.mapNotNull { request ->
            try {
                val ride = rideRepository.findById(request.rideId).orElse(null)
                if (ride == null) {
                    logger.warn { "Ride ${request.rideId} not found for matching request" }
                    request.fail("Ride not found")
                    matchingRequestRepository.save(request)
                    return@mapNotNull null
                }

                val nearbyDrivers = locationServiceClient.findNearbyDrivers(
                    h3Index = request.pickupH3,
                    radiusKm = matchingProperties.search.radiusKm,
                    limit = matchingProperties.search.maxDrivers
                )

                RequestWithData(
                    request = request,
                    ride = ride,
                    pickupLocation = ride.pickupLocation,
                    nearbyDrivers = nearbyDrivers
                )
            } catch (e: Exception) {
                logger.error(e) { "Error preparing data for request ${request.id}" }
                request.fail("Error preparing matching data: ${e.message}")
                matchingRequestRepository.save(request)
                null
            }
        }
    }

    private fun processMatchingResults(
        requestsWithData: List<RequestWithData>,
        allDrivers: List<AvailableDriver>,
        assignments: IntArray
    ) {
        val assignedDrivers = mutableSetOf<UUID>()

        requestsWithData.forEachIndexed { requestIndex, requestData ->
            val assignedDriverIndex = assignments[requestIndex]

            if (assignedDriverIndex != -1 && assignedDriverIndex < allDrivers.size) {
                val assignedDriver = allDrivers[assignedDriverIndex]

                if (assignedDriver.driverId in assignedDrivers) {
                    handleNoDriverAvailable(requestData)
                    return@forEachIndexed
                }

                val eligibleDrivers = requestData.nearbyDrivers
                    .filter { it.driverId !in assignedDrivers }
                    .filter { matchingScoreCalculator.isDriverEligible(it) }
                    .map { driver ->
                        val score = matchingScoreCalculator.calculateScore(
                            requestData.request,
                            driver,
                            requestData.pickupLocation
                        )
                        driver to score
                    }

                val selectedDrivers = matchingScoreCalculator.selectTopDrivers(
                    eligibleDrivers,
                    matchingProperties.driverCall.maxDrivers
                )

                if (selectedDrivers.isNotEmpty()) {
                    selectedDrivers.forEach { assignedDrivers.add(it.driverId) }

                    driverCallService.createDriverCalls(
                        ride = requestData.ride,
                        drivers = selectedDrivers,
                        pickupLocation = requestData.pickupLocation
                    )

                    requestData.request.complete()
                } else {
                    handleNoDriverAvailable(requestData)
                }
            } else {
                handleNoDriverAvailable(requestData)
            }
        }

        matchingRequestRepository.saveAll(requestsWithData.map { it.request })
    }

    private fun handleNoDriverAvailable(requestData: RequestWithData) {
        logger.warn { "No drivers available for ride ${requestData.ride.id}" }
        requestData.request.fail("No available drivers")
    }

    private fun failAllRequests(requests: List<MatchingRequest>, reason: String) {
        requests.forEach { it.fail(reason) }
        matchingRequestRepository.saveAll(requests)
    }

    data class RequestWithData(
        val request: MatchingRequest,
        val ride: Ride,
        val pickupLocation: Location,
        val nearbyDrivers: List<AvailableDriver>
    )
}