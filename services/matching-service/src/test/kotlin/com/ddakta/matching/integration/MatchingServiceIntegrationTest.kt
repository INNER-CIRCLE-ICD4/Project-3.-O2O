package com.ddakta.matching.integration

import com.ddakta.matching.dto.request.RideRequestDto
import com.ddakta.matching.dto.internal.LocationInfo
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.client.WireMock.*
import org.junit.jupiter.api.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.*
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.*
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.time.Duration
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EmbeddedKafka(
    partitions = 1,
    brokerProperties = [
        "listeners=PLAINTEXT://localhost:9092",
        "port=9092"
    ]
)
class MatchingServiceIntegrationTest {

    @Autowired
    lateinit var restTemplate: TestRestTemplate

    @Autowired
    lateinit var objectMapper: ObjectMapper

    companion object {
        // PostgreSQL with H3 extension
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer<Nothing>(DockerImageName.parse("postgres:15-alpine")).apply {
            withDatabaseName("ddakta_matching_test")
            withUsername("ddakta_test")
            withPassword("ddakta_test123")
            withInitScript("init-scripts/01-init-database.sql")
            waitingFor(Wait.forListeningPort())
            withStartupTimeout(Duration.ofMinutes(2))
        }

        // Redis
        @Container
        @JvmStatic
        val redis = GenericContainer<Nothing>(DockerImageName.parse("redis:7-alpine")).apply {
            withExposedPorts(6379)
            withCommand("redis-server", "--appendonly", "yes")
            waitingFor(Wait.forListeningPort())
        }

        // WireMock for external services
        @Container
        @JvmStatic
        val wireMock = GenericContainer<Nothing>(DockerImageName.parse("wiremock/wiremock:3.3.1")).apply {
            withExposedPorts(8080)
            withClasspathResourceMapping(
                "wiremock-stubs",
                "/home/wiremock",
                BindMode.READ_ONLY
            )
            withCommand("--verbose", "--global-response-templating")
            waitingFor(Wait.forListeningPort())
        }

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            // Database
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)

            // Redis
            registry.add("spring.redis.host", redis::getHost)
            registry.add("spring.redis.port", redis::getFirstMappedPort)

