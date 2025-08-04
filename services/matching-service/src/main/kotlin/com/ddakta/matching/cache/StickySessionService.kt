package com.ddakta.matching.cache

import com.ddakta.matching.dto.internal.SessionInfo
import com.fasterxml.jackson.databind.ObjectMapper
import mu.KotlinLogging
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

@Service
class StickySessionService(
    private val redisTemplate: RedisTemplate<String, String>,
    private val objectMapper: ObjectMapper
) {
    
    private val logger = KotlinLogging.logger {}
    
    companion object {
        // Session keys
        const val SESSION_KEY = "session:user:"
        const val INSTANCE_SESSIONS_KEY = "sessions:instance:"
        const val SESSION_MIGRATION_KEY = "session:migration:"
        const val SESSION_HEALTH_KEY = "session:health:"
        
        // TTL settings
        const val SESSION_TTL_MINUTES = 30L
        const val MIGRATION_TTL_SECONDS = 60L
        const val HEALTH_CHECK_INTERVAL_SECONDS = 10L
        
        // Configuration
        const val MAX_SESSIONS_PER_INSTANCE = 1000
        const val MIGRATION_BATCH_SIZE = 50
        const val SESSION_AFFINITY_THRESHOLD = 0.8 // 80% affinity before migration
    }
    
    // Local session cache for performance
    private val localSessionCache = ConcurrentHashMap<UUID, SessionInfo>()
    
    fun createOrUpdateSession(
        userId: UUID,
        instanceId: String,
        metadata: Map<String, Any> = emptyMap()
    ): SessionInfo {
        try {
            val sessionInfo = SessionInfo(
                userId = userId,
                instanceId = instanceId,
                createdAt = System.currentTimeMillis(),
                lastAccessedAt = System.currentTimeMillis(),
                metadata = metadata,
                migrationInProgress = false
            )
            
            // Save to Redis
            val sessionKey = "$SESSION_KEY$userId"
            val json = objectMapper.writeValueAsString(sessionInfo)
            redisTemplate.opsForValue().set(
                sessionKey,
                json,
                Duration.ofMinutes(SESSION_TTL_MINUTES)
            )
            
            // Track instance sessions
            val instanceKey = "$INSTANCE_SESSIONS_KEY$instanceId"
            redisTemplate.opsForSet().add(instanceKey, userId.toString())
            redisTemplate.expire(instanceKey, Duration.ofHours(1))
            
            // Update local cache
            localSessionCache[userId] = sessionInfo
            
            // Check if migration needed
            checkAndTriggerMigration(instanceId)
            
            logger.debug { "Created/updated session for user $userId on instance $instanceId" }
            
            return sessionInfo
            
        } catch (e: Exception) {
            logger.error(e) { "Failed to create/update session for user $userId" }
            throw e
        }
    }
    
    fun getSession(userId: UUID): SessionInfo? {
        return try {
            // Check local cache first
            localSessionCache[userId]?.let { cached ->
                // Validate TTL
                val age = System.currentTimeMillis() - cached.lastAccessedAt
                if (age < TimeUnit.MINUTES.toMillis(5)) {
                    return updateLastAccessed(cached)
                }
            }
            
            // Fetch from Redis
            val sessionKey = "$SESSION_KEY$userId"
            val json = redisTemplate.opsForValue().get(sessionKey)
            
            if (json != null) {
                val sessionInfo = objectMapper.readValue(json, SessionInfo::class.java)
                
                // Update local cache
                localSessionCache[userId] = sessionInfo
                
                return updateLastAccessed(sessionInfo)
            }
            
            null
            
        } catch (e: Exception) {
            logger.error(e) { "Error getting session for user $userId" }
            null
        }
    }
    
    fun getInstanceForUser(userId: UUID): String? {
        return getSession(userId)?.instanceId
    }
    
    fun migrateSession(
        userId: UUID,
        fromInstance: String,
        toInstance: String
    ): Boolean {
        return try {
            val migrationKey = "$SESSION_MIGRATION_KEY$userId"
            
            // Check if migration already in progress
            if (redisTemplate.hasKey(migrationKey)) {
                logger.warn { "Migration already in progress for user $userId" }
                return false
            }
            
            // Mark migration in progress
            redisTemplate.opsForValue().set(
                migrationKey,
                "$fromInstance->$toInstance",
                Duration.ofSeconds(MIGRATION_TTL_SECONDS)
            )
            
            // Get current session
            val session = getSession(userId)
            if (session == null || session.instanceId != fromInstance) {
                logger.warn { "Session not found or instance mismatch for user $userId" }
                return false
            }
            
            // Create new session with migration flag
            val migratedSession = session.copy(
                instanceId = toInstance,
                migrationInProgress = true,
                metadata = session.metadata + ("migrationTime" to System.currentTimeMillis())
            )
            
            // Atomic update
            val script = """
                local sessionKey = KEYS[1]
                local fromInstanceKey = KEYS[2]
                local toInstanceKey = KEYS[3]
                local userId = ARGV[1]
                local sessionData = ARGV[2]
                local ttl = ARGV[3]
                
                -- Update session
                redis.call('SET', sessionKey, sessionData, 'EX', ttl)
                
                -- Update instance tracking
                redis.call('SREM', fromInstanceKey, userId)
                redis.call('SADD', toInstanceKey, userId)
                
                return 1
            """.trimIndent()
            
            val redisScript = DefaultRedisScript(script, Long::class.java)
            
            redisTemplate.execute(
                redisScript,
                listOf(
                    "$SESSION_KEY$userId",
                    "$INSTANCE_SESSIONS_KEY$fromInstance",
                    "$INSTANCE_SESSIONS_KEY$toInstance"
                ),
                userId.toString(),
                objectMapper.writeValueAsString(migratedSession),
                (SESSION_TTL_MINUTES * 60).toString()
            )
            
            // Update local cache
            localSessionCache[userId] = migratedSession.copy(migrationInProgress = false)
            
            // Clean up migration flag
            redisTemplate.delete(migrationKey)
            
            logger.info { "Successfully migrated session for user $userId from $fromInstance to $toInstance" }
            
            true
            
        } catch (e: Exception) {
            logger.error(e) { "Failed to migrate session for user $userId" }
            false
        }
    }
    
    fun getInstanceLoad(instanceId: String): InstanceLoad {
        return try {
            val instanceKey = "$INSTANCE_SESSIONS_KEY$instanceId"
            val sessionCount = redisTemplate.opsForSet().size(instanceKey) ?: 0
            
            val healthKey = "$SESSION_HEALTH_KEY$instanceId"
            val lastHealthCheck = redisTemplate.opsForValue().get(healthKey)?.toLongOrNull() ?: 0
            
            val isHealthy = (System.currentTimeMillis() - lastHealthCheck) < 
                TimeUnit.SECONDS.toMillis(HEALTH_CHECK_INTERVAL_SECONDS * 3)
            
            InstanceLoad(
                instanceId = instanceId,
                sessionCount = sessionCount.toInt(),
                loadPercentage = (sessionCount.toDouble() / MAX_SESSIONS_PER_INSTANCE) * 100,
                isHealthy = isHealthy,
                lastHealthCheck = lastHealthCheck
            )
            
        } catch (e: Exception) {
            logger.error(e) { "Error getting instance load for $instanceId" }
            InstanceLoad(instanceId, 0, 0.0, false, 0)
        }
    }
    
    fun bulkMigrateSessions(
        fromInstance: String,
        toInstances: List<String>,
        maxSessions: Int = MIGRATION_BATCH_SIZE
    ): MigrationResult {
        val migrated = mutableListOf<UUID>()
        val failed = mutableListOf<UUID>()
        
        try {
            // Get sessions to migrate
            val instanceKey = "$INSTANCE_SESSIONS_KEY$fromInstance"
            val sessionIds = redisTemplate.opsForSet()
                .members(instanceKey)
                ?.take(maxSessions)
                ?.mapNotNull { id -> 
                    try {
                        UUID.fromString(id)
                    } catch (e: Exception) {
                        null
                    }
                } ?: emptyList()
            
            if (sessionIds.isEmpty()) {
                logger.info { "No sessions to migrate from instance $fromInstance" }
                return MigrationResult(emptyList(), emptyList())
            }
            
            // Distribute sessions across target instances
            var targetInstanceIterator = toInstances.iterator()
            
            sessionIds.forEach { userId ->
                if (!targetInstanceIterator.hasNext()) {
                    targetInstanceIterator = toInstances.iterator()
                }
                
                val targetInstance = targetInstanceIterator.next()
                
                if (migrateSession(userId, fromInstance, targetInstance)) {
                    migrated.add(userId)
                } else {
                    failed.add(userId)
                }
            }
            
            logger.info { 
                "Bulk migration complete: ${migrated.size} succeeded, ${failed.size} failed" 
            }
            
        } catch (e: Exception) {
            logger.error(e) { "Error during bulk migration from $fromInstance" }
        }
        
        return MigrationResult(migrated, failed)
    }
    
    fun updateInstanceHealth(instanceId: String) {
        try {
            val healthKey = "$SESSION_HEALTH_KEY$instanceId"
            redisTemplate.opsForValue().set(
                healthKey,
                System.currentTimeMillis().toString(),
                Duration.ofMinutes(1)
            )
        } catch (e: Exception) {
            logger.error(e) { "Failed to update health for instance $instanceId" }
        }
    }
    
    fun cleanupStaleSessions(instanceId: String? = null) {
        try {
            val pattern = if (instanceId != null) {
                "$INSTANCE_SESSIONS_KEY$instanceId"
            } else {
                "$INSTANCE_SESSIONS_KEY*"
            }
            
            val instanceKeys = redisTemplate.keys(pattern)
            var totalCleaned = 0
            
            instanceKeys.forEach { instanceKey ->
                val sessionIds = redisTemplate.opsForSet().members(instanceKey) ?: emptySet()
                
                sessionIds.forEach { sessionId ->
                    val userId = try {
                        UUID.fromString(sessionId)
                    } catch (e: Exception) {
                        null
                    }
                    
                    userId?.let {
                        val session = getSession(it)
                        if (session == null) {
                            // Session doesn't exist, remove from instance tracking
                            redisTemplate.opsForSet().remove(instanceKey, sessionId)
                            localSessionCache.remove(it)
                            totalCleaned++
                        }
                    }
                }
            }
            
            logger.info { "Cleaned up $totalCleaned stale sessions" }
            
        } catch (e: Exception) {
            logger.error(e) { "Error cleaning up stale sessions" }
        }
    }
    
    fun getSessionStats(): SessionStats {
        return try {
            val totalSessions = redisTemplate.keys("$SESSION_KEY*").size
            val instances = mutableMapOf<String, Int>()
            
            redisTemplate.keys("$INSTANCE_SESSIONS_KEY*").forEach { key ->
                val instanceId = key.removePrefix(INSTANCE_SESSIONS_KEY)
                val count = redisTemplate.opsForSet().size(key) ?: 0
                instances[instanceId] = count.toInt()
            }
            
            val avgSessionsPerInstance = if (instances.isNotEmpty()) {
                instances.values.average()
            } else {
                0.0
            }
            
            SessionStats(
                totalSessions = totalSessions,
                instanceCount = instances.size,
                sessionsPerInstance = instances,
                avgSessionsPerInstance = avgSessionsPerInstance,
                localCacheSize = localSessionCache.size
            )
            
        } catch (e: Exception) {
            logger.error(e) { "Error getting session stats" }
            SessionStats(0, 0, emptyMap(), 0.0, 0)
        }
    }
    
    private fun updateLastAccessed(session: SessionInfo): SessionInfo {
        val updated = session.copy(lastAccessedAt = System.currentTimeMillis())
        
        // Update in Redis asynchronously
        val sessionKey = "$SESSION_KEY${session.userId}"
        val json = objectMapper.writeValueAsString(updated)
        redisTemplate.opsForValue().set(
            sessionKey,
            json,
            Duration.ofMinutes(SESSION_TTL_MINUTES)
        )
        
        return updated
    }
    
    private fun checkAndTriggerMigration(instanceId: String) {
        try {
            val load = getInstanceLoad(instanceId)
            
            if (load.loadPercentage > SESSION_AFFINITY_THRESHOLD * 100) {
                logger.warn { 
                    "Instance $instanceId load at ${load.loadPercentage}%, migration may be needed" 
                }
                // In a real implementation, this would trigger an event or notification
                // to the orchestration layer to handle migration
            }
        } catch (e: Exception) {
            logger.error(e) { "Error checking migration trigger for instance $instanceId" }
        }
    }
    
    data class InstanceLoad(
        val instanceId: String,
        val sessionCount: Int,
        val loadPercentage: Double,
        val isHealthy: Boolean,
        val lastHealthCheck: Long
    )
    
    data class MigrationResult(
        val migrated: List<UUID>,
        val failed: List<UUID>
    )
    
    data class SessionStats(
        val totalSessions: Int,
        val instanceCount: Int,
        val sessionsPerInstance: Map<String, Int>,
        val avgSessionsPerInstance: Double,
        val localCacheSize: Int
    )
}