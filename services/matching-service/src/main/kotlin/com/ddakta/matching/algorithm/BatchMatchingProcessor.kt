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
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.LocalDateTime
import java.util.*

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
 * - 분산 환경에서의 중복 처리 방지를 위해 Redis 락 사용
 */
@Component
class BatchMatchingProcessor(
    private val matchingRequestRepository: MatchingRequestRepository,
    private val rideRepository: RideRepository,
    private val locationServiceClient: LocationServiceClient,
    private val hungarianAlgorithm: HungarianAlgorithm,
    private val matchingScoreCalculator: MatchingScoreCalculator,
    private val driverCallService: DriverCallService,
    private val redisTemplate: RedisTemplate<String, String>,
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
     * 1. 분산 락 획득 (중복 처리 방지)
     * 2. 대기 중인 매칭 요청 조회
     * 3. 각 요청에 대한 근처 드라이버 정보 수집
     * 4. 비용 행렬 생성 및 헝가리안 알고리즘 실행
     * 5. 최적 매칭에 따른 드라이버 호출
     * 6. 결과 처리 및 상태 업데이트
     */
    @Scheduled(fixedDelayString = "\${matching.batch.interval}")
    @Transactional
    fun processBatch() {
        // 분산 록 획득 시도
        // Redis를 사용하여 여러 인스턴스가 동시에 배치를 처리하는 것을 방지합니다
        val lockAcquired = acquireLock()
        if (!lockAcquired) {
            logger.debug { "Failed to acquire batch processing lock" }
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

            // 요청을 처리 중으로 표시
            // 다른 프로세스가 동일한 요청을 처리하지 않도록 상태를 즉시 업데이트합니다
            markRequestsAsProcessing(requests, batchId)

            // 각 요청에 대한 운행 세부 정보 및 근처 드라이버 조회
            // location-service를 통해 각 픽업 위치 주변의 가용 드라이버를 조회합니다
            // 성능 최적화: 가능한 경우 배치 API 호출을 사용합니다
            val requestsWithData = prepareMatchingData(requests)
            if (requestsWithData.isEmpty()) {
                logger.warn { "No valid requests with data to process" }
                return
            }

            // 모든 고유 드라이버 가져오기
            val allDrivers = requestsWithData
                .flatMap { it.nearbyDrivers }
                .distinctBy { it.driverId }

            if (allDrivers.isEmpty()) {
                logger.warn { "No available drivers found for batch $batchId" }
                failAllRequests(requestsWithData.map { it.request }, "No available drivers")
                return
            }

            // 비용 행렬 구축
            // 각 승객-드라이버 쌍에 대한 매칭 비용을 계산합니다
            // 비용은 거리(70%), 드라이버 평점(20%), 수락률(10%)의 가중 평균입니다
            val costMatrix = matchingScoreCalculator.calculateCostMatrix(
                requestsWithData.map { it.request to it.pickupLocation },
                allDrivers
            )

            // 헝가리안 알고리즘 실행
            // O(n³) 시간 복잡도로 전역 최적 매칭을 찾습니다
            // 결과는 각 승객에게 할당된 드라이버의 인덱스 배열입니다
            val assignments = hungarianAlgorithm.findOptimalMatching(costMatrix)

            // 매칭 결과 처리
            // 각 매칭된 쌍에 대해 드라이버 호출을 생성하고
            // 매칭되지 않은 요청은 다음 배치로 이월하거나 실패 처리합니다
            processMatchingResults(requestsWithData, allDrivers, assignments)

            val processingTime = System.currentTimeMillis() - startTime
            logger.info { "Batch $batchId processed in ${processingTime}ms" }

        } catch (e: Exception) {
            logger.error(e) { "Error processing matching batch" }
        } finally {
            releaseLock()
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
        val driverIndexMap = allDrivers.withIndex().associate { it.value.driverId to it.index }
        val assignedDrivers = mutableSetOf<UUID>()

        requestsWithData.forEachIndexed { requestIndex, requestData ->
            val assignedDriverIndex = assignments[requestIndex]

            if (assignedDriverIndex != -1 && assignedDriverIndex < allDrivers.size) {
                val assignedDriver = allDrivers[assignedDriverIndex]

                // 드라이버가 이미 다른 요청에 할당되었는지 확인
                if (assignedDriver.driverId in assignedDrivers) {
                    handleNoDriverAvailable(requestData)
                    return@forEachIndexed
                }

                // 이 요청에 대한 상위 3명의 드라이버 찾기
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
                    // 드라이버를 할당된 것으로 표시
                    selectedDrivers.forEach { assignedDrivers.add(it.driverId) }

                    // 드라이버 호출 생성
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

        // 모든 업데이트된 요청 저장
        matchingRequestRepository.saveAll(requestsWithData.map { it.request })
    }

    private fun handleNoDriverAvailable(requestData: RequestWithData) {
        logger.warn { "No drivers available for ride ${requestData.ride.id}" }
        requestData.request.fail("No available drivers")
        // 승객 알림을 위한 추가 로직을 여기에 추가할 수 있음
    }

    private fun failAllRequests(requests: List<MatchingRequest>, reason: String) {
        requests.forEach { it.fail(reason) }
        matchingRequestRepository.saveAll(requests)
    }

    private fun acquireLock(): Boolean {
        return redisTemplate.opsForValue().setIfAbsent(
            processingLock,
            Thread.currentThread().name,
            Duration.ofSeconds(5)
        ) ?: false
    }

    private fun releaseLock() {
        redisTemplate.delete(processingLock)
    }

    data class RequestWithData(
        val request: MatchingRequest,
        val ride: Ride,
        val pickupLocation: Location,
        val nearbyDrivers: List<AvailableDriver>
    )
}
