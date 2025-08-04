package com.ddakta.matching.optimization

import com.zaxxer.hikari.HikariDataSource
import com.zaxxer.hikari.HikariPoolMXBean
import io.micrometer.core.instrument.MeterRegistry
import mu.KotlinLogging
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import javax.sql.DataSource
import kotlin.math.max
import kotlin.math.min

@Service
class DatabaseOptimizationService(
    private val dataSource: DataSource,
    private val jdbcTemplate: JdbcTemplate,
    private val redisTemplate: RedisTemplate<String, String>,
    private val meterRegistry: MeterRegistry
) : HealthIndicator {
    
    private val logger = KotlinLogging.logger {}
    
    companion object {
        // Connection pool boundaries
        const val MIN_POOL_SIZE = 5
        const val MAX_POOL_SIZE = 50
        const val DEFAULT_POOL_SIZE = 20
        
        // Performance thresholds
        const val TARGET_CONNECTION_WAIT_MS = 100L
        const val MAX_CONNECTION_WAIT_MS = 1000L
        const val SLOW_QUERY_THRESHOLD_MS = 100L
        
        // Query cache settings
        const val QUERY_CACHE_TTL_SECONDS = 300L
        const val MAX_CACHED_QUERIES = 1000
        
        // Index optimization
        const val INDEX_USAGE_THRESHOLD = 0.7 // 70% usage to consider effective
        
        // Redis keys
        const val QUERY_STATS_KEY = "db:query:stats:"
        const val SLOW_QUERY_KEY = "db:query:slow:"
        const val POOL_METRICS_KEY = "db:pool:metrics:"
    }
    
    private val queryExecutionTimes = ConcurrentHashMap<String, MutableList<Long>>()
    private val slowQueries = ConcurrentHashMap<String, QueryStats>()
    
    private val hikariPool: HikariPoolMXBean? by lazy {
        if (dataSource is HikariDataSource) {
            dataSource.hikariPoolMXBean
        } else {
            null
        }
    }
    
    fun getPoolMetrics(): ConnectionPoolMetrics {
        return hikariPool?.let { pool ->
            ConnectionPoolMetrics(
                totalConnections = pool.totalConnections,
                activeConnections = pool.activeConnections,
                idleConnections = pool.idleConnections,
                threadsAwaitingConnection = pool.threadsAwaitingConnection,
                connectionWaitTime = getAverageConnectionWaitTime()
            )
        } ?: ConnectionPoolMetrics(0, 0, 0, 0, 0.0)
    }
    
    fun optimizeConnectionPool(): PoolOptimizationResult {
        val metrics = getPoolMetrics()
        val recommendations = mutableListOf<String>()
        var newPoolSize: Int? = null
        
        try {
            // Analyze connection usage patterns
            val utilizationRate = if (metrics.totalConnections > 0) {
                metrics.activeConnections.toDouble() / metrics.totalConnections
            } else {
                0.0
            }
            
            // High wait times - increase pool size
            if (metrics.connectionWaitTime > TARGET_CONNECTION_WAIT_MS && 
                metrics.threadsAwaitingConnection > 0) {
                
                val suggestedSize = min(
                    metrics.totalConnections + 5,
                    MAX_POOL_SIZE
                )
                
                if (suggestedSize > metrics.totalConnections) {
                    newPoolSize = suggestedSize
                    recommendations.add("Increase pool size to $suggestedSize to reduce wait times")
                }
            }
            
            // Low utilization - decrease pool size
            if (utilizationRate < 0.3 && metrics.totalConnections > MIN_POOL_SIZE) {
                val suggestedSize = max(
                    (metrics.totalConnections * 0.8).toInt(),
                    MIN_POOL_SIZE
                )
                
                newPoolSize = suggestedSize
                recommendations.add("Decrease pool size to $suggestedSize due to low utilization")
            }
            
            // Apply optimizations if needed
            if (newPoolSize != null && dataSource is HikariDataSource) {
                adjustPoolSize(newPoolSize)
                recommendations.add("Pool size adjusted successfully")
            }
            
            // Additional recommendations
            if (metrics.connectionWaitTime > MAX_CONNECTION_WAIT_MS) {
                recommendations.add("Critical: Connection wait time exceeds threshold")
            }
            
            recordPoolMetrics(metrics)
            
            return PoolOptimizationResult(
                currentSize = metrics.totalConnections,
                recommendedSize = newPoolSize ?: metrics.totalConnections,
                utilizationRate = utilizationRate,
                recommendations = recommendations
            )
            
        } catch (e: Exception) {
            logger.error(e) { "Error optimizing connection pool" }
            return PoolOptimizationResult(
                metrics.totalConnections,
                metrics.totalConnections,
                0.0,
                listOf("Error: ${e.message}")
            )
        }
    }
    
    fun analyzeSlowQueries(limit: Int = 10): List<QueryAnalysis> {
        return slowQueries.entries
            .sortedByDescending { it.value.avgExecutionTime }
            .take(limit)
            .map { (query, stats) ->
                QueryAnalysis(
                    query = query,
                    avgExecutionTime = stats.avgExecutionTime,
                    executionCount = stats.count,
                    recommendations = generateQueryRecommendations(query, stats)
                )
            }
    }
    
    fun optimizeQuery(query: String): QueryOptimizationResult {
        return try {
            // Analyze query execution plan
            val explainQuery = "EXPLAIN (ANALYZE, BUFFERS) $query"
            val executionPlan = jdbcTemplate.queryForList(explainQuery)
            
            val recommendations = mutableListOf<String>()
            var optimizedQuery = query
            
            // Parse execution plan for optimization opportunities
            executionPlan.forEach { row ->
                val planText = row.values.joinToString(" ")
                
                // Check for sequential scans
                if (planText.contains("Seq Scan", ignoreCase = true)) {
                    recommendations.add("Consider adding index to avoid sequential scan")
                }
                
                // Check for missing indexes
                if (planText.contains("Filter:", ignoreCase = true)) {
                    recommendations.add("Consider adding index on filter columns")
                }
                
                // Check for inefficient joins
                if (planText.contains("Nested Loop", ignoreCase = true) && 
                    planText.contains("rows=", ignoreCase = true)) {
                    val rowsMatch = Regex("rows=(\\d+)").find(planText)
                    val rows = rowsMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
                    if (rows > 1000) {
                        recommendations.add("Consider using hash join for large datasets")
                    }
                }
            }
            
            // Check for common optimization patterns
            if (query.contains("SELECT *", ignoreCase = true)) {
                recommendations.add("Specify only required columns instead of SELECT *")
                optimizedQuery = optimizedQuery.replace("SELECT *", "SELECT /* specific columns */")
            }
            
            if (!query.contains("LIMIT", ignoreCase = true) && 
                query.contains("SELECT", ignoreCase = true)) {
                recommendations.add("Consider adding LIMIT clause for better performance")
            }
            
            QueryOptimizationResult(
                originalQuery = query,
                optimizedQuery = optimizedQuery,
                recommendations = recommendations,
                estimatedImprovement = calculateEstimatedImprovement(recommendations)
            )
            
        } catch (e: Exception) {
            logger.error(e) { "Error optimizing query: $query" }
            QueryOptimizationResult(
                query,
                query,
                listOf("Error analyzing query: ${e.message}"),
                0.0
            )
        }
    }
    
    fun analyzeIndexUsage(): IndexAnalysisResult {
        return try {
            // Query for index usage statistics
            val indexStats = jdbcTemplate.queryForList("""
                SELECT 
                    schemaname,
                    tablename,
                    indexname,
                    idx_scan,
                    idx_tup_read,
                    idx_tup_fetch
                FROM pg_stat_user_indexes
                WHERE schemaname = 'public'
                ORDER BY idx_scan DESC
            """)
            
            val unusedIndexes = mutableListOf<String>()
            val inefficientIndexes = mutableListOf<String>()
            val recommendations = mutableListOf<String>()
            
            indexStats.forEach { row ->
                val indexName = row["indexname"] as String
                val scans = (row["idx_scan"] as Number).toLong()
                val tupleReads = (row["idx_tup_read"] as Number).toLong()
                
                if (scans == 0L) {
                    unusedIndexes.add(indexName)
                } else if (tupleReads > 0 && scans > 0) {
                    val efficiency = tupleReads.toDouble() / scans
                    if (efficiency < 100) {
                        inefficientIndexes.add(indexName)
                    }
                }
            }
            
            // Generate recommendations
            if (unusedIndexes.isNotEmpty()) {
                recommendations.add("Consider dropping unused indexes: ${unusedIndexes.take(3).joinToString()}")
            }
            
            if (inefficientIndexes.isNotEmpty()) {
                recommendations.add("Review inefficient indexes: ${inefficientIndexes.take(3).joinToString()}")
            }
            
            // Check for missing indexes on foreign keys
            val missingFKIndexes = checkMissingForeignKeyIndexes()
            if (missingFKIndexes.isNotEmpty()) {
                recommendations.add("Add indexes on foreign keys: ${missingFKIndexes.joinToString()}")
            }
            
            IndexAnalysisResult(
                totalIndexes = indexStats.size,
                unusedIndexes = unusedIndexes,
                inefficientIndexes = inefficientIndexes,
                recommendations = recommendations
            )
            
        } catch (e: Exception) {
            logger.error(e) { "Error analyzing index usage" }
            IndexAnalysisResult(0, emptyList(), emptyList(), listOf("Error: ${e.message}"))
        }
    }
    
    fun recordQueryExecution(query: String, executionTime: Long) {
        try {
            // Normalize query for grouping
            val normalizedQuery = normalizeQuery(query)
            
            // Record execution time
            queryExecutionTimes.computeIfAbsent(normalizedQuery) { mutableListOf() }
                .add(executionTime)
            
            // Keep only recent executions
            val executions = queryExecutionTimes[normalizedQuery]!!
            if (executions.size > 100) {
                executions.removeAt(0)
            }
            
            // Track slow queries
            if (executionTime > SLOW_QUERY_THRESHOLD_MS) {
                updateSlowQueryStats(normalizedQuery, executionTime)
            }
            
            // Record metrics
            meterRegistry.timer("db.query.execution", "query", normalizedQuery)
                .record(Duration.ofMillis(executionTime))
            
        } catch (e: Exception) {
            logger.error(e) { "Error recording query execution" }
        }
    }
    
    @Scheduled(fixedDelay = 60000) // Every minute
    fun performPeriodicOptimization() {
        try {
            // Optimize connection pool
            val poolResult = optimizeConnectionPool()
            if (poolResult.recommendations.isNotEmpty()) {
                logger.info { "Pool optimization: ${poolResult.recommendations.joinToString()}" }
            }
            
            // Clean up old metrics
            cleanupOldMetrics()
            
        } catch (e: Exception) {
            logger.error(e) { "Error in periodic optimization" }
        }
    }
    
    override fun health(): Health {
        return try {
            val metrics = getPoolMetrics()
            
            if (metrics.connectionWaitTime > MAX_CONNECTION_WAIT_MS) {
                Health.down()
                    .withDetail("connectionWaitTime", metrics.connectionWaitTime)
                    .withDetail("reason", "High connection wait time")
                    .build()
            } else if (metrics.threadsAwaitingConnection > 5) {
                Health.down()
                    .withDetail("threadsWaiting", metrics.threadsAwaitingConnection)
                    .withDetail("reason", "Too many threads waiting")
                    .build()
            } else {
                Health.up()
                    .withDetail("activeConnections", metrics.activeConnections)
                    .withDetail("totalConnections", metrics.totalConnections)
                    .build()
            }
        } catch (e: Exception) {
            Health.down()
                .withException(e)
                .build()
        }
    }
    
    private fun adjustPoolSize(newSize: Int) {
        if (dataSource is HikariDataSource) {
            dataSource.maximumPoolSize = newSize
            logger.info { "Adjusted connection pool size to $newSize" }
        }
    }
    
    private fun getAverageConnectionWaitTime(): Double {
        // This would typically come from HikariCP metrics
        // For now, returning a placeholder
        return 50.0
    }
    
    private fun normalizeQuery(query: String): String {
        // Remove specific values to group similar queries
        return query
            .replace(Regex("\\b\\d+\\b"), "?")
            .replace(Regex("'[^']*'"), "?")
            .trim()
            .take(200) // Limit length
    }
    
    private fun updateSlowQueryStats(query: String, executionTime: Long) {
        slowQueries.compute(query) { _, existing ->
            if (existing == null) {
                QueryStats(query, 1, executionTime.toDouble(), executionTime)
            } else {
                QueryStats(
                    query,
                    existing.count + 1,
                    (existing.avgExecutionTime * existing.count + executionTime) / (existing.count + 1),
                    max(existing.maxExecutionTime, executionTime)
                )
            }
        }
    }
    
    private fun generateQueryRecommendations(query: String, stats: QueryStats): List<String> {
        val recommendations = mutableListOf<String>()
        
        if (stats.avgExecutionTime > 1000) {
            recommendations.add("Query consistently slow - consider optimization")
        }
        
        if (query.contains("JOIN", ignoreCase = true) && stats.avgExecutionTime > 500) {
            recommendations.add("Review join conditions and indexes")
        }
        
        if (!query.contains("LIMIT", ignoreCase = true)) {
            recommendations.add("Consider adding LIMIT clause")
        }
        
        return recommendations
    }
    
    private fun checkMissingForeignKeyIndexes(): List<String> {
        // This would check for foreign keys without indexes
        // Simplified implementation
        return emptyList()
    }
    
    private fun calculateEstimatedImprovement(recommendations: List<String>): Double {
        // Estimate improvement based on recommendations
        var improvement = 0.0
        
        recommendations.forEach { rec ->
            when {
                rec.contains("index", ignoreCase = true) -> improvement += 0.3
                rec.contains("SELECT *", ignoreCase = true) -> improvement += 0.1
                rec.contains("LIMIT", ignoreCase = true) -> improvement += 0.2
                rec.contains("join", ignoreCase = true) -> improvement += 0.25
            }
        }
        
        return min(improvement, 0.8) // Cap at 80% improvement
    }
    
    private fun recordPoolMetrics(metrics: ConnectionPoolMetrics) {
        try {
            val metricsMap = mapOf(
                "totalConnections" to metrics.totalConnections.toString(),
                "activeConnections" to metrics.activeConnections.toString(),
                "idleConnections" to metrics.idleConnections.toString(),
                "threadsWaiting" to metrics.threadsAwaitingConnection.toString(),
                "connectionWaitTime" to metrics.connectionWaitTime.toString(),
                "timestamp" to System.currentTimeMillis().toString()
            )
            
            redisTemplate.opsForHash<String, String>()
                .putAll("$POOL_METRICS_KEY${System.currentTimeMillis()}", metricsMap)
            
            redisTemplate.expire(
                "$POOL_METRICS_KEY${System.currentTimeMillis()}",
                Duration.ofHours(1)
            )
            
        } catch (e: Exception) {
            logger.error(e) { "Failed to record pool metrics" }
        }
    }
    
    private fun cleanupOldMetrics() {
        // Clean up query execution times older than 1 hour
        val cutoff = System.currentTimeMillis() - 3600000
        
        queryExecutionTimes.forEach { (query, times) ->
            times.removeIf { it < cutoff }
        }
        
        // Remove empty entries
        queryExecutionTimes.entries.removeIf { it.value.isEmpty() }
    }
    
    data class ConnectionPoolMetrics(
        val totalConnections: Int,
        val activeConnections: Int,
        val idleConnections: Int,
        val threadsAwaitingConnection: Int,
        val connectionWaitTime: Double
    )
    
    data class PoolOptimizationResult(
        val currentSize: Int,
        val recommendedSize: Int,
        val utilizationRate: Double,
        val recommendations: List<String>
    )
    
    data class QueryStats(
        val query: String,
        val count: Int,
        val avgExecutionTime: Double,
        val maxExecutionTime: Long
    )
    
    data class QueryAnalysis(
        val query: String,
        val avgExecutionTime: Double,
        val executionCount: Int,
        val recommendations: List<String>
    )
    
    data class QueryOptimizationResult(
        val originalQuery: String,
        val optimizedQuery: String,
        val recommendations: List<String>,
        val estimatedImprovement: Double
    )
    
    data class IndexAnalysisResult(
        val totalIndexes: Int,
        val unusedIndexes: List<String>,
        val inefficientIndexes: List<String>,
        val recommendations: List<String>
    )
}