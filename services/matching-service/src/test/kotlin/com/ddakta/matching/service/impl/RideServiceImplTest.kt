package com.ddakta.matching.service.impl

import com.ddakta.matching.domain.entity.Ride
import com.ddakta.matching.domain.entity.RideStateTransition
import com.ddakta.matching.domain.enum.CancellationReason
import com.ddakta.matching.domain.enum.RideEvent
import com.ddakta.matching.domain.enum.RideStatus
import com.ddakta.matching.domain.repository.RideRepository
import com.ddakta.matching.domain.repository.RideStateTransitionRepository
import com.ddakta.matching.domain.vo.Fare
import com.ddakta.matching.domain.vo.Location
import com.ddakta.matching.dto.request.RideRequestDto
import com.ddakta.matching.dto.request.RideStatusUpdateDto
import com.ddakta.matching.dto.request.RequestLocationDto
import com.ddakta.matching.dto.request.FareEstimateDto
import com.ddakta.matching.dto.response.RideResponseDto
import com.ddakta.matching.event.producer.RideEventProducer
import com.ddakta.matching.exception.*
import com.ddakta.matching.service.FareCalculationService
import com.ddakta.matching.service.StateManagementService
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
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ValueOperations
import org.springframework.messaging.simp.SimpMessagingTemplate
import java.math.BigDecimal
import java.util.*
import java.util.concurrent.TimeUnit

@ExtendWith(MockKExtension::class)
@DisplayName("RideService Implementation Tests")
class RideServiceImplTest {

    @MockK
    private lateinit var rideRepository: RideRepository

    @MockK
    private lateinit var rideStateTransitionRepository: RideStateTransitionRepository

    @MockK
    private lateinit var stateManagementService: StateManagementService

    @MockK
    private lateinit var surgePriceService: SurgePriceService

    @MockK
    private lateinit var fareCalculationService: FareCalculationService

    @MockK
    private lateinit var rideEventProducer: RideEventProducer

    @MockK
    private lateinit var redisTemplate: RedisTemplate<String, String>

    @MockK
    private lateinit var messagingTemplate: SimpMessagingTemplate

    @MockK
    private lateinit var valueOperations: ValueOperations<String, String>

    @InjectMockKs
    private lateinit var rideService: RideServiceImpl

    private lateinit var testRide: Ride
    private lateinit var testRideRequest: RideRequestDto

    @BeforeEach
    fun setUp() {
        // Mock Redis operations
        every { redisTemplate.opsForValue() } returns valueOperations
        every { valueOperations.setIfAbsent(any<String>(), any<String>(), any<Long>(), any<TimeUnit>()) } returns true
        every { valueOperations.set(any<String>(), any<String>(), any<Long>(), any<TimeUnit>()) } returns Unit
        every { valueOperations.get(any()) } returns null
        every { redisTemplate.delete(any<String>()) } returns true

        // Mock messaging template
        every { messagingTemplate.convertAndSendToUser(any(), any(), any()) } just Runs

        // Test data setup
        testRide = createTestRide()
        testRideRequest = createTestRideRequest()
    }