            // External services (WireMock)
            registry.add("ddakta.location-service.url") { 
                "http://${wireMock.host}:${wireMock.firstMappedPort}" 
            }
            registry.add("ddakta.user-service.url") { 
                "http://${wireMock.host}:${wireMock.firstMappedPort}" 
            }
        }
    }

    @BeforeAll
    fun setup() {
        // Setup WireMock stubs
        setupLocationServiceStubs()
        setupUserServiceStubs()
    }

    @Test
    @DisplayName("전체 운행 시나리오 E2E 테스트")
    fun `should complete full ride lifecycle from request to completion`() {
        // Given - 승객이 강남역에서 삼성역으로 가는 운행을 요청
        val rideRequest = RideRequestDto(
            passengerId = UUID.randomUUID(),
            pickupLocation = LocationInfo(
                latitude = 37.4979,
                longitude = 127.0276,
                h3Index = "882a100d9ffffff",
                address = "강남역 2번출구"
            ),
            dropoffLocation = LocationInfo(
                latitude = 37.5172,
                longitude = 127.0473,
                h3Index = "882a100c1ffffff",
                address = "삼성역 5번출구"
            )
        )

        // When - 운행 요청
        val createResponse = restTemplate.postForEntity(
            "/api/v1/rides",
            rideRequest,
            Map::class.java
        )

        // Then - 운행이 생성됨
        assertEquals(HttpStatus.CREATED, createResponse.statusCode)
        val rideId = createResponse.body?.get("rideId") as String
        assertNotNull(rideId)

        // When - 매칭 상태 확인 (폴링)
        var matched = false
        var attempts = 0
        val maxAttempts = 30 // 30초 타임아웃

        while (!matched && attempts < maxAttempts) {
            Thread.sleep(1000) // 1초 대기

            val statusResponse = restTemplate.getForEntity(
                "/api/v1/rides/$rideId",
                Map::class.java
            )

            if (statusResponse.statusCode == HttpStatus.OK) {
                val status = statusResponse.body?.get("status") as String
                if (status == "MATCHED" || status == "DRIVER_ASSIGNED") {
                    matched = true
                    assertNotNull(statusResponse.body?.get("driverId"))
                }
            }
            attempts++
        }

        assertTrue(matched, "Ride should be matched within 30 seconds")
    }

    @Test
    @DisplayName("동시 다발적 운행 요청 처리 테스트")
    fun `should handle concurrent ride requests efficiently`() {
        val concurrentRequests = 10
        val latch = CountDownLatch(concurrentRequests)
        val results = mutableListOf<ResponseEntity<Map<*, *>>>()

        // Given - 여러 승객이 동시에 운행 요청
        val threads = (1..concurrentRequests).map { index ->
            Thread {
                val request = RideRequestDto(
                    passengerId = UUID.randomUUID(),
                    pickupLocation = LocationInfo(
                        latitude = 37.4979 + (index * 0.001),
                        longitude = 127.0276 + (index * 0.001),
                        h3Index = "882a100d9ffffff",
                        address = "Test location $index"
                    ),
                    dropoffLocation = LocationInfo(
                        latitude = 37.5172,
                        longitude = 127.0473,
                        h3Index = "882a100c1ffffff",
                        address = "삼성역"
                    )
                )

                val response = restTemplate.postForEntity(
                    "/api/v1/rides",
                    request,
                    Map::class.java
                )

                synchronized(results) {
                    results.add(response)
                }
                latch.countDown()
            }
        }

        // When - 동시 실행
        threads.forEach { it.start() }
        assertTrue(latch.await(10, TimeUnit.SECONDS))

        // Then - 모든 요청이 성공적으로 처리됨
        assertEquals(concurrentRequests, results.size)
        results.forEach { response ->
            assertEquals(HttpStatus.CREATED, response.statusCode)
            assertNotNull(response.body?.get("rideId"))
        }
    }

    @Test
    @DisplayName("Redis 분산 락 동작 테스트")
    fun `should prevent duplicate ride requests using distributed lock`() {
        val passengerId = UUID.randomUUID()
        val request = RideRequestDto(
            passengerId = passengerId,
            pickupLocation = LocationInfo(
                latitude = 37.4979,
                longitude = 127.0276,
                h3Index = "882a100d9ffffff",
                address = "강남역"
            ),
            dropoffLocation = LocationInfo(
                latitude = 37.5172,
                longitude = 127.0473,
                h3Index = "882a100c1ffffff",
                address = "삼성역"
            )
        )

        // When - 같은 승객이 연속으로 운행 요청
        val response1 = restTemplate.postForEntity("/api/v1/rides", request, Map::class.java)
        val response2 = restTemplate.postForEntity("/api/v1/rides", request, Map::class.java)

        // Then - 첫 번째는 성공, 두 번째는 실패
        assertEquals(HttpStatus.CREATED, response1.statusCode)
        assertEquals(HttpStatus.CONFLICT, response2.statusCode)
    }

    @Test
    @DisplayName("서지 가격 적용 테스트")
    fun `should apply surge pricing during high demand`() {
        // Given - 특정 지역에 많은 요청 생성 (서지 트리거)
        val h3Cell = "882a100d9ffffff"
        val surgeRequests = (1..20).map {
            Thread {
                val request = RideRequestDto(
                    passengerId = UUID.randomUUID(),
                    pickupLocation = LocationInfo(
                        latitude = 37.4979,
                        longitude = 127.0276,
                        h3Index = h3Cell,
                        address = "High demand area"
                    ),
                    dropoffLocation = LocationInfo(
                        latitude = 37.5172,
                        longitude = 127.0473,
                        h3Index = "882a100c1ffffff",
                        address = "Destination"
                    )
                )
                restTemplate.postForEntity("/api/v1/rides", request, Map::class.java)
            }
        }

        surgeRequests.forEach { it.start() }
        surgeRequests.forEach { it.join() }

        // Wait for surge calculation
        Thread.sleep(2000)

        // When - 새로운 운행 요청
        val newRequest = RideRequestDto(
            passengerId = UUID.randomUUID(),
            pickupLocation = LocationInfo(
                latitude = 37.4979,
                longitude = 127.0276,
                h3Index = h3Cell,
                address = "Surge area"
            ),
            dropoffLocation = LocationInfo(
                latitude = 37.5172,
                longitude = 127.0473,
                h3Index = "882a100c1ffffff",
                address = "Destination"
            )
        )

        val response = restTemplate.postForEntity("/api/v1/rides", newRequest, Map::class.java)
        val rideId = response.body?.get("rideId") as String

        // Then - 서지 가격이 적용됨
        val rideDetails = restTemplate.getForEntity("/api/v1/rides/$rideId", Map::class.java)
        val surgeMultiplier = rideDetails.body?.get("surgeMultiplier") as Double
        assertTrue(surgeMultiplier > 1.0, "Surge multiplier should be greater than 1.0")
    }

    private fun setupLocationServiceStubs() {
        val wireMockUrl = "http://${wireMock.host}:${wireMock.firstMappedPort}"
        configureFor(wireMock.host, wireMock.firstMappedPort)

        // Available drivers stub
        stubFor(
            get(urlPathMatching("/api/v1/drivers/available"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                            {
                                "drivers": [
                                    {
                                        "id": "${UUID.randomUUID()}",
                                        "location": {
                                            "latitude": 37.4985,
                                            "longitude": 127.0282,
                                            "h3Index": "882a100d9ffffff",
                                            "lastUpdated": "${System.currentTimeMillis()}"
                                        },
                                        "status": "AVAILABLE",
                                        "rating": 4.8,
                                        "acceptanceRate": 0.92,
                                        "completionRate": 0.98,
                                        "vehicleType": "STANDARD"
                                    }
                                ],
                                "totalCount": 1,
                                "timestamp": "${System.currentTimeMillis()}"
                            }
                        """.trimIndent())
                )
        )
    }

    private fun setupUserServiceStubs() {
        // User info stubs
        stubFor(
            get(urlPathMatching("/api/v1/users/passengers/.*"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                            {
                                "id": "${UUID.randomUUID()}",
                                "name": "Test Passenger",
                                "email": "test@example.com",
                                "phone": "+821012345678",
                                "rating": 4.7,
                                "totalRides": 100,
                                "memberSince": "2023-01-01",
                                "preferredPaymentMethod": "CARD",
                                "isVip": false
                            }
                        """.trimIndent())
                )
        )
    }
}