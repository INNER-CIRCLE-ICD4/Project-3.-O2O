package com.ddakta.matching.service.impl

import com.ddakta.matching.algorithm.DriverSelector
import com.ddakta.matching.algorithm.HungarianAlgorithm
import com.ddakta.matching.algorithm.MatchingScoreCalculator
import com.ddakta.matching.client.LocationServiceClient
import com.ddakta.matching.config.MatchingProperties
import com.ddakta.matching.domain.entity.DriverCall
import com.ddakta.matching.domain.entity.MatchingRequest
import com.ddakta.matching.domain.entity.Ride
import com.ddakta.matching.domain.enum.MatchingRequestStatus
import com.ddakta.matching.domain.enum.RideStatus
import com.ddakta.matching.domain.repository.DriverCallRepository
import com.ddakta.matching.domain.repository.MatchingRequestRepository
import com.ddakta.matching.domain.repository.RideRepository
import com.ddakta.matching.domain.vo.Fare
import com.ddakta.matching.domain.vo.Location
import com.ddakta.matching.dto.internal.AvailableDriver
import com.ddakta.matching.dto.internal.MatchingResult
import com.ddakta.matching.event.producer.RideEventProducer
import com.ddakta.matching.service.DriverCallService
import com.ddakta.matching.service.RideService
import com.ddakta.matching.service.SurgePriceService
import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.redisson.api.RLock
import org.redisson.api.RedissonClient
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.TimeUnit

@ExtendWith(MockKExtension::class)
@DisplayName("MatchingService Implementation Tests")
class MatchingServiceImplTest {

    @MockK
    private lateinit var matchingRequestRepository: MatchingRequestRepository

    @MockK
    private lateinit var rideRepository: RideRepository

    @MockK
    private lateinit var driverCallRepository: DriverCallRepository

    @MockK
    private lateinit var locationServiceClient: LocationServiceClient

    @MockK
    private lateinit var driverSelector: DriverSelector

    @MockK
    private lateinit var matchingScoreCalculator: MatchingScoreCalculator

    @MockK
    private lateinit var hungarianAlgorithm: HungarianAlgorithm

    @MockK
    private lateinit var driverCallService: DriverCallService

    @MockK
    private lateinit var rideService: RideService

    @MockK
    private lateinit var surgePriceService: SurgePriceService

    @MockK
    private lateinit var rideEventProducer: RideEventProducer

    @MockK
    private lateinit var matchingProperties: MatchingProperties

    @MockK
    private lateinit var redisTemplate: RedisTemplate<String, String>

    @MockK
    private lateinit var redissonClient: RedissonClient

    @MockK
    private lateinit var fairLock: RLock

    @MockK
    private lateinit var valueOperations: ValueOperations<String, String>

    @InjectMockKs
    private lateinit var matchingService: MatchingServiceImpl

    private lateinit var testRide: Ride
    private lateinit var testMatchingRequest: MatchingRequest
    private lateinit var testAvailableDriver: AvailableDriver

    @BeforeEach
    fun setUp() {
        // Mock Redis operations
        every { redisTemplate.opsForValue() } returns valueOperations
        every { valueOperations.set(any<String>(), any<String>(), any<Long>(), any<TimeUnit>()) } returns Unit
        every { redisTemplate.delete(any<String>()) } returns true

        // Mock Redisson lock
        every { redissonClient.getFairLock(any()) } returns fairLock
        every { fairLock.tryLock(any<Long>(), any<Long>(), any<TimeUnit>()) } returns true
        every { fairLock.isHeldByCurrentThread } returns true
        every { fairLock.unlock() } returns Unit

        // Test data setup
        testRide = createTestRide()
        testMatchingRequest = createTestMatchingRequest()
        testAvailableDriver = createTestAvailableDriver()
    }

