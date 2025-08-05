package com.ddakta.matching.service.impl

import com.ddakta.matching.config.MatchingProperties
import com.ddakta.matching.domain.entity.DriverCall
import com.ddakta.matching.domain.entity.MatchingRequest
import com.ddakta.matching.domain.entity.Ride
import com.ddakta.matching.domain.enum.DriverCallStatus
import com.ddakta.matching.domain.enum.MatchingRequestStatus
import com.ddakta.matching.domain.enum.RideStatus
import com.ddakta.matching.domain.repository.DriverCallRepository
import com.ddakta.matching.domain.repository.MatchingRequestRepository
import com.ddakta.matching.domain.repository.RideRepository
import com.ddakta.matching.dto.internal.AvailableDriver
import com.ddakta.matching.dto.internal.MatchedDriver
import com.ddakta.matching.dto.internal.MatchingResult
import com.ddakta.matching.algorithm.DriverSelector
import com.ddakta.matching.algorithm.HungarianAlgorithm
import com.ddakta.matching.algorithm.MatchingScoreCalculator
import com.ddakta.matching.client.LocationServiceClient
import com.ddakta.matching.event.producer.RideEventProducer
import com.ddakta.matching.service.DriverCallService
import com.ddakta.matching.service.MatchingService
import com.ddakta.matching.service.RideService
import com.ddakta.matching.service.SurgePriceService
import com.ddakta.matching.utils.DistanceCalculator
import mu.KotlinLogging
import org.redisson.api.RedissonClient
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis

/**
 * 매칭 서비스 구현체
 * 
 * 승객과 드라이버를 매칭하는 핵심 비즈니스 로직을 담당합니다.
 * 주요 기능:
 * 1. 배치 매칭 처리: 여러 매칭 요청을 효율적으로 동시 처리
 * 2. 지역별 그룹핑: H3 인덱스를 기반으로 근처 요청을 그룹화
 * 3. 분산 락: Redis를 통한 다중 인스턴스 환경에서의 동시성 제어
 * 4. 재시도 로직: 실패 시 최대 3회까지 재시도
 */