    @Nested
    @DisplayName("Ride Creation Tests")
    inner class RideCreationTests {

        @Test
        @DisplayName("Should create ride successfully")
        fun shouldCreateRideSuccessfully() {
            // Given
            every { rideRepository.findActiveRideByPassenger(any()) } returns null
            every { surgePriceService.getCurrentSurgeMultiplier(any()) } returns 1.5
            every { fareCalculationService.calculateEstimatedFare(any(), any(), any()) } returns createTestFare()
            every { rideRepository.save(any()) } answers { firstArg<Ride>() }
            every { rideStateTransitionRepository.save(any()) } returns mockk<RideStateTransition>()
            every { rideEventProducer.publishRideRequested(any()) } just Runs

            // When
            val result = rideService.createRide(testRideRequest)

            // Then
            assertThat(result).isNotNull
            assertThat(result.passengerId).isEqualTo(testRideRequest.passengerId)
            assertThat(result.pickupLocation.latitude).isEqualTo(testRideRequest.pickupLocation.latitude)
            assertThat(result.pickupLocation.longitude).isEqualTo(testRideRequest.pickupLocation.longitude)
            assertThat(result.dropoffLocation.latitude).isEqualTo(testRideRequest.dropoffLocation.latitude)
            assertThat(result.dropoffLocation.longitude).isEqualTo(testRideRequest.dropoffLocation.longitude)
            verify { rideRepository.save(any()) }
            verify { rideStateTransitionRepository.save(any()) }
            verify { rideEventProducer.publishRideRequested(any()) }
            verify { messagingTemplate.convertAndSendToUser(any(), any(), any()) }
        }

        @Test
        @DisplayName("Should prevent duplicate ride requests")
        fun shouldPreventDuplicateRideRequests() {
            // Given
            every { valueOperations.setIfAbsent(any<String>(), any<String>(), any<Long>(), any<TimeUnit>()) } returns false

            // When & Then
            assertThrows<DuplicateRideRequestException> {
                rideService.createRide(testRideRequest)
            }

            verify(exactly = 0) { rideRepository.save(any()) }
        }

        @Test
        @DisplayName("Should prevent creating ride when passenger has active ride")
        fun shouldPreventCreatingRideWhenPassengerHasActiveRide() {
            // Given
            every { rideRepository.findActiveRideByPassenger(any()) } returns testRide

            // When & Then
            assertThrows<InvalidRideStateException> {
                rideService.createRide(testRideRequest)
            }

            verify { redisTemplate.delete(any<String>()) }
            verify(exactly = 0) { rideRepository.save(any()) }
        }

        @Test
        @DisplayName("Should handle exception during ride creation")
        fun shouldHandleExceptionDuringRideCreation() {
            // Given
            every { rideRepository.findActiveRideByPassenger(any()) } returns null
            every { surgePriceService.getCurrentSurgeMultiplier(any()) } throws RuntimeException("Service error")

            // When & Then
            assertThrows<RuntimeException> {
                rideService.createRide(testRideRequest)
            }

            verify { redisTemplate.delete(any<String>()) }
            verify(exactly = 0) { rideRepository.save(any()) }
        }

        @Test
        @DisplayName("Should create ride with provided fare estimate")
        fun shouldCreateRideWithProvidedFareEstimate() {
            // Given
            val requestWithFare = testRideRequest.copy(
                estimatedFare = FareEstimateDto(
                    baseFare = 5000.0,
                    estimatedTotal = 6000.0,
                    currency = "KRW"
                )
            )
            
            every { rideRepository.findActiveRideByPassenger(any()) } returns null
            every { surgePriceService.getCurrentSurgeMultiplier(any()) } returns 1.2
            every { rideRepository.save(any()) } answers { firstArg<Ride>() }
            every { rideStateTransitionRepository.save(any()) } returns mockk<RideStateTransition>()
            every { rideEventProducer.publishRideRequested(any()) } just Runs

            // When
            val result = rideService.createRide(requestWithFare)

            // Then
            assertThat(result).isNotNull
            verify { rideRepository.save(any()) }
            verify(exactly = 0) { fareCalculationService.calculateEstimatedFare(any(), any(), any()) }
        }
    }

