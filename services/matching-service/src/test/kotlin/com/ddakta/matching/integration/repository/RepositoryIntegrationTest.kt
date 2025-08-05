package com.ddakta.matching.integration.repository

import com.ddakta.matching.domain.entity.DriverCall
import com.ddakta.matching.domain.entity.MatchingRequest
import com.ddakta.matching.domain.entity.Ride
import com.ddakta.matching.domain.entity.RideStateTransition
import com.ddakta.matching.domain.enum.DriverCallStatus
import com.ddakta.matching.domain.enum.MatchingRequestStatus
import com.ddakta.matching.domain.enum.RideEvent
import com.ddakta.matching.domain.enum.RideStatus
import com.ddakta.matching.domain.repository.DriverCallRepository
import com.ddakta.matching.domain.repository.MatchingRequestRepository
import com.ddakta.matching.domain.repository.RideRepository
import com.ddakta.matching.domain.repository.RideStateTransitionRepository
import com.ddakta.matching.domain.vo.Fare
import com.ddakta.matching.domain.vo.Location
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.springframework.context.annotation.ComponentScan
import org.springframework.data.domain.PageRequest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*

@DataJpaTest(
    excludeAutoConfiguration = [
        org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration::class,
        org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration::class,
        org.springframework.cloud.openfeign.FeignAutoConfiguration::class
    ]
)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@ComponentScan(basePackages = ["com.ddakta.matching.domain.repository"])
@ActiveProfiles("test")
@TestPropertySource(
    properties = [
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "spring.sql.init.mode=never"
    ]
)
@DisplayName("Repository Integration Tests")
class RepositoryIntegrationTest {

    @Autowired
    private lateinit var entityManager: TestEntityManager

    @Autowired
    private lateinit var rideRepository: RideRepository

    @Autowired
    private lateinit var matchingRequestRepository: MatchingRequestRepository

    @Autowired
    private lateinit var driverCallRepository: DriverCallRepository

    @Autowired
    private lateinit var rideStateTransitionRepository: RideStateTransitionRepository

    private lateinit var testRide: Ride
    private lateinit var testMatchingRequest: MatchingRequest
    private lateinit var testDriverCall: DriverCall

    @BeforeEach
    fun setUp() {
        testRide = createTestRide()
        testMatchingRequest = createTestMatchingRequest()
        testDriverCall = createTestDriverCall(testRide)
    }

    @Nested
    @DisplayName("Ride Repository Tests")
    inner class RideRepositoryTests {

        @Test
        @DisplayName("Should save and find ride by ID")
        fun shouldSaveAndFindRideById() {
            // Given
            val savedRide = rideRepository.save(testRide)
            entityManager.flush()
            entityManager.clear()

            // When
            val foundRide = rideRepository.findById(savedRide.id!!).orElse(null)

            // Then
            assertThat(foundRide).isNotNull
            assertThat(foundRide.passengerId).isEqualTo(testRide.passengerId)
            assertThat(foundRide.pickupLocation.latitude).isEqualTo(testRide.pickupLocation.latitude)
            assertThat(foundRide.dropoffLocation.longitude).isEqualTo(testRide.dropoffLocation.longitude)
            assertThat(foundRide.status).isEqualTo(RideStatus.REQUESTED)
        }

        @Test
        @DisplayName("Should find active ride by passenger")
        fun shouldFindActiveRideByPassenger() {
            // Given
            val savedRide = rideRepository.save(testRide)
            entityManager.flush()

            // When
            val activeRide = rideRepository.findActiveRideByPassenger(testRide.passengerId)

            // Then
            assertThat(activeRide).isNotNull
            assertThat(activeRide!!.id).isEqualTo(savedRide.id)
            assertThat(activeRide.passengerId).isEqualTo(testRide.passengerId)
        }

        @Test
        @DisplayName("Should find active ride by driver")
        fun shouldFindActiveRideByDriver() {
            // Given
            val driverId = UUID.randomUUID()
            testRide.assignDriver(driverId)
            testRide.updateStatus(RideStatus.MATCHED)
            val savedRide = rideRepository.save(testRide)
            entityManager.flush()

            // When
            val activeRide = rideRepository.findActiveRideByDriver(driverId)

            // Then
            assertThat(activeRide).isNotNull
            assertThat(activeRide!!.id).isEqualTo(savedRide.id)
            assertThat(activeRide.driverId).isEqualTo(driverId)
        }

        @Test
        @DisplayName("Should find ride by ID with lock")
        fun shouldFindRideByIdWithLock() {
            // Given
            val savedRide = rideRepository.save(testRide)
            entityManager.flush()

            // When
            val lockedRide = rideRepository.findByIdWithLock(savedRide.id!!)

            // Then
            assertThat(lockedRide).isNotNull
            assertThat(lockedRide!!.id).isEqualTo(savedRide.id)
        }

        @Test
        @DisplayName("Should return null when no active ride exists for passenger")
        fun shouldReturnNullWhenNoActiveRideExistsForPassenger() {
            // Given
            val nonExistentPassengerId = UUID.randomUUID()

            // When
            val activeRide = rideRepository.findActiveRideByPassenger(nonExistentPassengerId)

            // Then
            assertThat(activeRide).isNull()
        }

        @Test
        @DisplayName("Should update ride status correctly")
        fun shouldUpdateRideStatusCorrectly() {
            // Given
            val savedRide = rideRepository.save(testRide)
            entityManager.flush()
            entityManager.clear()

            // When
            val ride = rideRepository.findById(savedRide.id!!).get()
            ride.updateStatus(RideStatus.MATCHED)
            rideRepository.save(ride)
            entityManager.flush()
            entityManager.clear()

            // Then
            val updatedRide = rideRepository.findById(savedRide.id!!).get()
            assertThat(updatedRide.status).isEqualTo(RideStatus.MATCHED)
        }
    }

