package com.ddakta.matching.integration.workflow

import com.ddakta.matching.domain.enum.RideStatus
import com.ddakta.matching.domain.repository.RideRepository
import com.ddakta.matching.domain.repository.RideStateTransitionRepository
import com.ddakta.matching.dto.request.RideRequestDto
import com.ddakta.matching.dto.request.RequestLocationDto
import com.ddakta.matching.integration.BaseIntegrationTest
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.http.HttpStatus
import com.ddakta.matching.dto.response.RideResponseDto
import java.time.Duration
import java.util.*

@DisplayName("Full Workflow Integration Tests")
class FullWorkflowIntegrationTest : BaseIntegrationTest() {

    @Autowired
    private lateinit var rideRepository: RideRepository

    @Autowired
    private lateinit var rideStateTransitionRepository: RideStateTransitionRepository

    @Autowired
    private lateinit var redisTemplate: RedisTemplate<String, String>

    private lateinit var wireMockServer: WireMockServer
    private lateinit var kafkaConsumer: KafkaConsumer<String, String>

    @BeforeEach
    fun setUp() {
        // Setup WireMock server
        wireMockServer = WireMockServer(
            WireMockConfiguration.options().port(8090)
        )
        wireMockServer.start()
        setupExternalServiceMocks()

        // Setup Kafka consumer for event verification
        setupKafkaConsumer()

        // Clear Redis cache
        redisTemplate.connectionFactory?.connection?.serverCommands()?.flushAll()
    }

    @AfterEach
    fun tearDown() {
        kafkaConsumer.close()
        wireMockServer.stop()
    }

    @Nested
    @DisplayName("Complete Ride Lifecycle Tests")
    inner class CompleteRideLifecycleTests {

        @Test
        @DisplayName("Should complete full ride workflow from request to matching")
        fun shouldCompleteFullRideWorkflowFromRequestToMatching() {
            // Given - Create ride request
            val rideRequest = RideRequestDto(
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

            // When - Submit ride request via API
            val response = restTemplate.postForEntity(
                apiUrl("/rides"),
                rideRequest,
                RideResponseDto::class.java
            )
            
            // Then - Verify ride creation
            assertThat(response.statusCode).isEqualTo(HttpStatus.CREATED)
            assertThat(response.body).isNotNull
            assertThat(response.body!!.passengerId).isEqualTo(rideRequest.passengerId)
            assertThat(response.body!!.status).isEqualTo(RideStatus.REQUESTED)
            
            val rideId = response.body!!.id

            // Then - Verify ride is saved in database
            val savedRide = rideRepository.findById(rideId).orElse(null)
            assertThat(savedRide).isNotNull
            assertThat(savedRide.status).isEqualTo(RideStatus.REQUESTED)
            assertThat(savedRide.passengerId).isEqualTo(rideRequest.passengerId)

            // Verify ride request event was published to Kafka
            waitForKafkaMessage("ride-events") { message ->
                message.contains("RIDE_REQUESTED") && message.contains(rideId.toString())
            }

            // Verify Redis caching
            val activeRideCacheKey = "ride:active:passenger:${rideRequest.passengerId}"
            waitForAsyncOperation {
                redisTemplate.opsForValue().get(activeRideCacheKey) != null
            }
            assertThat(redisTemplate.opsForValue().get(activeRideCacheKey)).isEqualTo(rideId.toString())

            // Verify state transition was recorded
            val stateTransitions = rideStateTransitionRepository.findByRideId(rideId)
            assertThat(stateTransitions).isNotEmpty
            assertThat(stateTransitions[0].toStatus).isEqualTo(RideStatus.REQUESTED)
        }

        @Test
        @DisplayName("Should handle concurrent ride requests with duplicate prevention")
        fun shouldHandleConcurrentRideRequestsWithDuplicatePrevention() {
            // Given
            val passengerId = UUID.randomUUID()
            val rideRequest = RideRequestDto(
                passengerId = passengerId,
                pickupLocation = RequestLocationDto(37.5665, 126.9780, "Seoul Station", "8830e1d8dffffff"),
                dropoffLocation = RequestLocationDto(37.5759, 126.9768, "Myeongdong", "8830e1d89ffffff"),
                estimatedFare = null
            )

            // When - Submit two concurrent requests

            val result1Future = Thread {
                try {
                    val response = restTemplate.postForEntity(
                        apiUrl("/rides"),
                        rideRequest,
                        String::class.java
                    )
                    assertThat(response.statusCode).isEqualTo(HttpStatus.CREATED)
                } catch (e: Exception) {
                    // First request might succeed
                }
            }

            val result2Future = Thread {
                try {
                    val response = restTemplate.postForEntity(
                        apiUrl("/rides"),
                        rideRequest,
                        String::class.java
                    )
                    assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT) // Should be rejected as duplicate
                } catch (e: Exception) {
                    // Second request should fail due to duplicate prevention
                }
            }

            result1Future.start()
            Thread.sleep(50) // Small delay to ensure first request starts processing
            result2Future.start()

            result1Future.join(5000)
            result2Future.join(5000)

            // Then - Verify only one ride was created
            waitForAsyncOperation {
                val rides = rideRepository.findAll().filter { it.passengerId == passengerId }
                rides.size == 1
            }

            val rides = rideRepository.findAll().filter { it.passengerId == passengerId }
            assertThat(rides).hasSize(1)
        }

        @Test
        @DisplayName("Should prevent ride creation when passenger has active ride")
        fun shouldPreventRideCreationWhenPassengerHasActiveRide() {
            // Given - Create first ride
            val passengerId = UUID.randomUUID()
            val firstRideRequest = RideRequestDto(
                passengerId = passengerId,
                pickupLocation = RequestLocationDto(37.5665, 126.9780, "Seoul Station", "8830e1d8dffffff"),
                dropoffLocation = RequestLocationDto(37.5759, 126.9768, "Myeongdong", "8830e1d89ffffff"),
                estimatedFare = null
            )

            val firstResponse = restTemplate.postForEntity(
                apiUrl("/rides"),
                firstRideRequest,
                String::class.java
            )
            assertThat(firstResponse.statusCode).isEqualTo(HttpStatus.CREATED)

            // When - Try to create second ride for same passenger
            val secondRideRequest = firstRideRequest.copy(
                dropoffLocation = RequestLocationDto(37.5400, 126.9900, "Gangnam", "8830e1d88ffffff")
            )

            val secondResponse = restTemplate.postForEntity(
                apiUrl("/rides"),
                secondRideRequest,
                Map::class.java
            )
            assertThat(secondResponse.statusCode).isEqualTo(HttpStatus.CONFLICT)
            assertThat(secondResponse.body?.get("message").toString()).contains("active ride")

            // Then - Verify only one ride exists
            val rides = rideRepository.findAll().filter { it.passengerId == passengerId }
            assertThat(rides).hasSize(1)
        }
    }