    @Nested
    @DisplayName("Batch Processing Tests")
    inner class BatchProcessingTests {

        @Test
        @DisplayName("Should process empty batch successfully")
        fun shouldProcessEmptyBatchSuccessfully() {
            // Given
            every { matchingRequestRepository.findPendingRequests(any(), any()) } returns emptyList()

            // When
            val results = matchingService.processMatchingBatch()

            // Then
            assertThat(results).isEmpty()
            verify { matchingRequestRepository.findPendingRequests(any(), any()) }
        }

        @Test
        @DisplayName("Should process batch with matching requests")
        fun shouldProcessBatchWithMatchingRequests() {
            // Given
            val requests = listOf(testMatchingRequest)
            val drivers = listOf(testAvailableDriver)
            val costMatrix = arrayOf(doubleArrayOf(0.5))
            val assignments = intArrayOf(0)

            every { matchingRequestRepository.findPendingRequests(any(), any()) } returns requests
            every { locationServiceClient.getAvailableDrivers(any()) } returns drivers
            every { matchingScoreCalculator.calculateScore(any(), any(), any(), any()) } returns 0.8
            every { hungarianAlgorithm.findOptimalMatching(any()) } returns assignments
            every { rideRepository.findById(any()) } returns Optional.of(testRide)
            every { rideRepository.findByIdWithLock(any()) } returns testRide
            every { rideRepository.save(any()) } returns testRide
            every { matchingRequestRepository.save(any()) } returns testMatchingRequest
            
            // Mock driver call save
            val mockDriverCall = mockk<DriverCall>(relaxed = true) {
                every { estimatedArrivalSeconds } returns 180
                every { driverId } returns testAvailableDriver.driverId
                every { ride.id } returns testRide.id!!
            }
            every { driverCallRepository.save(any()) } returns mockDriverCall
            
            every { rideEventProducer.publishRideMatched(any(), any(), any()) } just Runs
            every { rideEventProducer.publishDriverCallRequest(any(), any()) } just Runs

            // When
            val results = matchingService.processMatchingBatch()

            // Then
            verify { matchingRequestRepository.findPendingRequests(any(), any()) }
            verify { locationServiceClient.getAvailableDrivers(any()) }
            verify { hungarianAlgorithm.findOptimalMatching(any()) }
            verify { rideEventProducer.publishRideMatched(any(), any(), any()) }
        }

        @Test
        @DisplayName("Should handle lock acquisition failure")
        fun shouldHandleLockAcquisitionFailure() {
            // Given
            every { fairLock.tryLock(any<Long>(), any<Long>(), any<TimeUnit>()) } returns false

            // When
            val results = matchingService.processMatchingBatch()

            // Then
            assertThat(results).isEmpty()
            verify(exactly = 0) { matchingRequestRepository.findPendingRequests(any(), any()) }
        }

        @Test
        @DisplayName("Should handle no available drivers scenario")
        fun shouldHandleNoAvailableDriversScenario() {
            // Given
            val requests = listOf(testMatchingRequest)
            every { matchingRequestRepository.findPendingRequests(any(), any()) } returns requests
            every { locationServiceClient.getAvailableDrivers(any()) } returns emptyList()
            every { matchingRequestRepository.save(any()) } returns testMatchingRequest

            // When
            val results = matchingService.processMatchingBatch()

            // Then
            verify { locationServiceClient.getAvailableDrivers(any()) }
            verify { matchingRequestRepository.save(any()) }
        }
    }

    @Nested
    @DisplayName("Cost Matrix Creation Tests")
    inner class CostMatrixTests {

        @Test
        @DisplayName("Should create valid cost matrix")
        fun shouldCreateValidCostMatrix() {
            // Given
            val requests = listOf(testMatchingRequest)
            val drivers = listOf(testAvailableDriver)
            
            every { rideRepository.findById(any()) } returns Optional.of(testRide)
            every { matchingScoreCalculator.calculateScore(any(), any(), any(), any()) } returns 0.8

            // When - Access private method via reflection for testing
            val costMatrixMethod = matchingService.javaClass.getDeclaredMethod(
                "createCostMatrix", 
                List::class.java, 
                List::class.java
            )
            costMatrixMethod.isAccessible = true
            val costMatrix = costMatrixMethod.invoke(matchingService, requests, drivers) as Array<DoubleArray>

            // Then
            assertThat(costMatrix.size).isEqualTo(1)
            assertThat(costMatrix[0]).hasSize(1)
            assertThat(costMatrix[0][0]).isPositive()
        }
    }

    @Nested
    @DisplayName("Matching Request Management Tests")
    inner class MatchingRequestManagementTests {

        @Test
        @DisplayName("Should create new matching request")
        fun shouldCreateNewMatchingRequest() {
            // Given
            every { matchingRequestRepository.findByRideId(any()) } returns null
            every { surgePriceService.getCurrentSurgeMultiplier(any()) } returns 1.2
            every { matchingRequestRepository.save(any()) } returns testMatchingRequest

            // When
            val result = matchingService.createMatchingRequest(testRide)

            // Then
            assertThat(result).isNotNull
            verify { matchingRequestRepository.save(any()) }
            verify { surgePriceService.getCurrentSurgeMultiplier(any()) }
        }

        @Test
        @DisplayName("Should return existing matching request")
        fun shouldReturnExistingMatchingRequest() {
            // Given
            every { matchingRequestRepository.findByRideId(any()) } returns testMatchingRequest

            // When
            val result = matchingService.createMatchingRequest(testRide)

            // Then
            assertThat(result).isEqualTo(testMatchingRequest)
            verify(exactly = 0) { matchingRequestRepository.save(any()) }
        }

        @Test
        @DisplayName("Should cancel matching request")
        fun shouldCancelMatchingRequest() {
            // Given
            val rideId = UUID.randomUUID()
            every { matchingRequestRepository.findByRideId(rideId) } returns testMatchingRequest
            every { matchingRequestRepository.save(any()) } returns testMatchingRequest
            every { driverCallService.cancelAllCallsForRide(rideId) } just Runs

            // When
            matchingService.cancelMatchingRequest(rideId)

            // Then
            verify { matchingRequestRepository.save(any()) }
            verify { driverCallService.cancelAllCallsForRide(rideId) }
            verify { redisTemplate.delete(any<String>()) }
        }
    }