    @Nested
    @DisplayName("Ride Retrieval Tests")
    inner class RideRetrievalTests {

        @Test
        @DisplayName("Should get ride by ID")
        fun shouldGetRideById() {
            // Given
            val rideId = UUID.randomUUID()
            every { rideRepository.findById(rideId) } returns Optional.of(testRide)

            // When
            val result = rideService.getRide(rideId)

            // Then
            assertThat(result).isNotNull
            assertThat(result.id).isEqualTo(testRide.id)
        }

        @Test
        @DisplayName("Should throw exception when ride not found")
        fun shouldThrowExceptionWhenRideNotFound() {
            // Given
            val rideId = UUID.randomUUID()
            every { rideRepository.findById(rideId) } returns Optional.empty()

            // When & Then
            assertThrows<RideNotFoundException> {
                rideService.getRide(rideId)
            }
        }

        @Test
        @DisplayName("Should get active ride for passenger from cache")
        fun shouldGetActiveRideForPassengerFromCache() {
            // Given
            val passengerId = UUID.randomUUID()
            every { valueOperations.get(any()) } returns testRide.id.toString()
            every { rideRepository.findById(any()) } returns Optional.of(testRide)

            // When
            val result = rideService.getActiveRideForPassenger(passengerId)

            // Then
            assertThat(result).isNotNull
            assertThat(result!!.id).isEqualTo(testRide.id)
            verify(exactly = 0) { rideRepository.findActiveRideByPassenger(any()) }
        }

        @Test
        @DisplayName("Should get active ride for passenger from database when not cached")
        fun shouldGetActiveRideForPassengerFromDatabaseWhenNotCached() {
            // Given
            val passengerId = UUID.randomUUID()
            every { valueOperations.get(any()) } returns null
            every { rideRepository.findActiveRideByPassenger(passengerId) } returns testRide

            // When
            val result = rideService.getActiveRideForPassenger(passengerId)

            // Then
            assertThat(result).isNotNull
            assertThat(result!!.id).isEqualTo(testRide.id)
            verify { rideRepository.findActiveRideByPassenger(passengerId) }
            verify { valueOperations.set(any(), any(), any<Long>(), any<TimeUnit>()) }
        }

        @Test
        @DisplayName("Should return null when no active ride for passenger")
        fun shouldReturnNullWhenNoActiveRideForPassenger() {
            // Given
            val passengerId = UUID.randomUUID()
            every { valueOperations.get(any()) } returns null
            every { rideRepository.findActiveRideByPassenger(passengerId) } returns null

            // When
            val result = rideService.getActiveRideForPassenger(passengerId)

            // Then
            assertThat(result).isNull()
        }

        @Test
        @DisplayName("Should get active ride for driver")
        fun shouldGetActiveRideForDriver() {
            // Given
            val driverId = UUID.randomUUID()
            val rideWithDriver = createTestRide().apply { 
                // Need to be in MATCHED state to assign driver
                updateStatus(RideStatus.MATCHED)
                assignDriver(driverId) 
            }
            
            every { valueOperations.get(any()) } returns null
            every { rideRepository.findActiveRideByDriver(driverId) } returns rideWithDriver
            every { valueOperations.set(any(), any(), any<Long>(), any<TimeUnit>()) } returns Unit

            // When
            val result = rideService.getActiveRideForDriver(driverId)

            // Then
            assertThat(result).isNotNull
            assertThat(result!!.driverId).isEqualTo(driverId)
            verify { valueOperations.set(any(), any(), any<Long>(), any<TimeUnit>()) }
        }
    }

    @Nested
    @DisplayName("Ride Status Update Tests")
    inner class RideStatusUpdateTests {

        @Test
        @DisplayName("Should update ride status successfully")
        fun shouldUpdateRideStatusSuccessfully() {
            // Given
            val rideId = UUID.randomUUID()
            val statusUpdate = RideStatusUpdateDto(event = RideEvent.DRIVER_ARRIVED)
            val actorId = UUID.randomUUID()
            val updatedRide = createTestRide().apply { 
                // Set up proper state progression
                updateStatus(RideStatus.MATCHED)
                updateStatus(RideStatus.DRIVER_ASSIGNED)
                updateStatus(RideStatus.EN_ROUTE_TO_PICKUP)
                updateStatus(RideStatus.ARRIVED_AT_PICKUP) 
            }

            every { rideRepository.findByIdWithLock(rideId) } returns testRide
            every { stateManagementService.processStateTransition(any(), any(), any()) } returns updatedRide
            every { rideStateTransitionRepository.save(any()) } returns mockk<RideStateTransition>()
            every { rideRepository.save(any()) } returns updatedRide
            every { rideEventProducer.publishRideStatusChanged(any(), any(), any()) } just Runs

            // When
            val result = rideService.updateRideStatus(rideId, statusUpdate, actorId)

            // Then
            assertThat(result).isNotNull
            assertThat(result.status).isEqualTo(RideStatus.ARRIVED_AT_PICKUP)
            verify { stateManagementService.processStateTransition(testRide, statusUpdate.event, actorId) }
            verify { rideStateTransitionRepository.save(any()) }
            verify { rideRepository.save(any()) }
            verify { rideEventProducer.publishRideStatusChanged(any(), any(), any()) }
            verify { messagingTemplate.convertAndSendToUser(any(), any(), any()) }
        }

        @Test
        @DisplayName("Should throw exception when ride not found for status update")
        fun shouldThrowExceptionWhenRideNotFoundForStatusUpdate() {
            // Given
            val rideId = UUID.randomUUID()
            val statusUpdate = RideStatusUpdateDto(event = RideEvent.DRIVER_ARRIVED)
            val actorId = UUID.randomUUID()

            every { rideRepository.findByIdWithLock(rideId) } returns null

            // When & Then
            assertThrows<RideNotFoundException> {
                rideService.updateRideStatus(rideId, statusUpdate, actorId)
            }
        }
    }

