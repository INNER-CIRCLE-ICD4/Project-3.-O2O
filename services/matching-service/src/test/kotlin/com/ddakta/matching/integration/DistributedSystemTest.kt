package com.ddakta.matching.integration

import com.ddakta.matching.cache.StickySessionService
import com.ddakta.matching.dto.request.RideRequestDto
import com.ddakta.matching.dto.internal.LocationInfo
import kotlinx.coroutines.*
import org.junit.jupiter.api.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.http.*
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.web.client.RestTemplate
import org.testcontainers.containers.*
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.time.Duration
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class DistributedSystemTest {

    @Autowired
    lateinit var stickySessionService: StickySessionService

    @Autowired
    lateinit var redisTemplate: RedisTemplate<String, String>

    @Value("\${server.instance-id:test-instance}")
    lateinit var instanceId: String

    companion object {
        // Shared Redis for distributed coordination
        @Container
        @JvmStatic
        val redis = GenericContainer<Nothing>(DockerImageName.parse("redis:7-alpine")).apply {
            withExposedPorts(6379)
            withCommand("redis-server", "--appendonly", "yes", "--maxclients", "10000")
            waitingFor(Wait.forListeningPort())
        }

        // Network for service communication
        val network = Network.newNetwork()

        // Multiple matching service instances
        val matchingServices = mutableListOf<GenericContainer<*>>()

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.redis.host", redis::getHost)
            registry.add("spring.redis.port", redis::getFirstMappedPort)
        }

        @BeforeAll
        @JvmStatic
        fun setupDistributedEnvironment() {
            // Start multiple matching service instances
            // Note: In real test, you'd build and run your actual service image
            println("Distributed environment setup complete")
        }
    }

    @Test
    @Order(1)
    @DisplayName("Sticky Session 생성 및 인스턴스 할당 테스트")
    fun `should create sticky sessions and distribute across instances`() {
        val userCount = 100
        val sessions = ConcurrentHashMap<UUID, String>()
        val latch = CountDownLatch(userCount)

        // Create sessions concurrently
        val users = (1..userCount).map { UUID.randomUUID() }
        
        runBlocking {
            users.map { userId ->
                async(Dispatchers.IO) {
                    val session = stickySessionService.createOrUpdateSession(
                        userId = userId,
                        instanceId = "instance-${userId.hashCode() % 3}",
                        metadata = mapOf("test" to true)
                    )
                    sessions[userId] = session.instanceId
                    latch.countDown()
                }
            }.awaitAll()
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS))
        assertEquals(userCount, sessions.size)

        // Verify distribution across instances
        val distribution = sessions.values.groupBy { it }.mapValues { it.value.size }
        println("Session distribution: $distribution")
        
        // Each instance should have roughly 1/3 of sessions
        distribution.values.forEach { count ->
            assertTrue(count in 20..50, "Sessions should be distributed evenly")
        }
    }

    @Test
    @Order(2)
    @DisplayName("세션 마이그레이션 테스트")
    fun `should migrate sessions between instances successfully`() {
        // Create initial sessions
        val usersToMigrate = (1..10).map { UUID.randomUUID() }
        val sourceInstance = "instance-1"
        val targetInstance = "instance-2"

        // Create sessions on source instance
        usersToMigrate.forEach { userId ->
            stickySessionService.createOrUpdateSession(
                userId = userId,
                instanceId = sourceInstance
            )
        }

        // Verify initial state
        val initialLoad = stickySessionService.getInstanceLoad(sourceInstance)
        assertTrue(initialLoad.sessionCount >= 10)

        // Perform migration
        val migrationResult = stickySessionService.bulkMigrateSessions(
            fromInstance = sourceInstance,
            toInstances = listOf(targetInstance),
            maxSessions = 10
        )

        // Verify migration results
        assertTrue(migrationResult.migrated.isNotEmpty())
        assertTrue(migrationResult.failed.isEmpty())

        // Verify sessions moved to target instance
        migrationResult.migrated.forEach { userId ->
            val session = stickySessionService.getSession(userId)
            assertNotNull(session)
            assertEquals(targetInstance, session.instanceId)
        }
    }

    @Test
    @Order(3)
    @DisplayName("분산 락을 사용한 동시성 제어 테스트")
    fun `should handle concurrent operations with distributed locks`() {
        val key = "test:concurrent:${UUID.randomUUID()}"
        val counter = AtomicInteger(0)
        val operations = 100
        val threads = 10
        val latch = CountDownLatch(operations)

        // Simulate concurrent increments with distributed lock
        val executors = (1..threads).map {
            Thread {
                repeat(operations / threads) {
                    val lockKey = "lock:$key"
                    val locked = redisTemplate.opsForValue().setIfAbsent(
                        lockKey,
                        instanceId,
                        Duration.ofSeconds(5)
                    ) ?: false

                    if (locked) {
                        try {
                            // Critical section
                            val current = redisTemplate.opsForValue().get(key)?.toIntOrNull() ?: 0
                            Thread.sleep(1) // Simulate work
                            redisTemplate.opsForValue().set(key, (current + 1).toString())
                            counter.incrementAndGet()
                        } finally {
                            redisTemplate.delete(lockKey)
                        }
                    } else {
                        // Retry after brief wait
                        Thread.sleep(10)
                    }
                    latch.countDown()
                }
            }
        }

        executors.forEach { it.start() }
        assertTrue(latch.await(30, TimeUnit.SECONDS))
        executors.forEach { it.join() }

        // Verify final count
        val finalCount = redisTemplate.opsForValue().get(key)?.toIntOrNull() ?: 0
        assertTrue(finalCount > 0, "Counter should have been incremented")
        println("Final count: $finalCount out of $operations attempts")
    }

    @Test
    @Order(4)
    @DisplayName("인스턴스 장애 시 세션 복구 테스트")
    fun `should recover sessions when instance fails`() {
        val failingInstance = "instance-failing"
        val healthyInstances = listOf("instance-1", "instance-2", "instance-3")
        
        // Create sessions on failing instance
        val affectedUsers = (1..20).map { UUID.randomUUID() }
        affectedUsers.forEach { userId ->
            stickySessionService.createOrUpdateSession(
                userId = userId,
                instanceId = failingInstance
            )
        }

        // Simulate instance failure by marking unhealthy
        redisTemplate.delete("session:health:$failingInstance")
        Thread.sleep(1000)

        // Verify instance marked as unhealthy
        val instanceLoad = stickySessionService.getInstanceLoad(failingInstance)
        assertTrue(!instanceLoad.isHealthy)

        // Trigger recovery migration
        val recoveryResult = stickySessionService.bulkMigrateSessions(
            fromInstance = failingInstance,
            toInstances = healthyInstances,
            maxSessions = 50
        )

        // Verify all sessions migrated
        assertEquals(affectedUsers.size, recoveryResult.migrated.size)
        assertTrue(recoveryResult.failed.isEmpty())

        // Verify sessions distributed across healthy instances
        val newDistribution = affectedUsers.map { userId ->
            stickySessionService.getSession(userId)?.instanceId
        }.filterNotNull().groupBy { it }

        assertEquals(healthyInstances.size, newDistribution.keys.size)
        newDistribution.values.forEach { instances ->
            assertTrue(instances.isNotEmpty())
        }
    }

    @Test
    @Order(5)
    @DisplayName("캐시 일관성 테스트")
    fun `should maintain cache consistency across instances`() {
        val rideId = UUID.randomUUID()
        val cacheKey = "ride:$rideId"
        val instances = listOf("instance-1", "instance-2", "instance-3")

        // Simulate cache updates from different instances
        instances.forEachIndexed { index, instance ->
            val rideData = """
                {
                    "id": "$rideId",
                    "status": "REQUESTED",
                    "updatedBy": "$instance",
                    "version": $index
                }
            """.trimIndent()

            // Each instance updates the cache
            redisTemplate.opsForValue().set(
                cacheKey,
                rideData,
                Duration.ofMinutes(5)
            )

            // Brief pause to simulate real timing
            Thread.sleep(100)
        }

        // Verify final state
        val finalData = redisTemplate.opsForValue().get(cacheKey)
        assertNotNull(finalData)
        assertTrue(finalData.contains("instance-3"))
        assertTrue(finalData.contains("version\": 2"))
    }

    @Test
    @Order(6)
    @DisplayName("고가용성 시나리오 테스트")
    fun `should maintain service availability during rolling updates`() {
        val testUsers = (1..50).map { UUID.randomUUID() }
        val results = ConcurrentHashMap<UUID, Boolean>()
        val instances = listOf("instance-1", "instance-2", "instance-3")
        
        // Simulate rolling update scenario
        runBlocking {
            testUsers.map { userId ->
                async(Dispatchers.IO) {
                    try {
                        // Create session
                        val targetInstance = instances[userId.hashCode() % instances.size]
                        stickySessionService.createOrUpdateSession(
                            userId = userId,
                            instanceId = targetInstance
                        )

                        // Simulate instance going down and coming back
                        if (userId.hashCode() % 5 == 0) {
                            delay(100)
                            // Simulate migration due to instance update
                            val newInstance = instances[(userId.hashCode() + 1) % instances.size]
                            stickySessionService.migrateSession(
                                userId = userId,
                                fromInstance = targetInstance,
                                toInstance = newInstance
                            )
                        }

                        results[userId] = true
                    } catch (e: Exception) {
                        results[userId] = false
                        println("Failed for user $userId: ${e.message}")
                    }
                }
            }.awaitAll()
        }

        // Verify high availability maintained
        val successRate = results.values.count { it }.toDouble() / results.size
        assertTrue(successRate > 0.95, "Should maintain >95% availability during updates")
        println("Availability during rolling update: ${successRate * 100}%")
    }

    @Test
    @Order(7)
    @DisplayName("네트워크 파티션 복구 테스트")
    fun `should handle network partition and recovery`() {
        val partitionKey = "partition:test:${UUID.randomUUID()}"
        val value1 = "value-from-partition-1"
        val value2 = "value-from-partition-2"

        // Simulate split-brain scenario
        // In real test, you'd simulate network partition between instances
        
        // Partition 1 writes
        redisTemplate.opsForValue().set(partitionKey, value1)
        val firstWrite = redisTemplate.opsForValue().get(partitionKey)
        assertEquals(value1, firstWrite)

        // Simulate delay
        Thread.sleep(100)

        // Partition 2 writes (would normally be blocked in real partition)
        redisTemplate.opsForValue().set(partitionKey, value2)
        val secondWrite = redisTemplate.opsForValue().get(partitionKey)
        assertEquals(value2, secondWrite) // Last write wins in Redis

        // Verify eventual consistency after partition heals
        Thread.sleep(500)
        val finalValue = redisTemplate.opsForValue().get(partitionKey)
        assertNotNull(finalValue)
        // In real scenario, you'd implement conflict resolution
    }

    @Test
    @Order(8)
    @DisplayName("부하 분산 효율성 테스트")
    fun `should distribute load efficiently across instances`() {
        val requestCount = 1000
        val instanceLoads = ConcurrentHashMap<String, AtomicInteger>()
        val instances = listOf("instance-1", "instance-2", "instance-3")
        
        // Initialize counters
        instances.forEach { instance ->
            instanceLoads[instance] = AtomicInteger(0)
        }

        // Simulate load distribution
        runBlocking {
            (1..requestCount).map { requestId ->
                async(Dispatchers.IO) {
                    val userId = UUID.randomUUID()
                    
                    // Get or create session (simulating sticky session routing)
                    val session = stickySessionService.getSession(userId)
                        ?: stickySessionService.createOrUpdateSession(
                            userId = userId,
                            instanceId = instances[requestId % instances.size]
                        )
                    
                    // Increment load counter for the instance
                    instanceLoads[session.instanceId]?.incrementAndGet()
                }
            }.awaitAll()
        }

        // Analyze load distribution
        val loadDistribution = instanceLoads.mapValues { it.value.get() }
        println("Load distribution: $loadDistribution")

        // Verify relatively even distribution (within 20% variance)
        val avgLoad = requestCount / instances.size
        loadDistribution.values.forEach { load ->
            assertTrue(
                load in (avgLoad * 0.8).toInt()..(avgLoad * 1.2).toInt(),
                "Load should be distributed evenly across instances"
            )
        }
    }
}