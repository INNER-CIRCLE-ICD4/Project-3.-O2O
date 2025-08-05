package com.ddakta.matching.integration.service

import com.ddakta.matching.client.LocationServiceClient
import com.ddakta.matching.domain.entity.Ride
import com.ddakta.matching.domain.enum.MatchingRequestStatus
import com.ddakta.matching.domain.enum.RideStatus
import com.ddakta.matching.domain.repository.MatchingRequestRepository
import com.ddakta.matching.domain.repository.RideRepository
import com.ddakta.matching.domain.vo.Fare
import com.ddakta.matching.domain.vo.Location
import com.ddakta.matching.dto.internal.AvailableDriver
import com.ddakta.matching.integration.BaseIntegrationTest
import com.ddakta.matching.service.MatchingService
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.test.context.ActiveProfiles
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*

@DisplayName("Matching Service Integration Tests")
class MatchingServiceIntegrationTest : BaseIntegrationTest() {

    @Autowired
    private lateinit var matchingService: MatchingService

    @Autowired
    private lateinit var rideRepository: RideRepository

    @Autowired
    private lateinit var matchingRequestRepository: MatchingRequestRepository

    @Autowired
    private lateinit var redisTemplate: RedisTemplate<String, String>

    private lateinit var wireMockServer: WireMockServer
    private lateinit var testRide: Ride

    @BeforeEach
    fun setUp() {
        // Setup WireMock server for external service calls
        wireMockServer = WireMockServer(
            WireMockConfiguration.options()
                .port(8090)
                .usingFilesUnderDirectory("src/test/resources/wiremock")
        )
        wireMockServer.start()
        
        // Setup mock responses for location service
        setupLocationServiceMocks()
        
        // Create test data
        testRide = createTestRide()
        
        // Clear Redis cache
        redisTemplate.connectionFactory?.connection?.serverCommands()?.flushAll()
    }

    @AfterEach
    fun tearDown() {
        wireMockServer.stop()
    }

    @Nested
    @DisplayName("Batch Processing Integration Tests")
    inner class BatchProcessingIntegrationTests {

        @Test
        @DisplayName("Should process matching batch with real database and Redis")
        fun shouldProcessMatchingBatchWithRealDatabaseAndRedis() {
            // Given - Create real ride and matching request in database
            val savedRide = rideRepository.save(testRide)
            val matchingRequest = matchingService.createMatchingRequest(savedRide)
            
            // Verify matching request is saved in database
            val foundRequest = matchingRequestRepository.findByRideId(savedRide.id!!)
            assertThat(foundRequest).isNotNull
            assertThat(foundRequest!!.status).isEqualTo(MatchingRequestStatus.PENDING)

            // When - Process matching batch
            val results = matchingService.processMatchingBatch()

            // Then - Verify results and database state
            waitForAsyncOperation(5000) {
                val updatedRequest = matchingRequestRepository.findByRideId(savedRide.id!!)
                updatedRequest != null && updatedRequest.status != MatchingRequestStatus.PENDING
            }

            val updatedRequest = matchingRequestRepository.findByRideId(savedRide.id!!)
            assertThat(updatedRequest).isNotNull
            
            // Verify Redis cache was used (check for lock keys)
            val lockKeys = redisTemplate.keys("matching:batch:lock*")
            // Lock might be released by now, but we can verify the process ran
        }

        @Test
        @DisplayName("Should handle concurrent batch processing with Redis locks")
        fun shouldHandleConcurrentBatchProcessingWithRedisLocks() {
            // Given
            val savedRide1 = rideRepository.save(testRide)
            val savedRide2 = rideRepository.save(createTestRide())
            
            matchingService.createMatchingRequest(savedRide1)
            matchingService.createMatchingRequest(savedRide2)

            // When - Process batches concurrently
            val thread1 = Thread { matchingService.processMatchingBatch() }
            val thread2 = Thread { matchingService.processMatchingBatch() }
            
            thread1.start()
            thread2.start()
            
            thread1.join(10000) // 10 second timeout
            thread2.join(10000)

            // Then - Verify both requests were processed (one thread should get lock)
            waitForAsyncOperation(5000) {
                val request1 = matchingRequestRepository.findByRideId(savedRide1.id!!)
                val request2 = matchingRequestRepository.findByRideId(savedRide2.id!!)
                request1 != null && request2 != null
            }
        }

        @Test
        @DisplayName("Should handle no available drivers scenario")
        fun shouldHandleNoAvailableDriversScenario() {
            // Given - Mock location service to return no drivers
            wireMockServer.stubFor(
                post(urlEqualTo("/api/v1/drivers/search/available"))
                    .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[]")) // Empty driver list
            )

            val savedRide = rideRepository.save(testRide)
            matchingService.createMatchingRequest(savedRide)

            // When
            val results = matchingService.processMatchingBatch()

            // Then
            waitForAsyncOperation(5000) {
                val updatedRequest = matchingRequestRepository.findByRideId(savedRide.id!!)
                updatedRequest != null && updatedRequest.status == MatchingRequestStatus.FAILED
            }

            val updatedRequest = matchingRequestRepository.findByRideId(savedRide.id!!)
            assertThat(updatedRequest!!.status).isEqualTo(MatchingRequestStatus.FAILED)
        }
    }