@Service
@Transactional
class MatchingServiceImpl(
    private val matchingRequestRepository: MatchingRequestRepository,
    private val rideRepository: RideRepository,
    private val driverCallRepository: DriverCallRepository,
    private val locationServiceClient: LocationServiceClient,
    private val driverSelector: DriverSelector,
    private val matchingScoreCalculator: MatchingScoreCalculator,
    private val hungarianAlgorithm: HungarianAlgorithm,
    private val driverCallService: DriverCallService,
    private val rideService: RideService,
    private val surgePriceService: SurgePriceService,
    private val rideEventProducer: RideEventProducer,
    private val matchingProperties: MatchingProperties,
    private val redisTemplate: RedisTemplate<String, String>,
    private val redissonClient: RedissonClient
) : MatchingService {

    private val logger = KotlinLogging.logger {}

    companion object {
        const val MATCHING_LOCK_KEY = "matching:batch:lock"
        const val DRIVER_ASSIGNMENT_LOCK_KEY = "driver:assignment:"
        const val MATCHING_REQUEST_CACHE_KEY = "matching:request:"
        const val CACHE_TTL_MINUTES = 10L
        const val MAX_DRIVERS_PER_REQUEST = 5
        const val MAX_RETRY_COUNT = 3
        const val BATCH_SIZE = 100
    }

    /**
     * 배치 매칭 처리 메인 메서드
     * 
     * 분산 환경에서의 동시성 제어를 위해 Redisson FairLock을 사용합니다.
     * FairLock은 요청 순서를 보장하여 공정한 락 획듍을 보장합니다.
     * 
     * 동작 방식:
     * 1. 락 획듍 시도 (0초 대기, 최대 5초 보유)
     * 2. 락을 획듍한 경우에만 매칭 처리 수행
     * 3. 처리 완료 후 반드시 락 해제
     * 
     * @return 매칭 결과 리스트
     */
    override fun processMatchingBatch(): List<MatchingResult> {
        val lock = redissonClient.getFairLock(MATCHING_LOCK_KEY)

        if (!lock.tryLock(0, 5, TimeUnit.SECONDS)) {
            logger.debug { "Another instance is processing matching batch" }
            return emptyList()
        }

        val startTime = System.currentTimeMillis()
        try {
            val result = processMatchingBatchInternal()
            val processingTime = System.currentTimeMillis() - startTime
            logger.info { "Matching batch completed in ${processingTime}ms" }
            return result
        } finally {
            if (lock.isHeldByCurrentThread) {
                lock.unlock()
            }
        }
    }

    /**
     * 매칭 배치 내부 처리 로직
     * 
     * 실제 매칭 처리를 수행하는 핵심 로직입니다.
     * 
     * 처리 단계:
     * 1. 대기 중인 매칭 요청 조회 (10분 이내 요청)
     * 2. H3 인덱스 기반 지역별 그룹핑
     * 3. 각 지역별로 병렬 매칭 처리
     * 4. 오류 발생 시 graceful degradation
     */
    private fun processMatchingBatchInternal(): List<MatchingResult> {
        // 1. 활성 매칭 요청 조회
        // TODO: 임시 수정 - findActiveRequests 대신 findPendingRequests 사용
        // 10분 이내에 생성된 대기 중인 요청만 조회하여
        // 오래된 요청이 계속 대기하는 것을 방지합니다
        val activeRequests = matchingRequestRepository.findPendingRequests(
            LocalDateTime.now().minusMinutes(10), // maxAge
            BATCH_SIZE
        )

        if (activeRequests.isEmpty()) {
            logger.debug { "No active matching requests found" }
            return emptyList()
        }

        logger.info { "Processing ${activeRequests.size} matching requests" }

        // 2. 지역별로 요청 그룹화 (H3 인덱스 기준)
        // H3는 Uber가 개발한 육각형 기반 지리공간 인덱싱 시스템입니다.
        // 같은 H3 인덱스의 요청들은 지리적으로 가까우므로 함께 처리합니다.
        val requestsByH3 = activeRequests.groupBy { it.pickupH3 }
        val results = mutableListOf<MatchingResult>()

        // 3. 각 지역별로 매칭 처리
        // 병렬 처리가 가능하지만, 현재는 순차적으로 처리합니다.
        // 향후 성능 개선을 위해 코루틴이나 병렬 스트림을 사용할 수 있습니다.
        requestsByH3.forEach { (h3Index, requests) ->
            try {
                val regionResults = processRegionMatching(h3Index, requests)
                results.addAll(regionResults)
            } catch (e: Exception) {
                logger.error(e) { "Error processing region $h3Index" }
                // 해당 지역의 모든 요청을 실패로 처리
                requests.forEach { request ->
                    results.add(
                        MatchingResult(
                            rideId = request.rideId,
                            matchedDrivers = emptyList(),
                            success = false,
                            reason = "Processing error: ${e.message}"
                        )
                    )
                }
            }
        }

        return results
    }

    /**
     * 지역별 매칭 처리
     * 
     * 특정 H3 지역의 모든 매칭 요청을 처리합니다.
     * 헝가리안 알고리즘을 사용하여 전체 최적화를 달성합니다.
     * 
     * @param h3Index 지역 H3 인덱스
     * @param requests 해당 지역의 매칭 요청 리스트
     * @return 매칭 결과 리스트
     */
    private fun processRegionMatching(
        h3Index: String,
        requests: List<MatchingRequest>
    ): List<MatchingResult> {
        // 1. 해당 지역의 가용 드라이버 조회
        // 주변 H3 인덱스를 포함하여 더 넓은 범위에서 드라이버를 찾습니다.
        // location-service가 사용 불가한 경우 fallback으로 예외 처리합니다.
        val availableDrivers = try {
            val nearbyH3Indexes = getNearbyH3Indexes(h3Index)
            locationServiceClient.getAvailableDrivers(nearbyH3Indexes)
        } catch (e: Exception) {
            logger.warn { "Failed to get available drivers for $h3Index: ${e.message}" }
            return requests.map { request ->
                MatchingResult(
                    rideId = request.rideId,
                    matchedDrivers = emptyList(),
                    success = false,
                    reason = "Failed to get available drivers"
                )
            }
        }

        if (availableDrivers.isEmpty()) {
            logger.info { "No available drivers in $h3Index" }
            return requests.map { request ->
                handleNoDriversAvailable(request)
            }
        }

        // 2. 헝가리안 알고리즘을 위한 비용 매트릭스 생성
        val costMatrix = createCostMatrix(requests, availableDrivers)

        // 3. 헝가리안 알고리즘 실행
        val assignments = hungarianAlgorithm.findOptimalMatching(costMatrix)

        // 4. 매칭 결과 처리
        return processAssignments(requests, availableDrivers, assignments, costMatrix)
    }

    private fun createCostMatrix(
        requests: List<MatchingRequest>,
        drivers: List<AvailableDriver>
    ): Array<DoubleArray> {
        val matrix = Array(requests.size) { DoubleArray(drivers.size) }

        requests.forEachIndexed { rIndex, request ->
            val ride = rideRepository.findById(request.rideId).orElse(null)
                ?: return@forEachIndexed

            drivers.forEachIndexed { dIndex, driver ->
                // 거리 계산
                val distance = try {
                    DistanceCalculator.calculateDistance(
                        ride.pickupLocation.latitude,
                        ride.pickupLocation.longitude,
                        driver.currentLocation.latitude,
                        driver.currentLocation.longitude
                    )
                } catch (e: Exception) {
                    1000.0 // 기본값
                }

                // 매칭 점수 계산 (낮을수록 좋음)
                val score = matchingScoreCalculator.calculateScore(
                    distanceMeters = distance.toDouble(),
                    driverRating = driver.rating,
                    acceptanceRate = driver.acceptanceRate,
                    completionRate = driver.completionRate
                )

                // 비용 = 1 / 점수 (점수가 높을수록 비용이 낮음)
                matrix[rIndex][dIndex] = if (score > 0) 1.0 / score else Double.MAX_VALUE
            }
        }

        return matrix
    }

    private fun processAssignments(
        requests: List<MatchingRequest>,
        drivers: List<AvailableDriver>,
        assignments: IntArray,
        costMatrix: Array<DoubleArray>
    ): List<MatchingResult> {
        val results = mutableListOf<MatchingResult>()
        val assignedDriverIds = mutableSetOf<UUID>()

        assignments.forEachIndexed { requestIndex, driverIndex ->
            if (requestIndex >= requests.size) return@forEachIndexed

            val request = requests[requestIndex]
            val result = if (driverIndex >= 0 && driverIndex < drivers.size) {
                val driver = drivers[driverIndex]

                // 중복 할당 방지
                if (driver.driverId in assignedDriverIds) {
                    createNoDriverResult(request)
                } else {
                    assignedDriverIds.add(driver.driverId)
                    processDriverAssignment(request, driver, costMatrix[requestIndex][driverIndex])
                }
            } else {
                createNoDriverResult(request)
            }

            results.add(result)
        }

        return results
    }

    private fun processDriverAssignment(
        request: MatchingRequest,
        driver: AvailableDriver,
        cost: Double
    ): MatchingResult {
        try {
            // 드라이버 잠금 획득
            val driverLock = redissonClient.getFairLock("$DRIVER_ASSIGNMENT_LOCK_KEY${driver.driverId}")

            if (!driverLock.tryLock(100, 1000, TimeUnit.MILLISECONDS)) {
                logger.warn { "Failed to acquire lock for driver ${driver.driverId}" }
                return createNoDriverResult(request)
            }

                try {
                    // 운행 정보 조회
                    val ride = rideRepository.findByIdWithLock(request.rideId)
                        ?: return createFailedResult(request, "Ride not found")

                    // 이미 매칭된 경우 스킵
                    if (ride.status != RideStatus.REQUESTED) {
                        return createFailedResult(request, "Ride already matched")
                    }

                    // 드라이버 호출 생성
                    val driverCalls = createDriverCalls(ride, listOf(driver))
                    if (driverCalls.isEmpty()) {
                        return createNoDriverResult(request)
                    }

                    // TODO: 임시 수정 - 비즈니스 메서드 사용
                    ride.updateStatus(RideStatus.MATCHED)
                    rideRepository.save(ride)

                    // TODO: 임시 수정 - 비즈니스 메서드 사용
                    request.markAsMatched()
                    matchingRequestRepository.save(request)

                    // 이벤트 발행
                    rideEventProducer.publishRideMatched(
                        ride,
                        driverCalls.first().estimatedArrivalSeconds,
                        null // TODO: 임시 수정 - Location 타입 불일치 해결
                    )
                    val rideId = ride.id
                    rideEventProducer.publishDriverCallRequest(rideId, driverCalls)

                    logger.info { "Successfully matched ride ${ride.id} with driver ${driver.driverId}" }

                    return MatchingResult(
                        rideId = request.rideId,
                        matchedDrivers = listOf(
                            MatchedDriver(
                                driverId = driver.driverId,
                                estimatedArrivalSeconds = driverCalls.first().estimatedArrivalSeconds ?: 0,
                                score = if (cost > 0) 1.0 / cost else 0.0,
                                rank = 1
                            )
                        ),
                        success = true,
                        matchingScore = if (cost > 0) 1.0 / cost else 0.0
                    )

                } finally {
                    driverLock.unlock()
                }

        } catch (e: Exception) {
            logger.error(e) { "Error processing driver assignment for request ${request.rideId}" }
            return createFailedResult(request, e.message ?: "Unknown error")
        }
    }

    private fun createDriverCalls(
        ride: Ride,
        drivers: List<AvailableDriver>
    ): List<DriverCall> {
        return drivers.mapNotNull { driver ->
            try {
                val estimatedArrival = calculateEstimatedArrival(
                    ride.pickupLocation.latitude,
                    ride.pickupLocation.longitude,
                    driver.currentLocation.latitude,
                    driver.currentLocation.longitude
                )

                val driverCall = DriverCall(
                    ride = ride,
                    driverId = driver.driverId,
                    sequenceNumber = 1,
                    estimatedArrivalSeconds = estimatedArrival,
                    estimatedFare = ride.fare?.totalFare,
                    expiresAt = LocalDateTime.now().plusSeconds(30),
                    driverLocation = driver.currentLocation
                )

                driverCallRepository.save(driverCall)

            } catch (e: Exception) {
                logger.error(e) { "Failed to create driver call for driver ${driver.driverId}" }
                null
            }
        }
    }

    private fun calculateEstimatedArrival(
        pickupLat: Double,
        pickupLng: Double,
        driverLat: Double,
        driverLng: Double
    ): Int {
        val distanceMeters = DistanceCalculator.calculateDistance(
            pickupLat, pickupLng, driverLat, driverLng
        )

        // 평균 속도 30km/h 가정
        return (distanceMeters / 1000.0 * 120).toInt() // 2분/km
    }

    override fun createMatchingRequest(ride: Ride): MatchingRequest {
        // 중복 요청 방지
        val rideId = ride.id
        val existing = matchingRequestRepository.findByRideId(rideId)
        if (existing != null) {
            return existing
        }

        val surgeMultiplier = surgePriceService.getCurrentSurgeMultiplier(ride.pickupLocation.h3Index)

        val matchingRequest = MatchingRequest(
            rideId = rideId,
            passengerId = ride.passengerId,
            pickupH3 = ride.pickupLocation.h3Index,
            dropoffH3 = ride.dropoffLocation.h3Index,
            surgeMultiplier = BigDecimal.valueOf(surgeMultiplier),
            expiresAt = LocalDateTime.now().plusMinutes(5)
        )

        val saved = matchingRequestRepository.save(matchingRequest)

        // 캐시에 저장
        cacheMatchingRequest(saved)

        logger.info { "Created matching request for ride ${ride.id}" }

        return saved
    }

    override fun cancelMatchingRequest(rideId: UUID) {
        val matchingRequest = matchingRequestRepository.findByRideId(rideId)

        if (matchingRequest != null && matchingRequest.status == MatchingRequestStatus.PENDING) {
            matchingRequest.status = MatchingRequestStatus.FAILED
            matchingRequestRepository.save(matchingRequest)

            // 캐시에서 제거
            removeCachedMatchingRequest(rideId)

            // 관련 드라이버 호출 취소
            driverCallService.cancelAllCallsForRide(rideId)

            logger.info { "Cancelled matching request for ride $rideId" }
        }
    }

    override fun getActiveMatchingRequests(limit: Int): List<MatchingRequest> {
        return matchingRequestRepository.findPendingRequests(
            LocalDateTime.now().minusMinutes(10),
            limit
        )
    }

    override fun handleMatchingTimeout(rideId: UUID) {
        val matchingRequest = matchingRequestRepository.findByRideId(rideId)

        if (matchingRequest != null && matchingRequest.status == MatchingRequestStatus.PENDING) {
            matchingRequest.status = MatchingRequestStatus.FAILED
            matchingRequestRepository.save(matchingRequest)

            // 운행 상태 업데이트
            val ride = rideRepository.findById(rideId).orElse(null)
            if (ride != null && ride.status == RideStatus.REQUESTED) {
                ride.updateStatus(RideStatus.FAILED)
                rideRepository.save(ride)

                // 이벤트 발행
                rideEventProducer.publishRideStatusChanged(
                    ride,
                    RideStatus.REQUESTED.name,
                    mapOf("reason" to "matching_timeout")
                )
            }

            logger.info { "Handled matching timeout for ride $rideId" }
        }
    }

    override fun retryMatching(rideId: UUID): MatchingResult? {
        val ride = rideRepository.findById(rideId).orElse(null)
            ?: return null

        if (ride.status != RideStatus.REQUESTED) {
            return MatchingResult(
                rideId = rideId,
                matchedDrivers = emptyList(),
                success = false,
                reason = "Invalid ride status for retry: ${ride.status}"
            )
        }

        // 기존 매칭 요청 재활성화 또는 새로 생성
        val matchingRequest = matchingRequestRepository.findByRideId(rideId)
            ?: return null

        if (matchingRequest.retryCount >= MAX_RETRY_COUNT) {
            return MatchingResult(
                rideId = rideId,
                matchedDrivers = emptyList(),
                success = false,
                reason = "Max retry count exceeded"
            )
        }

        matchingRequest.status = MatchingRequestStatus.PENDING
        // 재시도 카운트 증가 및 상태 리셋
        matchingRequest.resetForRetry()
        matchingRequestRepository.save(matchingRequest)

        logger.info { "Retrying matching for ride $rideId (attempt ${matchingRequest.retryCount})" }

        // 즉시 매칭 시도
        return processRegionMatching(
            matchingRequest.pickupH3,
            listOf(matchingRequest)
        ).firstOrNull()
    }

    private fun getNearbyH3Indexes(h3Index: String): List<String> {
        // H3 인접 지역 계산 (실제로는 H3 라이브러리 사용)
        // 여기서는 간단히 현재 지역만 반환
        return listOf(h3Index)
    }

    private fun handleNoDriversAvailable(request: MatchingRequest): MatchingResult {
        // 매칭 요청 실패 처리
        request.fail("No available drivers")
        matchingRequestRepository.save(request)

        return MatchingResult(
            rideId = request.rideId,
            matchedDrivers = emptyList(),
            success = false,
            reason = "No available drivers"
        )
    }

    private fun createNoDriverResult(request: MatchingRequest): MatchingResult {
        return MatchingResult(
            rideId = request.rideId,
            matchedDrivers = emptyList(),
            success = false,
            reason = "No available drivers"
        )
    }

    private fun createFailedResult(request: MatchingRequest, reason: String): MatchingResult {
        return MatchingResult(
            rideId = request.rideId,
            matchedDrivers = emptyList(),
            success = false,
            reason = reason
        )
    }

    private fun cacheMatchingRequest(request: MatchingRequest) {
        val key = "$MATCHING_REQUEST_CACHE_KEY${request.rideId}"
        redisTemplate.opsForValue().set(
            key,
            request.id.toString(),
            CACHE_TTL_MINUTES,
            TimeUnit.MINUTES
        )
    }

    private fun removeCachedMatchingRequest(rideId: UUID) {
        val key = "$MATCHING_REQUEST_CACHE_KEY$rideId"
        redisTemplate.delete(key)
    }
}