    @Nested
    @DisplayName("Ride Cancellation Tests")
    inner class RideCancellationTests {

        @Test
        @DisplayName("Should cancel ride successfully")
        fun shouldCancelRideSuccessfully() {
            // Given
            val rideId = UUID.randomUUID()
            val reason = CancellationReason.PASSENGER_CANCELLED
            val cancelledBy = UUID.randomUUID()
            val cancelledRide = createTestRide().apply {
                // Cancellation will work because testRide is in REQUESTED status
                updateStatus(RideStatus.CANCELLED)
            }

            every { rideRepository.findByIdWithLock(rideId) } returns testRide
            every { rideStateTransitionRepository.save(any()) } returns mockk<RideStateTransition>()
            every { rideRepository.save(any()) } answers { 
                // Just return the ride as-is - the service already updated it to CANCELLED
                firstArg<Ride>()
            }
            every { rideEventProducer.publishRideCancelled(any()) } just Runs

            // When
            val result = rideService.cancelRide(rideId, reason, cancelledBy)

            // Then
            assertThat(result).isNotNull
            assertThat(result.status).isEqualTo(RideStatus.CANCELLED)
            verify { rideStateTransitionRepository.save(any()) }
            verify { rideRepository.save(any()) }
            verify { rideEventProducer.publishRideCancelled(any()) }
            verify { redisTemplate.delete(any<String>()) }
            verify { messagingTemplate.convertAndSendToUser(any(), any(), any()) }
        }

        @Test
        @DisplayName("Should throw exception when cancelling non-cancellable ride")
        fun shouldThrowExceptionWhenCancellingNonCancellableRide() {
            // Given
            val rideId = UUID.randomUUID()
            val reason = CancellationReason.PASSENGER_CANCELLED
            val cancelledBy = UUID.randomUUID()
            val completedRide = createTestRide().apply { 
                // Need to go through proper state transitions to reach COMPLETED
                updateStatus(RideStatus.MATCHED)
                updateStatus(RideStatus.DRIVER_ASSIGNED)
                updateStatus(RideStatus.EN_ROUTE_TO_PICKUP)
                updateStatus(RideStatus.ARRIVED_AT_PICKUP)
                updateStatus(RideStatus.ON_TRIP)
                updateStatus(RideStatus.COMPLETED)
            }

            every { rideRepository.findByIdWithLock(rideId) } returns completedRide

            // When & Then
            assertThrows<InvalidRideStateException> {
                rideService.cancelRide(rideId, reason, cancelledBy)
            }
        }
    }

    @Nested
    @DisplayName("Driver Assignment Tests")
    inner class DriverAssignmentTests {

        @Test
        @DisplayName("Should assign driver to ride")
        fun shouldAssignDriverToRide() {
            // Given
            val rideId = UUID.randomUUID()
            val driverId = UUID.randomUUID()
            val matchedRide = createTestRide().apply {
                // Need to be in MATCHED state to assign driver
                updateStatus(RideStatus.MATCHED)
            }

            every { rideRepository.findByIdWithLock(rideId) } returns matchedRide
            every { rideRepository.save(any()) } answers { 
                firstArg<Ride>()  // Just return the ride, it's already modified by the service
            }

            // When
            val result = rideService.assignDriver(rideId, driverId)

            // Then
            assertThat(result).isNotNull
            assertThat(result.driverId).isEqualTo(driverId)
            verify { rideRepository.save(any()) }
        }

        @Test
        @DisplayName("Should throw exception when assigning driver to non-existent ride")
        fun shouldThrowExceptionWhenAssigningDriverToNonExistentRide() {
            // Given
            val rideId = UUID.randomUUID()
            val driverId = UUID.randomUUID()

            every { rideRepository.findByIdWithLock(rideId) } returns null

            // When & Then
            assertThrows<RideNotFoundException> {
                rideService.assignDriver(rideId, driverId)
            }
        }
    }