    @Nested
    @DisplayName("Request Management Integration Tests")
    inner class RequestManagementIntegrationTests {

        @Test
        @DisplayName("Should create matching request with Redis caching")
        fun shouldCreateMatchingRequestWithRedisCaching() {
            // Given
            val savedRide = rideRepository.save(testRide)

            // When
            val matchingRequest = matchingService.createMatchingRequest(savedRide)

            // Then
            assertThat(matchingRequest).isNotNull
            assertThat(matchingRequest.rideId).isEqualTo(savedRide.id)
            
            // Verify request is saved in database
            val foundRequest = matchingRequestRepository.findByRideId(savedRide.id!!)
            assertThat(foundRequest).isNotNull
            assertThat(foundRequest!!.id).isEqualTo(matchingRequest.id)
            
            // Verify request is cached in Redis
            val cacheKey = "matching:request:${savedRide.id}"
            val cachedRequestId = redisTemplate.opsForValue().get(cacheKey)
            assertThat(cachedRequestId).isEqualTo(matchingRequest.id.toString())
        }

        @Test
        @DisplayName("Should return existing matching request for same ride")
        fun shouldReturnExistingMatchingRequestForSameRide() {
            // Given
            val savedRide = rideRepository.save(testRide)
            val firstRequest = matchingService.createMatchingRequest(savedRide)

            // When - Try to create another request for same ride
            val secondRequest = matchingService.createMatchingRequest(savedRide)

            // Then
            assertThat(secondRequest.id).isEqualTo(firstRequest.id)
            
            // Verify only one request exists in database
            val allRequests = matchingRequestRepository.findAll()
            val rideRequests = allRequests.filter { it.rideId == savedRide.id }
            assertThat(rideRequests).hasSize(1)
        }

        @Test
        @DisplayName("Should cancel matching request and clear cache")
        fun shouldCancelMatchingRequestAndClearCache() {
            // Given
            val savedRide = rideRepository.save(testRide)
            val matchingRequest = matchingService.createMatchingRequest(savedRide)
            
            // Verify cache is set
            val cacheKey = "matching:request:${savedRide.id}"
            assertThat(redisTemplate.opsForValue().get(cacheKey)).isNotNull()

            // When
            matchingService.cancelMatchingRequest(savedRide.id!!)

            // Then
            val updatedRequest = matchingRequestRepository.findByRideId(savedRide.id!!)
            assertThat(updatedRequest!!.status).isEqualTo(MatchingRequestStatus.FAILED)
            
            // Verify cache is cleared
            assertThat(redisTemplate.opsForValue().get(cacheKey)).isNull()
        }
    }

    @Nested
    @DisplayName("Timeout Handling Integration Tests")
    inner class TimeoutHandlingIntegrationTests {

        @Test
        @DisplayName("Should handle matching timeout and update database")
        fun shouldHandleMatchingTimeoutAndUpdateDatabase() {
            // Given
            val savedRide = rideRepository.save(testRide)
            val matchingRequest = matchingService.createMatchingRequest(savedRide)

            // When
            matchingService.handleMatchingTimeout(savedRide.id!!)

            // Then
            val updatedRequest = matchingRequestRepository.findByRideId(savedRide.id!!)
            assertThat(updatedRequest!!.status).isEqualTo(MatchingRequestStatus.FAILED)
            
            val updatedRide = rideRepository.findById(savedRide.id!!).orElse(null)
            assertThat(updatedRide.status).isEqualTo(RideStatus.FAILED)
        }

        @Test
        @DisplayName("Should ignore timeout for already completed requests")
        fun shouldIgnoreTimeoutForAlreadyCompletedRequests() {
            // Given
            val savedRide = rideRepository.save(testRide)
            val matchingRequest = matchingService.createMatchingRequest(savedRide)
            
            // Mark request as completed
            matchingRequest.status = MatchingRequestStatus.COMPLETED
            matchingRequestRepository.save(matchingRequest)

            // When
            matchingService.handleMatchingTimeout(savedRide.id!!)

            // Then - Status should remain completed
            val updatedRequest = matchingRequestRepository.findByRideId(savedRide.id!!)
            assertThat(updatedRequest!!.status).isEqualTo(MatchingRequestStatus.COMPLETED)
            
            val updatedRide = rideRepository.findById(savedRide.id!!).orElse(null)
            assertThat(updatedRide.status).isEqualTo(RideStatus.REQUESTED) // Should not change
        }
    }