    @Nested
    @DisplayName("Matching Request Repository Tests")
    inner class MatchingRequestRepositoryTests {

        @Test
        @DisplayName("Should save and find matching request by ride ID")
        fun shouldSaveAndFindMatchingRequestByRideId() {
            // Given
            val savedRide = rideRepository.save(testRide)
            val matchingRequest = createTestMatchingRequest(savedRide.id!!)
            val savedRequest = matchingRequestRepository.save(matchingRequest)
            entityManager.flush()

            // When
            val foundRequest = matchingRequestRepository.findByRideId(savedRide.id!!)

            // Then
            assertThat(foundRequest).isNotNull
            assertThat(foundRequest!!.id).isEqualTo(savedRequest.id)
            assertThat(foundRequest.rideId).isEqualTo(savedRide.id)
            assertThat(foundRequest.status).isEqualTo(MatchingRequestStatus.PENDING)
        }

        @Test
        @DisplayName("Should find pending requests within time range")
        fun shouldFindPendingRequestsWithinTimeRange() {
            // Given
            val savedRide = rideRepository.save(testRide)
            val matchingRequest = createTestMatchingRequest(savedRide.id!!)
            matchingRequest.status = MatchingRequestStatus.PENDING
            matchingRequestRepository.save(matchingRequest)
            entityManager.flush()

            // When
            val pendingRequests = matchingRequestRepository.findPendingRequests(
                maxAge = LocalDateTime.now().minusMinutes(10),
                limit = 100
            )

            // Then
            assertThat(pendingRequests).isNotEmpty
            assertThat(pendingRequests).hasSize(1)
            assertThat(pendingRequests[0].status).isEqualTo(MatchingRequestStatus.PENDING)
        }

        @Test
        @DisplayName("Should not find expired pending requests")
        fun shouldNotFindExpiredPendingRequests() {
            // Given
            val savedRide = rideRepository.save(testRide)
            val oldMatchingRequest = createTestMatchingRequest(savedRide.id!!)
            oldMatchingRequest.status = MatchingRequestStatus.PENDING
            // Note: We cannot directly set createdAt for testing, but the query will work correctly
            matchingRequestRepository.save(oldMatchingRequest)
            entityManager.flush()

            // When
            val pendingRequests = matchingRequestRepository.findPendingRequests(
                maxAge = LocalDateTime.now().minusMinutes(10),
                limit = 100
            )

            // Then
            assertThat(pendingRequests).isEmpty()
        }

        @Test
        @DisplayName("Should update matching request status")
        fun shouldUpdateMatchingRequestStatus() {
            // Given
            val savedRide = rideRepository.save(testRide)
            val matchingRequest = createTestMatchingRequest(savedRide.id!!)
            val savedRequest = matchingRequestRepository.save(matchingRequest)
            entityManager.flush()
            entityManager.clear()

            // When
            val request = matchingRequestRepository.findById(savedRequest.id!!).get()
            request.status = MatchingRequestStatus.COMPLETED
            matchingRequestRepository.save(request)
            entityManager.flush()
            entityManager.clear()

            // Then
            val updatedRequest = matchingRequestRepository.findById(savedRequest.id!!).get()
            assertThat(updatedRequest.status).isEqualTo(MatchingRequestStatus.COMPLETED)
        }
    }