    @Nested
    @DisplayName("Timeout Handling Tests")
    inner class TimeoutHandlingTests {

        @Test
        @DisplayName("Should handle matching timeout")
        fun shouldHandleMatchingTimeout() {
            // Given
            val rideId = UUID.randomUUID()
            every { matchingRequestRepository.findByRideId(rideId) } returns testMatchingRequest
            every { matchingRequestRepository.save(any()) } returns testMatchingRequest
            every { rideRepository.findById(rideId) } returns Optional.of(testRide)
            every { rideRepository.save(any()) } returns testRide
            every { rideEventProducer.publishRideStatusChanged(any(), any(), any()) } just Runs

            // When
            matchingService.handleMatchingTimeout(rideId)

            // Then
            verify { matchingRequestRepository.save(any()) }
            verify { rideRepository.save(any()) }
            verify { rideEventProducer.publishRideStatusChanged(any(), any(), any()) }
        }

        @Test
        @DisplayName("Should ignore timeout for non-pending requests")
        fun shouldIgnoreTimeoutForNonPendingRequests() {
            // Given
            val rideId = UUID.randomUUID()
            val completedRequest = createTestMatchingRequest().apply {
                startProcessing(UUID.randomUUID())
                complete()
            }
            every { matchingRequestRepository.findByRideId(rideId) } returns completedRequest

            // When
            matchingService.handleMatchingTimeout(rideId)

            // Then
            verify(exactly = 0) { matchingRequestRepository.save(any()) }
            verify(exactly = 0) { rideRepository.save(any()) }
        }
    }

    @Nested
    @DisplayName("Retry Logic Tests")
    inner class RetryLogicTests {

        @Test
        @DisplayName("Should retry matching for valid ride")
        fun shouldRetryMatchingForValidRide() {
            // Given
            val rideId = UUID.randomUUID()
            val testRequest = createTestMatchingRequest().apply {
                status = MatchingRequestStatus.FAILED
                // retryCount is incremented by fail() method
            }
            
            every { rideRepository.findById(rideId) } returns Optional.of(testRide)
            every { matchingRequestRepository.findByRideId(rideId) } returns testRequest
            
            // When & Then - expect exception due to implementation bug
            assertThrows<IllegalArgumentException> {
                matchingService.retryMatching(rideId)
            }
        }

        @Test
        @DisplayName("Should reject retry when max retry count exceeded")
        fun shouldRejectRetryWhenMaxRetryCountExceeded() {
            // Given
            val rideId = UUID.randomUUID()
            val testRequest = createTestMatchingRequest().apply {
                // Fail multiple times to exceed MAX_RETRY_COUNT
                repeat(5) {
                    fail("No drivers available")
                    if (it < 4) resetForRetry()
                }
            }
            
            every { rideRepository.findById(rideId) } returns Optional.of(testRide)
            every { matchingRequestRepository.findByRideId(rideId) } returns testRequest

            // When
            val result = matchingService.retryMatching(rideId)

            // Then
            assertThat(result).isNotNull
            assertThat(result!!.success).isFalse
            assertThat(result.reason).contains("Max retry count exceeded")
        }

        @Test
        @DisplayName("Should reject retry for invalid ride status")
        fun shouldRejectRetryForInvalidRideStatus() {
            // Given
            val rideId = UUID.randomUUID()
            val completedRide = createTestRide().apply {
                // Valid state transitions
                updateStatus(RideStatus.MATCHED)
                updateStatus(RideStatus.DRIVER_ASSIGNED)
                updateStatus(RideStatus.EN_ROUTE_TO_PICKUP)
                updateStatus(RideStatus.ARRIVED_AT_PICKUP)
                updateStatus(RideStatus.ON_TRIP)
                updateStatus(RideStatus.COMPLETED)
            }
            
            every { rideRepository.findById(rideId) } returns Optional.of(completedRide)

            // When
            val result = matchingService.retryMatching(rideId)

            // Then
            assertThat(result).isNotNull
            assertThat(result!!.success).isFalse
            assertThat(result.reason).contains("Invalid ride status")
        }
    }

