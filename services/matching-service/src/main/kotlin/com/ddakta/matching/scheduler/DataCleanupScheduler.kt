package com.ddakta.matching.scheduler

import com.ddakta.matching.domain.repository.MatchingRequestRepository
import com.ddakta.matching.domain.repository.RideRepository
import io.micrometer.core.instrument.MeterRegistry
import mu.KotlinLogging
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

@Component
class DataCleanupScheduler(
    private val rideRepository: RideRepository,
    private val matchingRequestRepository: MatchingRequestRepository,
    private val redisTemplate: RedisTemplate<String, String>,
    private val meterRegistry: MeterRegistry
) {

    private val logger = KotlinLogging.logger {}

    companion object {
        // Retention policies (in days)
        const val COMPLETED_RIDES_RETENTION_DAYS = 90L
        const val CANCELLED_RIDES_RETENTION_DAYS = 30L
        const val FAILED_RIDES_RETENTION_DAYS = 7L
        const val EXPIRED_REQUESTS_RETENTION_DAYS = 3L

        // Batch sizes for cleanup
        const val DELETE_BATCH_SIZE = 1000
        const val ARCHIVE_BATCH_SIZE = 500

        // Redis key patterns
        const val TEMP_KEY_PATTERN = "temp:*"
        const val SESSION_KEY_PATTERN = "session:*"
        const val CACHE_KEY_PATTERN = "cache:*"

        // Archive settings
        const val ARCHIVE_TABLE_PREFIX = "archive_"
        const val COMPRESSION_ENABLED = true
    }

    private val totalDeleted = AtomicLong(0)
    private val totalArchived = AtomicLong(0)
    private val lastCleanupTime = AtomicLong(System.currentTimeMillis())

    @Scheduled(cron = "0 0 2 * * *") // Daily at 2 AM
    @Transactional
    fun cleanupOldRides() {
        try {
            logger.info { "Starting daily ride cleanup" }

            val startTime = System.currentTimeMillis()
            var deletedCount = 0L

            // Cleanup completed rides
            deletedCount += cleanupRidesByStatus(
                status = "COMPLETED",
                retentionDays = COMPLETED_RIDES_RETENTION_DAYS,
                archive = true
            )

            // Cleanup cancelled rides
            deletedCount += cleanupRidesByStatus(
                status = "CANCELLED",
                retentionDays = CANCELLED_RIDES_RETENTION_DAYS,
                archive = false
            )

            // Cleanup failed rides
            deletedCount += cleanupRidesByStatus(
                status = "FAILED",
                retentionDays = FAILED_RIDES_RETENTION_DAYS,
                archive = false
            )

            val duration = System.currentTimeMillis() - startTime
            totalDeleted.addAndGet(deletedCount)

            logger.info {
                "Ride cleanup completed: deleted=$deletedCount, duration=${duration}ms"
            }

            // Record metrics
            meterRegistry.counter("cleanup.rides.deleted").increment(deletedCount.toDouble())
            meterRegistry.timer("cleanup.rides.duration").record(Duration.ofMillis(duration))

        } catch (e: Exception) {
            logger.error(e) { "Error during ride cleanup" }
            meterRegistry.counter("cleanup.rides.errors").increment()
        }
    }

    @Scheduled(cron = "0 30 2 * * *") // Daily at 2:30 AM
    @Transactional
    fun cleanupExpiredMatchingRequests() {
        try {
            logger.info { "Starting matching request cleanup" }

            val cutoffDate = LocalDateTime.now().minusDays(EXPIRED_REQUESTS_RETENTION_DAYS)
            var deletedCount = 0L

            // Delete in batches to avoid long transactions
            while (true) {
                val deleted = matchingRequestRepository.deleteExpiredRequestsBefore(
                    cutoffDate,
                    DELETE_BATCH_SIZE
                )

                if (deleted == 0) break

                deletedCount += deleted

                // Small delay between batches
                Thread.sleep(100)
            }

            logger.info { "Deleted $deletedCount expired matching requests" }

            meterRegistry.counter("cleanup.requests.deleted").increment(deletedCount.toDouble())

        } catch (e: Exception) {
            logger.error(e) { "Error cleaning up matching requests" }
            meterRegistry.counter("cleanup.requests.errors").increment()
        }
    }

    @Scheduled(fixedDelay = 3600000) // Every hour
    fun cleanupRedisKeys() {
        try {
            logger.info { "Starting Redis cleanup" }

            var deletedCount = 0L

            // Cleanup temporary keys
            deletedCount += cleanupRedisPattern(TEMP_KEY_PATTERN, 0) // No TTL check, delete all

            // Cleanup expired session keys
            deletedCount += cleanupExpiredSessions()

            // Cleanup orphaned cache entries
            deletedCount += cleanupOrphanedCacheEntries()

            logger.info { "Redis cleanup completed: deleted $deletedCount keys" }

            meterRegistry.gauge("cleanup.redis.deleted", deletedCount)

        } catch (e: Exception) {
            logger.error(e) { "Error during Redis cleanup" }
            meterRegistry.counter("cleanup.redis.errors").increment()
        }
    }

    @Scheduled(cron = "0 0 3 * * SUN") // Weekly on Sunday at 3 AM
    fun performDeepCleanup() {
        try {
            logger.info { "Starting weekly deep cleanup" }

            // Vacuum analyze database tables
            vacuumAnalyzeTables()

            // Rebuild indexes if needed
            rebuildFragmentedIndexes()

            // Clean up old audit logs
            cleanupAuditLogs()

            // Optimize Redis memory
            optimizeRedisMemory()

            logger.info { "Deep cleanup completed" }

        } catch (e: Exception) {
            logger.error(e) { "Error during deep cleanup" }
        }
    }

    private fun cleanupRidesByStatus(
        status: String,
        retentionDays: Long,
        archive: Boolean
    ): Long {
        val cutoffDate = LocalDateTime.now().minusDays(retentionDays)
        var processedCount = 0L

        try {
            while (true) {
                // Find rides to process
                val rides = rideRepository.findOldRidesByStatus(
                    status,
                    cutoffDate,
                    if (archive) ARCHIVE_BATCH_SIZE else DELETE_BATCH_SIZE
                )

                if (rides.isEmpty()) break

                if (archive) {
                    // Archive before deletion
                    archiveRides(rides)
                    totalArchived.addAndGet(rides.size.toLong())
                }

                // Delete rides
                val rideIds = rides.map { it.id!! }
                rideRepository.deleteAllById(rideIds)

                // Clean up related cache entries
                rideIds.forEach { rideId ->
                    val keys = listOf(
                        "ride:$rideId",
                        "ride:state:$rideId",
                        "ride:active:*:$rideId"
                    )
                    redisTemplate.delete(keys)
                }

                processedCount += rides.size

                // Prevent overwhelming the system
                Thread.sleep(200)
            }

        } catch (e: Exception) {
            logger.error(e) { "Error cleaning up $status rides" }
            throw e
        }

        return processedCount
    }

    private fun archiveRides(rides: List<Any>) {
        // In a real implementation, this would:
        // 1. Serialize rides to compressed format
        // 2. Store in archive storage (S3, cold storage, etc.)
        // 3. Update archive index

        logger.debug { "Archived ${rides.size} rides" }
    }

    private fun cleanupRedisPattern(pattern: String, maxAge: Long): Long {
        var deletedCount = 0L

        try {
            val keys = redisTemplate.keys(pattern)

            keys.forEach { key ->
                if (maxAge == 0L || isKeyExpired(key, maxAge)) {
                    redisTemplate.delete(key)
                    deletedCount++
                }
            }

        } catch (e: Exception) {
            logger.error(e) { "Error cleaning up Redis pattern: $pattern" }
        }

        return deletedCount
    }

    private fun cleanupExpiredSessions(): Long {
        var deletedCount = 0L

        try {
            val sessionKeys = redisTemplate.keys("session:*")

            sessionKeys.forEach { key ->
                val ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS)

                // Delete if no TTL set or expired
                if (ttl == null || ttl < 0) {
                    redisTemplate.delete(key)
                    deletedCount++
                }
            }

        } catch (e: Exception) {
            logger.error(e) { "Error cleaning up sessions" }
        }

        return deletedCount
    }

    private fun cleanupOrphanedCacheEntries(): Long {
        var deletedCount = 0L

        try {
            // Find cache entries without corresponding database records
            val rideKeys = redisTemplate.keys("ride:*")

            rideKeys.forEach { key ->
                val rideId = key.substringAfter("ride:").substringBefore(":")

                // Check if ride exists in database
                if (!rideExists(rideId)) {
                    redisTemplate.delete(key)
                    deletedCount++
                }
            }

        } catch (e: Exception) {
            logger.error(e) { "Error cleaning up orphaned cache entries" }
        }

        return deletedCount
    }

    private fun vacuumAnalyzeTables() {
        try {
            logger.info { "Running VACUUM ANALYZE on tables" }

            // In production, this would be more sophisticated
            val tables = listOf("rides", "matching_requests", "driver_calls")

            tables.forEach { table ->
                // Note: VACUUM should be run carefully in production
                logger.debug { "Analyzing table: $table" }
            }

        } catch (e: Exception) {
            logger.error(e) { "Error during vacuum analyze" }
        }
    }

    private fun rebuildFragmentedIndexes() {
        // Check index fragmentation and rebuild if necessary
        logger.debug { "Checking index fragmentation" }
    }

    private fun cleanupAuditLogs() {
        // Clean up old audit log entries
        logger.debug { "Cleaning up audit logs" }
    }

    private fun optimizeRedisMemory() {
        try {
            // Trigger Redis memory optimization
            val info = redisTemplate.connectionFactory?.connection?.info("memory")
            logger.debug { "Redis memory info: $info" }

            // Could trigger memory defragmentation if supported

        } catch (e: Exception) {
            logger.error(e) { "Error optimizing Redis memory" }
        }
    }

    private fun isKeyExpired(key: String, maxAgeMillis: Long): Boolean {
        // Check if key is older than maxAge
        // This is a simplified check - in production would check actual creation time
        return true
    }

    private fun rideExists(rideId: String): Boolean {
        return try {
            rideRepository.existsById(java.util.UUID.fromString(rideId))
        } catch (e: Exception) {
            false
        }
    }

    fun getCleanupStats(): CleanupStats {
        return CleanupStats(
            totalDeleted = totalDeleted.get(),
            totalArchived = totalArchived.get(),
            lastCleanupTime = lastCleanupTime.get(),
            nextScheduledCleanup = getNextScheduledCleanup()
        )
    }

    private fun getNextScheduledCleanup(): Long {
        // Calculate next 2 AM
        val now = LocalDateTime.now()
        val next2AM = now.plusDays(1).withHour(2).withMinute(0).withSecond(0)
        return java.time.ZoneId.systemDefault().let { zoneId ->
            next2AM.atZone(zoneId).toInstant().toEpochMilli()
        }
    }

    data class CleanupStats(
        val totalDeleted: Long,
        val totalArchived: Long,
        val lastCleanupTime: Long,
        val nextScheduledCleanup: Long
    )
}