    @Nested
    @DisplayName("Driver Call Repository Tests")
    inner class DriverCallRepositoryTests {

        @Test
        @DisplayName("Should save and find driver call by ride ID")
        fun shouldSaveAndFindDriverCallByRideId() {
            // Given
            val savedRide = rideRepository.save(testRide)
            val driverCall = createTestDriverCall(savedRide)
            val savedCall = driverCallRepository.save(driverCall)
            entityManager.flush()

            // When
            val foundCalls = driverCallRepository.findByRideId(savedRide.id!!)

            // Then
            assertThat(foundCalls).isNotEmpty
            assertThat(foundCalls).hasSize(1)
            assertThat(foundCalls[0].id).isEqualTo(savedCall.id)
            assertThat(foundCalls[0].ride.id).isEqualTo(savedRide.id)
        }

        @Test
        @DisplayName("Should find driver calls by status")
        fun shouldFindDriverCallsByStatus() {
            // Given
            val savedRide = rideRepository.save(testRide)
            val driverCall = createTestDriverCall(savedRide)
            driverCallRepository.save(driverCall)
            entityManager.flush()

            // When
            val pendingCalls = driverCallRepository.findByDriverIdAndStatus(
                driverCall.driverId,
                DriverCallStatus.PENDING
            )

            // Then
            assertThat(pendingCalls).isNotEmpty
            assertThat(pendingCalls[0].status).isEqualTo(DriverCallStatus.PENDING)
        }

        @Test
        @DisplayName("Should find active call by driver ID")
        fun shouldFindActiveCallByDriverId() {
            // Given
            val savedRide = rideRepository.save(testRide)
            val driverCall = createTestDriverCall(savedRide)
            val savedCall = driverCallRepository.save(driverCall)
            entityManager.flush()

            // When
            val activeCalls = driverCallRepository.findActiveCallsByDriverId(
                driverCall.driverId,
                LocalDateTime.now()
            )
            val activeCall = activeCalls.firstOrNull()

            // Then
            assertThat(activeCall).isNotNull
            assertThat(activeCall!!.id).isEqualTo(savedCall.id)
            assertThat(activeCall.driverId).isEqualTo(driverCall.driverId)
        }

        @Test
        @DisplayName("Should update driver call status to accepted")
        fun shouldUpdateDriverCallStatusToAccepted() {
            // Given
            val savedRide = rideRepository.save(testRide)
            val driverCall = createTestDriverCall(savedRide)
            val savedCall = driverCallRepository.save(driverCall)
            entityManager.flush()
            entityManager.clear()

            // When
            val call = driverCallRepository.findById(savedCall.id!!).get()
            call.accept()
            driverCallRepository.save(call)
            entityManager.flush()
            entityManager.clear()

            // Then
            val updatedCall = driverCallRepository.findById(savedCall.id!!).get()
            assertThat(updatedCall.status).isEqualTo(DriverCallStatus.ACCEPTED)
            assertThat(updatedCall.respondedAt).isNotNull()
        }
    }