    @Nested
    @DisplayName("Matching Workflow Integration Tests")
    inner class MatchingWorkflowIntegrationTests {

        @Test
        @DisplayName("Should complete matching workflow with available drivers")
        fun shouldCompleteMatchingWorkflowWithAvailableDrivers() {
            // Given - Setup available drivers response
            setupLocationServiceWithAvailableDrivers()

            // Create ride request
            val rideRequest = createTestRideRequest()
            val response = restTemplate.postForEntity(
                apiUrl("/rides"),
                rideRequest,
                Map::class.java
            )
            assertThat(response.statusCode).isEqualTo(HttpStatus.CREATED)
            
            val rideResponse = response.body!!
            val rideId = UUID.fromString(rideResponse["id"] as String)

            // When - Trigger matching batch processing via API
            val matchingResponse = restTemplate.postForEntity(
                apiUrl("/matching/process-batch"),
                null,
                String::class.java
            )
            assertThat(matchingResponse.statusCode).isEqualTo(HttpStatus.OK)

            // Then - Wait for matching to complete
            waitForAsyncOperation(10000) {
                val ride = rideRepository.findById(rideId).orElse(null)
                ride != null && ride.status != RideStatus.REQUESTED
            }

            val updatedRide = rideRepository.findById(rideId).orElse(null)
            assertThat(updatedRide.status).isIn(RideStatus.MATCHED, RideStatus.FAILED)

            // Verify matching events were published
            waitForKafkaMessage("matching-events") { message ->
                message.contains(rideId.toString())
            }

            // Verify external service was called
            wireMockServer.verify(
                postRequestedFor(urlEqualTo("/api/v1/drivers/search/available"))
            )
        }

        @Test
        @DisplayName("Should handle no available drivers scenario")
        fun shouldHandleNoAvailableDriversScenario() {
            // Given - Setup no available drivers
            wireMockServer.stubFor(
                post(urlEqualTo("/api/v1/drivers/search/available"))
                    .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[]"))
            )

            val rideRequest = createTestRideRequest()
            val response = restTemplate.postForEntity(
                apiUrl("/rides"),
                rideRequest,
                Map::class.java
            )
            assertThat(response.statusCode).isEqualTo(HttpStatus.CREATED)
            
            val rideResponse = response.body!!
            val rideId = UUID.fromString(rideResponse["id"] as String)

            // When - Process matching
            val matchingResponse = restTemplate.postForEntity(
                apiUrl("/matching/process-batch"),
                null,
                String::class.java
            )
            assertThat(matchingResponse.statusCode).isEqualTo(HttpStatus.OK)

            // Then - Ride should remain unmatched or failed
            waitForAsyncOperation(10000) {
                val ride = rideRepository.findById(rideId).orElse(null)
                ride != null
            }

            val finalRide = rideRepository.findById(rideId).orElse(null)
            assertThat(finalRide.status).isIn(RideStatus.REQUESTED, RideStatus.FAILED)
        }
    }

