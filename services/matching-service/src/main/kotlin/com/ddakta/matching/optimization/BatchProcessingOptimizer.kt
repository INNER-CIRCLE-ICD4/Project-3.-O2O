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
        // 동적 배치 크기 경계값
        const val MIN_BATCH_SIZE = 10
        const val MAX_BATCH_SIZE = 500
        const val DEFAULT_BATCH_SIZE = 100
        
        // 동적 간격 경계값 (밀리초)
        const val MIN_INTERVAL_MS = 500L
        const val MAX_INTERVAL_MS = 5000L
        const val DEFAULT_INTERVAL_MS = 1000L
        
        // 성능 임계값
        const val TARGET_PROCESSING_TIME_MS = 800L // 1초의 80%
        const val MAX_QUEUE_SIZE = 10000
        const val LATENCY_THRESHOLD_MS = 1500L
        
        // 조정 요소
        const val SIZE_INCREASE_FACTOR = 1.2
        const val SIZE_DECREASE_FACTOR = 0.8
        const val INTERVAL_ADJUSTMENT_STEP = 100L
        
        // Redis 키
        const val BATCH_METRICS_KEY = "batch:metrics:"
        const val BATCH_CONFIG_KEY = "batch:config:"
    }
    
    // 현재 설정
    private var currentBatchSize = AtomicInteger(DEFAULT_BATCH_SIZE)
    private var currentInterval = AtomicLong(DEFAULT_INTERVAL_MS)
    
    // 성능 추적
    private val processingTimes = ConcurrentHashMap<Long, Long>()
    private val queueSizes = ConcurrentHashMap<Long, Int>()
    private val successRates = ConcurrentHashMap<Long, Double>()
    
    // 메트릭
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
            
            // 메트릭 기록
            processingTimes[timestamp] = lastProcessingTime
            queueSizes[timestamp] = currentQueueSize
            successRates[timestamp] = successRate
            
            // 오래된 메트릭 정리 (최근 5분 유지)
            cleanOldMetrics(timestamp - 300000)
            
            // 조정값 계산
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
            
            // 중요한 변경사항이 있으면 적용
            if (shouldAdjustBatchSize(newSize)) {
                currentBatchSize.set(newSize)
                logger.info { "Adjusted batch size to $newSize" }
            }
            
            if (shouldAdjustInterval(newInterval)) {
                currentInterval.set(newInterval)
                logger.info { "Adjusted batch interval to ${newInterval}ms" }
            }
            
            // 설정 저장
            persistConfiguration()
            
            // 최적화 메트릭 기록
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
        // 비상 모드에서는 처리량보다 지연 시간을 우선시
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
        
        // 큐 압력 요소
        val queuePressure = queueSize.toDouble() / MAX_QUEUE_SIZE
        
        // 처리 효율 요소
        val processingEfficiency = TARGET_PROCESSING_TIME_MS.toDouble() / processingTime
        
        // 성공률 요소
        val successFactor = successRate
        
        // 결합 점수 (0.0에서 1.0)
        val performanceScore = (processingEfficiency * 0.5 + 
                               successFactor * 0.3 + 
                               (1 - queuePressure) * 0.2)
        
        return when {
            // 높은 큐 압력 - 배치 크기 증가
            queuePressure > 0.7 && processingTime < TARGET_PROCESSING_TIME_MS -> {
                min((currentSize * SIZE_INCREASE_FACTOR).toInt(), MAX_BATCH_SIZE)
            }
            // 낮은 효율 - 배치 크기 감소
            processingEfficiency < 0.5 || successRate < 0.8 -> {
                max((currentSize * SIZE_DECREASE_FACTOR).toInt(), MIN_BATCH_SIZE)
            }
            // 성장 여지가 있는 좋은 성능
            performanceScore > 0.8 && queuePressure > 0.3 -> {
                min((currentSize * 1.1).toInt(), MAX_BATCH_SIZE)
            }
            // 안정적인 성능
            else -> currentSize
        }
    }
    
    private fun calculateOptimalInterval(
        queueSize: Int,
        processingTime: Long,
        batchSize: Int
    ): Long {
        val currentIntervalMs = currentInterval.get()
        
        // 목표 처리 비율 계산
        val targetRate = queueSize.toDouble() / batchSize
        
        // 지연 시간 고려사항
        val latencyFactor = if (processingTime > LATENCY_THRESHOLD_MS) {
            0.8 // 따라잡기 위해 간격 감소
        } else {
            1.0
        }
        
        return when {
            // 높은 큐 압력 - 간격 감소
            targetRate > 5 -> {
                max((currentIntervalMs - INTERVAL_ADJUSTMENT_STEP) * latencyFactor, MIN_INTERVAL_MS.toDouble()).toLong()
            }
            // 낮은 큐 압력 - 간격 증가
            targetRate < 1 -> {
                min(currentIntervalMs + INTERVAL_ADJUSTMENT_STEP, MAX_INTERVAL_MS)
            }
            // 안정적
            else -> currentIntervalMs
        }
    }
    
    private fun shouldAdjustBatchSize(newSize: Int): Boolean {
        val currentSize = currentBatchSize.get()
        val changePct = kotlin.math.abs(newSize - currentSize).toDouble() / currentSize
        return changePct > 0.1 // 변경사항이 10% 이상일 때만 조정
    }
    
    private fun shouldAdjustInterval(newInterval: Long): Boolean {
        val currentIntervalMs = currentInterval.get()
        val changePct = kotlin.math.abs(newInterval - currentIntervalMs).toDouble() / currentIntervalMs
        return changePct > 0.1 // 변경사항이 10% 이상일 때만 조정
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