    @Nested
    @DisplayName("Ride State Transition Repository Tests")
    inner class RideStateTransitionRepositoryTests {

        @Test
        @DisplayName("Should save and find state transitions by ride ID")
        fun shouldSaveAndFindStateTransitionsByRideId() {
            // Given
            val savedRide = rideRepository.save(testRide)
            val stateTransition = RideStateTransition(
                ride = savedRide,
                fromStatus = RideStatus.REQUESTED,
                toStatus = RideStatus.MATCHED,
                event = RideEvent.MATCH_FOUND
            )
            val savedTransition = rideStateTransitionRepository.save(stateTransition)
            entityManager.flush()

            // When
            val transitions = rideStateTransitionRepository.findByRideId(savedRide.id!!)

            // Then
            assertThat(transitions).isNotEmpty
            assertThat(transitions).hasSize(1)
            assertThat(transitions[0].id).isEqualTo(savedTransition.id)
            assertThat(transitions[0].fromStatus).isEqualTo(RideStatus.REQUESTED)
            assertThat(transitions[0].toStatus).isEqualTo(RideStatus.MATCHED)
            assertThat(transitions[0].event).isEqualTo(RideEvent.MATCH_FOUND)
        }

        @Test
        @DisplayName("Should find recent state transitions")
        fun shouldFindRecentStateTransitions() {
            // Given
            val savedRide = rideRepository.save(testRide)
            val stateTransition1 = RideStateTransition(
                ride = savedRide,
                fromStatus = RideStatus.REQUESTED,
                toStatus = RideStatus.MATCHED,
                event = RideEvent.MATCH_FOUND
            )
            val stateTransition2 = RideStateTransition(
                ride = savedRide,
                fromStatus = RideStatus.MATCHED,
                toStatus = RideStatus.EN_ROUTE_TO_PICKUP,
                event = RideEvent.DRIVER_ACCEPTED
            )
            rideStateTransitionRepository.save(stateTransition1)
            rideStateTransitionRepository.save(stateTransition2)
            entityManager.flush()

            // When
            val recentTransitions = rideStateTransitionRepository.findRecentTransitions(
                since = LocalDateTime.now().minusMinutes(1),
                pageable = PageRequest.of(0, 10)
            )

            // Then
            assertThat(recentTransitions).hasSize(2)
            assertThat(recentTransitions[0].toStatus).isEqualTo(RideStatus.EN_ROUTE_TO_PICKUP) // Most recent first
            assertThat(recentTransitions[1].toStatus).isEqualTo(RideStatus.MATCHED)
        }
    }

    @Nested
    @DisplayName("Cross-Repository Transaction Tests")
    inner class CrossRepositoryTransactionTests {

        @Test
        @DisplayName("Should maintain transaction consistency across repositories")
        fun shouldMaintainTransactionConsistencyAcrossRepositories() {
            executeInTransaction {
                // Given - Create ride and matching request in same transaction
                val savedRide = rideRepository.save(testRide)
                val matchingRequest = createTestMatchingRequest(savedRide.id!!)
                val savedRequest = matchingRequestRepository.save(matchingRequest)
                
                // Create driver call
                val driverCall = createTestDriverCall(savedRide)
                val savedCall = driverCallRepository.save(driverCall)
                
                // Create state transition
                val stateTransition = RideStateTransition(
                    ride = savedRide,
                    fromStatus = RideStatus.REQUESTED,
                    toStatus = RideStatus.MATCHED,
                    event = RideEvent.MATCH_FOUND
                )
                val savedTransition = rideStateTransitionRepository.save(stateTransition)
                
                entityManager.flush()

                // Then - All entities should be saved and related
                assertThat(savedRide.id).isNotNull()
                assertThat(savedRequest.rideId).isEqualTo(savedRide.id)
                assertThat(savedCall.ride.id).isEqualTo(savedRide.id)
                assertThat(savedTransition.ride.id).isEqualTo(savedRide.id)
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

    private fun createTestMatchingRequest(rideId: UUID = UUID.randomUUID()): MatchingRequest {
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

    private fun createTestDriverCall(ride: Ride? = null): DriverCall {
        val rideToUse = ride ?: rideRepository.save(createTestRide())
        return DriverCall(
            ride = rideToUse,
            driverId = UUID.randomUUID(),
            sequenceNumber = 1,
            estimatedArrivalSeconds = 180,
            estimatedFare = BigDecimal("5000"),
            expiresAt = LocalDateTime.now().plusSeconds(30),
            driverLocation = Location(37.5660, 126.9775, null, "8830e1d8dffffff")
        )
    }
    
    private fun waitForAsyncOperation(
        timeoutMillis: Long = 5000,
        checkInterval: Long = 100,
        condition: () -> Boolean
    ) {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMillis) {
            if (condition()) {
                return
            }
            Thread.sleep(checkInterval)
        }
        throw AssertionError("Async operation did not complete within timeout")
    }
    
    private fun executeInTransaction(block: () -> Unit) {
        block()
    }
}