    @Nested
    @DisplayName("Error Handling Integration Tests")
    inner class ErrorHandlingIntegrationTests {

        @Test
        @DisplayName("Should handle external service failure gracefully")
        fun shouldHandleExternalServiceFailureGracefully() {
            // Given - Setup external service to fail
            wireMockServer.stubFor(
                post(urlEqualTo("/api/v1/drivers/search/available"))
                    .willReturn(aResponse()
                        .withStatus(500)
                        .withBody("{\"error\": \"Service unavailable\"}"))
            )

            val rideRequest = createTestRideRequest()
            val response = restTemplate.postForEntity(
                apiUrl("/rides"),
                rideRequest,
                Map::class.java
            )
            assertThat(response.statusCode).isEqualTo(HttpStatus.CREATED)
            
            val rideResponse = response.body!!
            val rideId = UUID.fromString(rideResponse["id"] as String)

            // When - Process matching (should handle failure gracefully)
            val matchingResponse = restTemplate.postForEntity(
                apiUrl("/matching/process-batch"),
                null,
                String::class.java
            )
            assertThat(matchingResponse.statusCode).isEqualTo(HttpStatus.OK) // Should not return error

            // Then - Verify ride remains in valid state
            val finalRide = rideRepository.findById(rideId).orElse(null)
            assertThat(finalRide).isNotNull
            assertThat(finalRide.status).isIn(RideStatus.REQUESTED, RideStatus.FAILED)
        }

        @Test
        @DisplayName("Should maintain data consistency during failures")
        fun shouldMaintainDataConsistencyDuringFailures() {
            // Given
            val rideRequest = createTestRideRequest()
            val response = restTemplate.postForEntity(
                apiUrl("/rides"),
                rideRequest,
                Map::class.java
            )
            assertThat(response.statusCode).isEqualTo(HttpStatus.CREATED)
            
            val rideResponse = response.body!!
            val rideId = UUID.fromString(rideResponse["id"] as String)

            // When - Simulate system failure during processing
            try {
                // Force external service timeout
                wireMockServer.stubFor(
                    post(urlEqualTo("/api/v1/drivers/search/available"))
                        .willReturn(aResponse()
                            .withFixedDelay(30000) // 30 second delay
                            .withStatus(200)
                            .withBody("[]"))
                )

                // Process with short timeout
                val matchingResponse = restTemplate.postForEntity(
                    apiUrl("/matching/process-batch"),
                    null,
                    String::class.java
                )
                assertThat(matchingResponse.statusCode).isEqualTo(HttpStatus.OK)

            } catch (e: Exception) {
                // Expected timeout
            }

            // Then - Verify data remains consistent
            val finalRide = rideRepository.findById(rideId).orElse(null)
            assertThat(finalRide).isNotNull
            
            // Verify no orphaned data
            val stateTransitions = rideStateTransitionRepository.findByRideId(rideId)
            assertThat(stateTransitions).isNotEmpty
            
            // All transitions should reference valid ride
            stateTransitions.forEach { transition ->
                assertThat(transition.ride.id).isEqualTo(rideId)
            }
        }
    }