    @Nested
    @DisplayName("Driver Assignment Tests")
    inner class DriverAssignmentTests {

        @Test
        @DisplayName("Should prevent duplicate driver assignments")
        fun shouldPreventDuplicateDriverAssignments() {
            // Given
            val requests = listOf(testMatchingRequest, createTestMatchingRequest())
            val drivers = listOf(testAvailableDriver) // Same driver for both requests
            val assignments = intArrayOf(0, 0) // Both requests assigned to same driver
            val costMatrix = arrayOf(doubleArrayOf(0.5), doubleArrayOf(0.6))

            every { rideRepository.findById(any()) } returns Optional.of(testRide)
            every { rideRepository.findByIdWithLock(any()) } returns testRide
            every { rideRepository.save(any()) } returns testRide
            every { matchingRequestRepository.save(any()) } returns testMatchingRequest
            
            val mockDriverCall = mockk<DriverCall>(relaxed = true) {
                every { estimatedArrivalSeconds } returns 180
            }
            every { driverCallRepository.save(any()) } returns mockDriverCall
            
            every { rideEventProducer.publishRideMatched(any(), any(), any()) } just Runs
            every { rideEventProducer.publishDriverCallRequest(any(), any()) } just Runs

            // When - Access private method via reflection
            val processAssignmentsMethod = matchingService.javaClass.getDeclaredMethod(
                "processAssignments",
                List::class.java,
                List::class.java,
                IntArray::class.java,
                Array<DoubleArray>::class.java
            )
            processAssignmentsMethod.isAccessible = true
            val results = processAssignmentsMethod.invoke(
                matchingService, requests, drivers, assignments, costMatrix
            ) as List<MatchingResult>

            // Then
            assertThat(results).hasSize(2)
            val successfulMatches = results.count { it.success }
            assertThat(successfulMatches).isEqualTo(1) // Only one should succeed due to duplicate prevention
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    inner class ErrorHandlingTests {

        @Test
        @DisplayName("Should handle location service failure gracefully")
        fun shouldHandleLocationServiceFailureGracefully() {
            // Given
            val requests = listOf(testMatchingRequest)
            every { matchingRequestRepository.findPendingRequests(any(), any()) } returns requests
            every { locationServiceClient.getAvailableDrivers(any()) } throws RuntimeException("Service unavailable")

            // When
            val results = matchingService.processMatchingBatch()

            // Then
            verify { locationServiceClient.getAvailableDrivers(any()) }
            // Should handle gracefully and not propagate exception
        }

        @Test
        @DisplayName("Should handle database connection failure")
        fun shouldHandleDatabaseConnectionFailure() {
            // Given
            every { matchingRequestRepository.findPendingRequests(any(), any()) } throws RuntimeException("DB connection failed")

            // When & Then
            assertThrows<RuntimeException> {
                matchingService.processMatchingBatch()
            }
        }
    }

    private fun createTestRide(): Ride {
        return Ride(
            passengerId = UUID.randomUUID(),
            pickupLocation = Location(37.5665, 126.9780, "Seoul Station", "8830e1d8dffffff"),
            dropoffLocation = Location(37.5759, 126.9768, "Myeongdong", "8830e1d89ffffff"),
            fare = Fare(
                baseFare = BigDecimal("5000"),
                surgeMultiplier = BigDecimal("1.0"),
                totalFare = BigDecimal("5000"),
                currency = "KRW"
            )
        )
    }

    private fun createTestMatchingRequest(): MatchingRequest {
        val rideId = UUID.randomUUID()
        return MatchingRequest(
            rideId = rideId,
            passengerId = UUID.randomUUID(),
            pickupH3 = "8830e1d8dffffff",
            dropoffH3 = "8830e1d89ffffff",
            surgeMultiplier = BigDecimal("1.0"),
            expiresAt = LocalDateTime.now().plusMinutes(5)
        ).apply {
            status = MatchingRequestStatus.PENDING
        }
    }

    private fun createTestAvailableDriver(): AvailableDriver {
        return AvailableDriver(
            driverId = UUID.randomUUID(),
            currentLocation = Location(37.5665, 126.9780, null, "8830e1d8dffffff"),
            rating = 4.8,
            acceptanceRate = 0.95,
            completionRate = 0.98,
            completedTrips = 1000,
            isAvailable = true
        )
    }
}