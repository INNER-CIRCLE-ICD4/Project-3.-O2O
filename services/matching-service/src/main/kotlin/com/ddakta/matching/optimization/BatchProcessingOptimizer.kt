package com.ddakta.matching.optimization

import com.ddakta.matching.config.MatchingProperties
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import mu.KotlinLogging
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max
import kotlin.math.min

@Service
class BatchProcessingOptimizer(
    private val matchingProperties: MatchingProperties,
    private val redisTemplate: RedisTemplate<String, String>,
    private val meterRegistry: MeterRegistry
) {
    
    private val logger = KotlinLogging.logger {}
    
    companion object {
        // Dynamic batch size boundaries
        const val MIN_BATCH_SIZE = 10
        const val MAX_BATCH_SIZE = 500
        const val DEFAULT_BATCH_SIZE = 100
        
        // Dynamic interval boundaries (milliseconds)
        const val MIN_INTERVAL_MS = 500L
        const val MAX_INTERVAL_MS = 5000L
        const val DEFAULT_INTERVAL_MS = 1000L
        
        // Performance thresholds
        const val TARGET_PROCESSING_TIME_MS = 800L // 80% of 1 second
        const val MAX_QUEUE_SIZE = 10000
        const val LATENCY_THRESHOLD_MS = 1500L
        
        // Adjustment factors
        const val SIZE_INCREASE_FACTOR = 1.2
        const val SIZE_DECREASE_FACTOR = 0.8
        const val INTERVAL_ADJUSTMENT_STEP = 100L
        
        // Redis keys
        const val BATCH_METRICS_KEY = "batch:metrics:"
        const val BATCH_CONFIG_KEY = "batch:config:"
    }
    
    // Current configuration
    private var currentBatchSize = AtomicInteger(DEFAULT_BATCH_SIZE)
    private var currentInterval = AtomicLong(DEFAULT_INTERVAL_MS)
    
    // Performance tracking
    private val processingTimes = ConcurrentHashMap<Long, Long>()
    private val queueSizes = ConcurrentHashMap<Long, Int>()
    private val successRates = ConcurrentHashMap<Long, Double>()
    
    // Metrics
    private val batchSizeGauge = meterRegistry.gauge("matching.batch.size", currentBatchSize)
    private val intervalGauge = meterRegistry.gauge("matching.batch.interval", currentInterval)
    private val processingTimer = Timer.builder("matching.batch.processing.time")
        .register(meterRegistry)
    
    fun getCurrentBatchConfig(): BatchConfig {
        return BatchConfig(
            batchSize = currentBatchSize.get(),
            intervalMs = currentInterval.get(),
            maxQueueSize = MAX_QUEUE_SIZE,
            adaptiveEnabled = true
        )
    }
    
    fun optimizeBatchParameters(
        currentQueueSize: Int,
        lastProcessingTime: Long,
        successRate: Double
    ): BatchConfig {
        try {
            val timestamp = System.currentTimeMillis()
            
            // Record metrics
            processingTimes[timestamp] = lastProcessingTime
            queueSizes[timestamp] = currentQueueSize
            successRates[timestamp] = successRate
            
            // Clean old metrics (keep last 5 minutes)
            cleanOldMetrics(timestamp - 300000)
            
            // Calculate adjustments
            val newSize = calculateOptimalBatchSize(
                currentQueueSize,
                lastProcessingTime,
                successRate
            )
            
            val newInterval = calculateOptimalInterval(
                currentQueueSize,
                lastProcessingTime,
                newSize
            )
            
            // Apply changes if significant
            if (shouldAdjustBatchSize(newSize)) {
                currentBatchSize.set(newSize)
                logger.info { "Adjusted batch size to $newSize" }
            }
            
            if (shouldAdjustInterval(newInterval)) {
                currentInterval.set(newInterval)
                logger.info { "Adjusted batch interval to ${newInterval}ms" }
            }
            
            // Persist configuration
            persistConfiguration()
            
            // Record optimization metrics
            recordOptimizationMetrics(currentQueueSize, lastProcessingTime, successRate)
            
            return getCurrentBatchConfig()
            
        } catch (e: Exception) {
            logger.error(e) { "Error optimizing batch parameters" }
            return getCurrentBatchConfig()
        }
    }
    
    fun getPerformanceMetrics(): BatchPerformanceMetrics {
        val recentProcessingTimes = processingTimes.values.toList().takeLast(10)
        val recentQueueSizes = queueSizes.values.toList().takeLast(10)
        val recentSuccessRates = successRates.values.toList().takeLast(10)
        
        return BatchPerformanceMetrics(
            avgProcessingTime = recentProcessingTimes.average(),
            avgQueueSize = recentQueueSizes.average(),
            avgSuccessRate = recentSuccessRates.average(),
            currentBatchSize = currentBatchSize.get(),
            currentInterval = currentInterval.get(),
            throughput = calculateThroughput(),
            latency = calculateAverageLatency()
        )
    }
    
    fun applyEmergencyMode() {
        // In emergency mode, prioritize latency over throughput
        currentBatchSize.set(MIN_BATCH_SIZE)
        currentInterval.set(MIN_INTERVAL_MS)
        
        logger.warn { "Applied emergency mode: batch_size=$MIN_BATCH_SIZE, interval=${MIN_INTERVAL_MS}ms" }
        
        persistConfiguration()
    }
    
    fun resetToDefaults() {
        currentBatchSize.set(matchingProperties.batch.size)
        currentInterval.set(matchingProperties.batch.interval.toLong())
        
        logger.info { "Reset batch parameters to defaults" }
        
        persistConfiguration()
    }
    
    private fun calculateOptimalBatchSize(
        queueSize: Int,
        processingTime: Long,
        successRate: Double
    ): Int {
        val currentSize = currentBatchSize.get()
        
        // Queue pressure factor
        val queuePressure = queueSize.toDouble() / MAX_QUEUE_SIZE
        
        // Processing efficiency factor
        val processingEfficiency = TARGET_PROCESSING_TIME_MS.toDouble() / processingTime
        
        // Success rate factor
        val successFactor = successRate
        
        // Combined score (0.0 to 1.0)
        val performanceScore = (processingEfficiency * 0.5 + 
                               successFactor * 0.3 + 
                               (1 - queuePressure) * 0.2)
        
        return when {
            // High queue pressure - increase batch size
            queuePressure > 0.7 && processingTime < TARGET_PROCESSING_TIME_MS -> {
                min((currentSize * SIZE_INCREASE_FACTOR).toInt(), MAX_BATCH_SIZE)
            }
            // Low efficiency - decrease batch size
            processingEfficiency < 0.5 || successRate < 0.8 -> {
                max((currentSize * SIZE_DECREASE_FACTOR).toInt(), MIN_BATCH_SIZE)
            }
            // Good performance with room to grow
            performanceScore > 0.8 && queuePressure > 0.3 -> {
                min((currentSize * 1.1).toInt(), MAX_BATCH_SIZE)
            }
            // Stable performance
            else -> currentSize
        }
    }
    
    private fun calculateOptimalInterval(
        queueSize: Int,
        processingTime: Long,
        batchSize: Int
    ): Long {
        val currentIntervalMs = currentInterval.get()
        
        // Calculate target processing rate
        val targetRate = queueSize.toDouble() / batchSize
        
        // Latency consideration
        val latencyFactor = if (processingTime > LATENCY_THRESHOLD_MS) {
            0.8 // Reduce interval to catch up
        } else {
            1.0
        }
        
        return when {
            // High queue pressure - reduce interval
            targetRate > 5 -> {
                max((currentIntervalMs - INTERVAL_ADJUSTMENT_STEP) * latencyFactor, MIN_INTERVAL_MS.toDouble()).toLong()
            }
            // Low queue pressure - increase interval
            targetRate < 1 -> {
                min(currentIntervalMs + INTERVAL_ADJUSTMENT_STEP, MAX_INTERVAL_MS)
            }
            // Stable
            else -> currentIntervalMs
        }
    }
    
    private fun shouldAdjustBatchSize(newSize: Int): Boolean {
        val currentSize = currentBatchSize.get()
        val changePct = kotlin.math.abs(newSize - currentSize).toDouble() / currentSize
        return changePct > 0.1 // Only adjust if change is more than 10%
    }
    
    private fun shouldAdjustInterval(newInterval: Long): Boolean {
        val currentIntervalMs = currentInterval.get()
        val changePct = kotlin.math.abs(newInterval - currentIntervalMs).toDouble() / currentIntervalMs
        return changePct > 0.1 // Only adjust if change is more than 10%
    }
    
    private fun calculateThroughput(): Double {
        val recentSizes = queueSizes.values.toList().takeLast(10)
        val timeWindow = 10000.0 // 10 seconds
        
        return if (recentSizes.isNotEmpty()) {
            (recentSizes.sum() * currentBatchSize.get()) / timeWindow * 1000
        } else {
            0.0
        }
    }
    
    private fun calculateAverageLatency(): Double {
        val recentTimes = processingTimes.values.toList().takeLast(10)
        return if (recentTimes.isNotEmpty()) {
            recentTimes.average() + currentInterval.get() / 2
        } else {
            currentInterval.get().toDouble()
        }
    }
    
    private fun cleanOldMetrics(cutoffTime: Long) {
        processingTimes.keys.removeIf { it < cutoffTime }
        queueSizes.keys.removeIf { it < cutoffTime }
        successRates.keys.removeIf { it < cutoffTime }
    }
    
    private fun persistConfiguration() {
        try {
            val config = mapOf(
                "batchSize" to currentBatchSize.get().toString(),
                "interval" to currentInterval.get().toString(),
                "timestamp" to System.currentTimeMillis().toString()
            )
            
            redisTemplate.opsForHash<String, String>()
                .putAll("$BATCH_CONFIG_KEY${System.currentTimeMillis() / 60000}", config)
            
            redisTemplate.expire(
                "$BATCH_CONFIG_KEY${System.currentTimeMillis() / 60000}",
                Duration.ofHours(24)
            )
            
        } catch (e: Exception) {
            logger.error(e) { "Failed to persist batch configuration" }
        }
    }
    
    private fun recordOptimizationMetrics(
        queueSize: Int,
        processingTime: Long,
        successRate: Double
    ) {
        processingTimer.record(Duration.ofMillis(processingTime))
        
        meterRegistry.gauge("matching.batch.queue.size", queueSize)
        meterRegistry.gauge("matching.batch.success.rate", successRate)
        
        val metrics = mapOf(
            "queueSize" to queueSize.toString(),
            "processingTime" to processingTime.toString(),
            "successRate" to successRate.toString(),
            "batchSize" to currentBatchSize.get().toString(),
            "interval" to currentInterval.get().toString()
        )
        
        try {
            redisTemplate.opsForHash<String, String>()
                .putAll("$BATCH_METRICS_KEY${System.currentTimeMillis()}", metrics)
            
            redisTemplate.expire(
                "$BATCH_METRICS_KEY${System.currentTimeMillis()}",
                Duration.ofHours(1)
            )
        } catch (e: Exception) {
            logger.error(e) { "Failed to record optimization metrics" }
        }
    }
    
    data class BatchConfig(
        val batchSize: Int,
        val intervalMs: Long,
        val maxQueueSize: Int,
        val adaptiveEnabled: Boolean
    )
    
    data class BatchPerformanceMetrics(
        val avgProcessingTime: Double,
        val avgQueueSize: Double,
        val avgSuccessRate: Double,
        val currentBatchSize: Int,
        val currentInterval: Long,
        val throughput: Double,
        val latency: Double
    )
}