    @Nested
    @DisplayName("Ride Completion Tests")
    inner class RideCompletionTests {

        @Test
        @DisplayName("Should complete ride successfully")
        fun shouldCompleteRideSuccessfully() {
            // Given
            val rideId = UUID.randomUUID()
            val distance = 5000
            val duration = 1200
            val driverId = UUID.randomUUID()
            val rideWithDriver = createTestRide().apply { 
                // Set ride to ON_TRIP status (valid for completion)
                updateStatus(RideStatus.MATCHED)
                assignDriver(driverId)  // This automatically changes status to DRIVER_ASSIGNED
                updateStatus(RideStatus.EN_ROUTE_TO_PICKUP)
                updateStatus(RideStatus.ARRIVED_AT_PICKUP)
                updateStatus(RideStatus.ON_TRIP)
            }

            every { rideRepository.findByIdWithLock(rideId) } returns rideWithDriver
            every { fareCalculationService.calculateFinalFare(any(), any(), any(), any()) } returns BigDecimal("7500")
            every { rideStateTransitionRepository.save(any()) } returns mockk<RideStateTransition>()
            every { rideRepository.save(any()) } answers { 
                val savedRide = firstArg<Ride>()
                // The service updates status to COMPLETED, so we return the updated ride
                savedRide
            }
            every { rideEventProducer.publishRideCompleted(any()) } just Runs
            every { messagingTemplate.convertAndSendToUser(any(), any(), any()) } just Runs

            // When
            val result = rideService.completeRide(rideId, distance, duration, driverId)

            // Then
            assertThat(result).isNotNull
            assertThat(result.status).isEqualTo(RideStatus.COMPLETED)
            verify { fareCalculationService.calculateFinalFare(any(), any(), any(), any()) }
            verify { rideStateTransitionRepository.save(any()) }
            verify { rideRepository.save(any()) }
            verify { rideEventProducer.publishRideCompleted(any()) }
            verify { redisTemplate.delete(any<String>()) }
            verify { messagingTemplate.convertAndSendToUser(any(), any(), any()) }
        }

        @Test
        @DisplayName("Should throw exception when completing ride with wrong driver")
        fun shouldThrowExceptionWhenCompletingRideWithWrongDriver() {
            // Given
            val rideId = UUID.randomUUID()
            val distance = 5000
            val duration = 1200
            val driverId = UUID.randomUUID()
            val wrongDriverId = UUID.randomUUID()
            val rideWithDriver = createTestRide().apply { 
                // Set ride to ON_TRIP status with assigned driver
                updateStatus(RideStatus.MATCHED)
                assignDriver(driverId)  // This automatically changes status to DRIVER_ASSIGNED
                // Don't call updateStatus(RideStatus.DRIVER_ASSIGNED) - it's already done by assignDriver
                updateStatus(RideStatus.EN_ROUTE_TO_PICKUP)
                updateStatus(RideStatus.ARRIVED_AT_PICKUP)
                updateStatus(RideStatus.ON_TRIP)
            }

            every { rideRepository.findByIdWithLock(rideId) } returns rideWithDriver

            // When & Then
            assertThrows<InvalidRideStateException> {
                rideService.completeRide(rideId, distance, duration, wrongDriverId)
            }
        }
    }