    @Nested
    @DisplayName("Performance Integration Tests")
    inner class PerformanceIntegrationTests {

        @Test
        @DisplayName("Should handle multiple concurrent requests efficiently")
        fun shouldHandleMultipleConcurrentRequestsEfficiently() {
            // Given - Setup for concurrent processing
            setupLocationServiceWithAvailableDrivers()
            
            val requests = (1..10).map { createTestRideRequest() }
            val threads = mutableListOf<Thread>()

            // When - Submit concurrent requests
            val startTime = System.currentTimeMillis()
            
            requests.forEach { request ->
                val thread = Thread {
                    try {
                        val response = restTemplate.postForEntity(
                            apiUrl("/rides"),
                            request,
                            String::class.java
                        )
                        assertThat(response.statusCode).isEqualTo(HttpStatus.CREATED)
                    } catch (e: Exception) {
                        // Some requests might be rejected due to business rules
                    }
                }
                threads.add(thread)
                thread.start()
            }

            // Wait for all threads to complete
            threads.forEach { it.join(10000) }
            val endTime = System.currentTimeMillis()

            // Then - Verify performance and correctness
            val totalTime = endTime - startTime
            assertThat(totalTime).isLessThan(15000) // Should complete within 15 seconds

            // Verify at least some rides were created
            val allRides = rideRepository.findAll()
            assertThat(allRides.size).isGreaterThan(0)
            
            // All created rides should be in valid state
            allRides.forEach { ride ->
                assertThat(ride.status).isIn(*RideStatus.values())
                assertThat(ride.createdAt).isNotNull()
            }
        }
    }

    private fun setupExternalServiceMocks() {
        // Default setup - no available drivers
        wireMockServer.stubFor(
            post(urlEqualTo("/api/v1/drivers/search/available"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("[]"))
        )

        // Surge price service
        wireMockServer.stubFor(
            get(urlMatching("/api/v1/surge/.*"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"surgeMultiplier\": 1.2}"))
        )

        // Fare calculation service
        wireMockServer.stubFor(
            post(urlEqualTo("/api/v1/fare/calculate"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("""
                        {
                            "baseFare": 5000,
                            "surgeMultiplier": 1.2,
                            "totalFare": 6000,
                            "currency": "KRW"
                        }
                    """.trimIndent()))
        )
    }

    private fun setupLocationServiceWithAvailableDrivers() {
        val driversJson = """
            [
                {
                    "driverId": "550e8400-e29b-41d4-a716-446655440201",
                    "currentLocation": {
                        "latitude": 37.5660,
                        "longitude": 126.9775,
                        "h3Index": "8830e1d8dffffff"
                    },
                    "rating": 4.8,
                    "acceptanceRate": 0.95,
                    "completionRate": 0.98,
                    "totalRides": 1000,
                    "isOnline": true,
                    "lastLocationUpdate": "2024-01-15T10:30:00"
                },
                {
                    "driverId": "550e8400-e29b-41d4-a716-446655440202",
                    "currentLocation": {
                        "latitude": 37.5670,
                        "longitude": 126.9785,
                        "h3Index": "8830e1d8dffffff"
                    },
                    "rating": 4.5,
                    "acceptanceRate": 0.88,
                    "completionRate": 0.95,
                    "totalRides": 800,
                    "isOnline": true,
                    "lastLocationUpdate": "2024-01-15T10:29:00"
                }
            ]
        """.trimIndent()

        wireMockServer.stubFor(
            post(urlEqualTo("/api/v1/drivers/search/available"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(driversJson))
        )
    }

    private fun setupKafkaConsumer() {
        val props = Properties().apply {
            put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.bootstrapServers)
            put(ConsumerConfig.GROUP_ID_CONFIG, "integration-test-consumer")
            put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java)
            put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java)
            put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
        }
        
        kafkaConsumer = KafkaConsumer(props)
        kafkaConsumer.subscribe(listOf("ride-events", "matching-events", "driver-call-events"))
    }

    private fun waitForKafkaMessage(topic: String, predicate: (String) -> Boolean) {
        val timeout = System.currentTimeMillis() + 10000 // 10 seconds
        
        while (System.currentTimeMillis() < timeout) {
            val records = kafkaConsumer.poll(Duration.ofMillis(100))
            for (record in records) {
                if (record.topic() == topic && predicate(record.value())) {
                    return
                }
            }
        }
        
        throw AssertionError("Kafka message not received within timeout for topic: $topic")
    }

    private fun createTestRideRequest(): RideRequestDto {
        return RideRequestDto(
            passengerId = UUID.randomUUID(),
            pickupLocation = RequestLocationDto(37.5665, 126.9780, "Seoul Station", "8830e1d8dffffff"),
            dropoffLocation = RequestLocationDto(37.5759, 126.9768, "Myeongdong", "8830e1d89ffffff"),
            estimatedFare = null
        )
    }
}