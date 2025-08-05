package com.ddakta.matching.integration

import com.ddakta.matching.config.TestApplication
import com.ddakta.matching.config.TestFeignConfiguration
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.cloud.openfeign.FeignAutoConfiguration
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.time.Duration

@SpringBootTest(
    classes = [TestApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "spring.kafka.producer.retries=3",
        "logging.level.com.ddakta.matching=DEBUG",
        "logging.level.org.testcontainers=INFO",
        "spring.cloud.openfeign.enabled=false",
        "spring.cloud.openfeign.autoconfiguration.jackson.enabled=false"
    ]
)
@ActiveProfiles("test", "integration")
@Import(TestFeignConfiguration::class)
@ImportAutoConfiguration(exclude = [FeignAutoConfiguration::class])
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
abstract class BaseIntegrationTest {

    @Autowired
    protected lateinit var restTemplate: TestRestTemplate
    
    @Autowired
    protected lateinit var objectMapper: ObjectMapper

    @LocalServerPort
    protected var port: Int = 0

    protected val baseUrl: String
        get() = "http://localhost:$port"

    companion object {
        @Container
        @JvmStatic
        val postgresContainer: PostgreSQLContainer<*> = PostgreSQLContainer(
            DockerImageName.parse("postgres:15-alpine")
        ).apply {
            withDatabaseName("ddakta_matching_test")
            withUsername("ddakta_test")
            withPassword("ddakta_test123")
            withInitScript("init-test-db.sql")
            withStartupTimeout(Duration.ofMinutes(2))
            withConnectTimeoutSeconds(120)
        }

        @Container
        @JvmStatic
        val redisContainer: GenericContainer<*> = GenericContainer(
            DockerImageName.parse("redis:7-alpine")
        ).apply {
            withExposedPorts(6379)
            withCommand("redis-server", "--appendonly", "yes", "--maxmemory", "256mb", "--maxmemory-policy", "allkeys-lru")
            waitingFor(Wait.forLogMessage(".*Ready to accept connections.*", 1))
            withStartupTimeout(Duration.ofMinutes(2))
        }

        @Container
        @JvmStatic
        val kafkaContainer: KafkaContainer = KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.5.0")
        ).apply {
            withStartupTimeout(Duration.ofMinutes(2))
            withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "true")
            withEnv("KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR", "1")
            withEnv("KAFKA_TRANSACTION_STATE_LOG_MIN_ISR", "1")
            withEnv("KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR", "1")
            withEnv("KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS", "0")
        }

        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            // Ensure containers are started before accessing their properties
            if (!postgresContainer.isRunning) {
                postgresContainer.start()
            }
            if (!redisContainer.isRunning) {
                redisContainer.start()
            }
            if (!kafkaContainer.isRunning) {
                kafkaContainer.start()
            }

            // PostgreSQL Configuration
            registry.add("spring.datasource.url") { 
                "jdbc:postgresql://${postgresContainer.host}:${postgresContainer.getMappedPort(5432)}/${postgresContainer.databaseName}"
            }
            registry.add("spring.datasource.username") { postgresContainer.username }
            registry.add("spring.datasource.password") { postgresContainer.password }
            registry.add("spring.datasource.driver-class-name") { "org.postgresql.Driver" }

            // Redis Configuration
            registry.add("spring.redis.host") { redisContainer.host }
            registry.add("spring.redis.port") { redisContainer.getMappedPort(6379) }
            registry.add("spring.redis.timeout") { "2000ms" }

            // Kafka Configuration
            registry.add("spring.kafka.bootstrap-servers") { kafkaContainer.bootstrapServers }
            registry.add("spring.kafka.consumer.group-id") { "matching-service-integration-test" }

            // Application-specific test configuration
            registry.add("matching.batch.size") { "10" }
            registry.add("matching.batch.interval") { "100" }
            registry.add("matching.driver-call.timeout") { "5" }
            registry.add("matching.driver-call.max-drivers") { "3" }
            registry.add("matching.search.radius-km") { "2.0" }
            registry.add("matching.search.max-drivers") { "10" }

            // External service URLs (will be mocked in tests)
            registry.add("ddakta.location-service.url") { "http://localhost:8090" }
            registry.add("ddakta.user-service.url") { "http://localhost:8090" }
        }

        @JvmStatic
        @BeforeAll
        fun beforeAll() {
            // Ensure containers are started
            postgresContainer.start()
            redisContainer.start()
            kafkaContainer.start()
        }
    }

    protected fun waitForContainersToBeReady() {
        // Wait for PostgreSQL
        postgresContainer.waitingFor(Wait.forListeningPort())
        
        // Wait for Redis
        redisContainer.waitingFor(Wait.forLogMessage(".*Ready to accept connections.*", 1))
        
        // Wait for Kafka
        kafkaContainer.waitingFor(Wait.forListeningPort())
    }

    protected fun executeInTransaction(action: () -> Unit) {
        try {
            action()
        } catch (e: Exception) {
            throw e
        }
    }

    /**
     * Helper method to create test data URLs
     */
    protected fun apiUrl(path: String): String = "$baseUrl/api/v1$path"

    /**
     * Helper method to wait for async operations
     */
    protected fun waitForAsyncOperation(
        timeoutMs: Long = 5000,
        intervalMs: Long = 100,
        condition: () -> Boolean
    ) {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            if (condition()) {
                return
            }
            Thread.sleep(intervalMs)
        }
        throw AssertionError("Condition not met within timeout of ${timeoutMs}ms")
    }

    /**
     * Helper method to clean up test data between tests
     */
    protected fun cleanupTestData() {
        // This will be called by individual tests if needed
        // Due to @Transactional, most test data will be automatically rolled back
    }
}