    @Nested
    @DisplayName("Ride Rating Tests")
    inner class RideRatingTests {

        @Test
        @DisplayName("Should update passenger rating")
        fun shouldUpdatePassengerRating() {
            // Given
            val rideId = UUID.randomUUID()
            val rating = 5
            val passengerId = UUID.randomUUID()
            val completedRide = createTestRide(passengerId).apply { 
                // Go through proper state transitions
                updateStatus(RideStatus.MATCHED)
                updateStatus(RideStatus.DRIVER_ASSIGNED)
                updateStatus(RideStatus.EN_ROUTE_TO_PICKUP)
                updateStatus(RideStatus.ARRIVED_AT_PICKUP)
                updateStatus(RideStatus.ON_TRIP)
                updateStatus(RideStatus.COMPLETED)
            }

            every { rideRepository.findByIdWithLock(rideId) } returns completedRide
            every { rideRepository.save(any()) } answers { firstArg<Ride>() }

            // When
            val result = rideService.updateRideRating(rideId, rating, true, passengerId)

            // Then
            assertThat(result).isNotNull
            assertThat(result.status).isEqualTo(RideStatus.COMPLETED)
            verify { rideRepository.save(any()) }
        }

        @Test
        @DisplayName("Should throw exception when rating non-completed ride")
        fun shouldThrowExceptionWhenRatingNonCompletedRide() {
            // Given
            val rideId = UUID.randomUUID()
            val rating = 5
            val passengerId = UUID.randomUUID()

            every { rideRepository.findByIdWithLock(rideId) } returns testRide

            // When & Then
            assertThrows<InvalidRideStateException> {
                rideService.updateRideRating(rideId, rating, true, passengerId)
            }
        }

        @Test
        @DisplayName("Should throw exception when unauthorized user tries to rate")
        fun shouldThrowExceptionWhenUnauthorizedUserTriesToRate() {
            // Given
            val rideId = UUID.randomUUID()
            val rating = 5
            val unauthorizedUserId = UUID.randomUUID()
            val originalPassengerId = UUID.randomUUID()
            val completedRide = createTestRide(originalPassengerId).apply { 
                // Go through proper state transitions
                updateStatus(RideStatus.MATCHED)
                updateStatus(RideStatus.DRIVER_ASSIGNED)
                updateStatus(RideStatus.EN_ROUTE_TO_PICKUP)
                updateStatus(RideStatus.ARRIVED_AT_PICKUP)
                updateStatus(RideStatus.ON_TRIP)
                updateStatus(RideStatus.COMPLETED)
            }

            every { rideRepository.findByIdWithLock(rideId) } returns completedRide

            // When & Then
            assertThrows<InvalidRideStateException> {
                rideService.updateRideRating(rideId, rating, true, unauthorizedUserId)
            }
        }
    }

    @Nested
    @DisplayName("Ride History Tests")
    inner class RideHistoryTests {

        @Test
        @DisplayName("Should get ride history")
        fun shouldGetRideHistory() {
            // Given
            val userId = UUID.randomUUID()
            val rides = PageImpl(listOf(testRide))

            every { rideRepository.findAll(any<Pageable>()) } returns rides

            // When
            val result = rideService.getRideHistory(userId, false, 10, 0)

            // Then
            assertThat(result).hasSize(1)
            assertThat(result[0].id).isEqualTo(testRide.id)
        }
    }

    private fun createTestRide(passengerId: UUID = UUID.randomUUID()): Ride {
        return Ride(
            passengerId = passengerId,
            pickupLocation = Location(37.5665, 126.9780, "Seoul Station", "8830e1d8dffffff"),
            dropoffLocation = Location(37.5759, 126.9768, "Myeongdong", "8830e1d89ffffff"),
            fare = createTestFare()
        )
    }

    private fun createTestRideRequest(): RideRequestDto {
        return RideRequestDto(
            passengerId = UUID.randomUUID(),
            pickupLocation = RequestLocationDto(
                latitude = 37.5665,
                longitude = 126.9780,
                address = "Seoul Station",
                h3Index = "8830e1d8dffffff"
            ),
            dropoffLocation = RequestLocationDto(
                latitude = 37.5759,
                longitude = 126.9768,
                address = "Myeongdong",
                h3Index = "8830e1d89ffffff"
            ),
            estimatedFare = null
        )
    }

    private fun createTestFare(): Fare {
        return Fare(
            baseFare = BigDecimal("5000"),
            surgeMultiplier = BigDecimal("1.0"),
            totalFare = BigDecimal("5000"),
            currency = "KRW"
        )
    }
}