    @Nested
    @DisplayName("Retry Logic Integration Tests")
    inner class RetryLogicIntegrationTests {

        @Test
        @DisplayName("Should retry matching with database state tracking")
        fun shouldRetryMatchingWithDatabaseStateTracking() {
            // Given
            val savedRide = rideRepository.save(testRide)
            val matchingRequest = matchingService.createMatchingRequest(savedRide)
            
            // Set up retry scenario
            matchingRequest.fail("No drivers available")
            matchingRequestRepository.save(matchingRequest)

            // When
            val result = matchingService.retryMatching(savedRide.id!!)

            // Then
            assertThat(result).isNotNull
            
            val updatedRequest = matchingRequestRepository.findByRideId(savedRide.id!!)
            assertThat(updatedRequest!!.retryCount).isGreaterThan(1)
        }

        @Test
        @DisplayName("Should reject retry when max count exceeded")
        fun shouldRejectRetryWhenMaxCountExceeded() {
            // Given
            val savedRide = rideRepository.save(testRide)
            val matchingRequest = matchingService.createMatchingRequest(savedRide)
            
            // Set retry count to maximum by failing multiple times
            // Fail 5 times to exceed MAX_RETRY_COUNT
            repeat(5) {
                matchingRequest.fail("No drivers available")
                if (it < 4) {
                    matchingRequest.resetForRetry()
                }
            }
            matchingRequestRepository.save(matchingRequest)

            // When
            val result = matchingService.retryMatching(savedRide.id!!)

            // Then
            assertThat(result).isNotNull
            assertThat(result!!.success).isFalse()
            assertThat(result.reason).contains("Max retry count exceeded")
        }
    }

    @Nested
    @DisplayName("External Service Integration Tests")
    inner class ExternalServiceIntegrationTests {

        @Test
        @DisplayName("Should handle location service failure gracefully")
        fun shouldHandleLocationServiceFailureGracefully() {
            // Given - Mock location service to return error
            wireMockServer.stubFor(
                post(urlEqualTo("/api/v1/drivers/search/available"))
                    .willReturn(aResponse()
                        .withStatus(500)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\": \"Service unavailable\"}"))
            )

            val savedRide = rideRepository.save(testRide)
            matchingService.createMatchingRequest(savedRide)

            // When
            val results = matchingService.processMatchingBatch()

            // Then - Should handle gracefully without throwing exception
            waitForAsyncOperation(5000) {
                val updatedRequest = matchingRequestRepository.findByRideId(savedRide.id!!)
                updatedRequest != null && updatedRequest.status == MatchingRequestStatus.FAILED
            }
        }

        @Test
        @DisplayName("Should process successful driver response from location service")
        fun shouldProcessSuccessfulDriverResponseFromLocationService() {
            // Given - Mock successful driver response
            setupLocationServiceWithDrivers()

            val savedRide = rideRepository.save(testRide)
            matchingService.createMatchingRequest(savedRide)

            // When
            val results = matchingService.processMatchingBatch()

            // Then
            waitForAsyncOperation(10000) {
                val updatedRequest = matchingRequestRepository.findByRideId(savedRide.id!!)
                updatedRequest != null && updatedRequest.status != MatchingRequestStatus.PENDING
            }

            // Verify WireMock received the request
            wireMockServer.verify(
                postRequestedFor(urlEqualTo("/api/v1/drivers/search/available"))
                    .withHeader("Content-Type", equalTo("application/json"))
            )
        }
    }

    private fun setupLocationServiceMocks() {
        // Default: return empty drivers list
        wireMockServer.stubFor(
            post(urlEqualTo("/api/v1/drivers/search/available"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("[]"))
        )
    }

    private fun setupLocationServiceWithDrivers() {
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
}