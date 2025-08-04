package com.ddakta.matching.scheduler

import com.ddakta.matching.cache.DriverAvailabilityCacheService
import com.ddakta.matching.client.LocationServiceClient
import com.ddakta.matching.event.producer.MatchingEventProducer
import io.micrometer.core.instrument.MeterRegistry
import mu.KotlinLogging
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min

@Component
class SurgePricingScheduler(
    private val driverAvailabilityCacheService: DriverAvailabilityCacheService,
    private val locationServiceClient: LocationServiceClient,
    private val eventProducer: MatchingEventProducer,
    private val redisTemplate: RedisTemplate<String, String>,
    private val meterRegistry: MeterRegistry
) {
    
    private val logger = KotlinLogging.logger {}
    
    companion object {
        // Surge pricing configuration
        const val MIN_SURGE_MULTIPLIER = 1.0
        const val MAX_SURGE_MULTIPLIER = 3.0
        const val SURGE_INCREMENT = 0.2
        
        // Thresholds
        const val HIGH_DEMAND_THRESHOLD = 0.8 // 80% of drivers busy
        const val LOW_SUPPLY_THRESHOLD = 5 // Less than 5 available drivers
        const val DEMAND_SPIKE_THRESHOLD = 2.0 // 2x normal demand
        
        // Redis keys
        const val SURGE_PRICING_KEY = "surge:pricing:"
        const val SURGE_HISTORY_KEY = "surge:history:"
        const val DEMAND_BASELINE_KEY = "demand:baseline:"
        
        // Time windows
        const val DEMAND_WINDOW_MINUTES = 15L
        const val SURGE_DURATION_MINUTES = 30L
        const val BASELINE_UPDATE_HOURS = 24L
    }
    
    private val currentSurgeMultipliers = ConcurrentHashMap<String, Double>()
    private val demandBaselines = ConcurrentHashMap<String, DemandBaseline>()
    
    @Scheduled(fixedDelay = 60000) // Every minute
    fun calculateSurgePricing() {
        try {
            logger.debug { "Starting surge pricing calculation" }
            
            // Get all H3 cells with statistics
            val cellStats = driverAvailabilityCacheService.getH3CellStats()
            
            cellStats.forEach { (h3Index, stats) ->
                val surgeMultiplier = calculateSurgeForCell(h3Index, stats)
                
                // Update surge pricing if changed
                if (shouldUpdateSurge(h3Index, surgeMultiplier)) {
                    updateSurgePricing(h3Index, surgeMultiplier)
                }
            }
            
            // Clean up expired surge pricing
            cleanupExpiredSurge()
            
            logger.info { "Surge pricing calculation completed for ${cellStats.size} cells" }
            
        } catch (e: Exception) {
            logger.error(e) { "Error calculating surge pricing" }
        }
    }
    
    @Scheduled(cron = "0 0 * * * *") // Every hour
    fun updateDemandBaselines() {
        try {
            logger.info { "Updating demand baselines" }
            
            val h3Cells = redisTemplate.keys("$DEMAND_BASELINE_KEY*")
                .map { it.removePrefix(DEMAND_BASELINE_KEY) }
            
            h3Cells.forEach { h3Index ->
                updateBaselineForCell(h3Index)
            }
            
            logger.info { "Updated baselines for ${h3Cells.size} cells" }
            
        } catch (e: Exception) {
            logger.error(e) { "Error updating demand baselines" }
        }
    }
    
    private fun calculateSurgeForCell(
        h3Index: String,
        stats: DriverAvailabilityCacheService.H3CellStats
    ): Double {
        // Get historical demand for this time window
        val baseline = getDemandBaseline(h3Index)
        val currentDemand = stats.recentDemand
        val availableDrivers = stats.availableDrivers
        
        // Calculate demand factors
        val demandRatio = if (baseline.avgDemand > 0) {
            currentDemand.toDouble() / baseline.avgDemand
        } else {
            1.0
        }
        
        val supplyRatio = if (currentDemand > 0) {
            availableDrivers.toDouble() / currentDemand
        } else {
            Double.MAX_VALUE
        }
        
        // Determine surge multiplier
        var surgeMultiplier = MIN_SURGE_MULTIPLIER
        
        // High demand relative to baseline
        if (demandRatio > DEMAND_SPIKE_THRESHOLD) {
            surgeMultiplier += SURGE_INCREMENT * (demandRatio - 1)
        }
        
        // Low supply
        if (availableDrivers < LOW_SUPPLY_THRESHOLD && currentDemand > 0) {
            surgeMultiplier += SURGE_INCREMENT * 2
        }
        
        // Supply-demand imbalance
        if (supplyRatio < HIGH_DEMAND_THRESHOLD) {
            surgeMultiplier += SURGE_INCREMENT * (1 - supplyRatio)
        }
        
        // Time-based adjustments (peak hours)
        val hourMultiplier = getTimeBasedMultiplier()
        surgeMultiplier *= hourMultiplier
        
        // Cap the multiplier
        surgeMultiplier = min(surgeMultiplier, MAX_SURGE_MULTIPLIER)
        surgeMultiplier = max(surgeMultiplier, MIN_SURGE_MULTIPLIER)
        
        // Round to nearest increment
        surgeMultiplier = (surgeMultiplier / SURGE_INCREMENT).toInt() * SURGE_INCREMENT
        
        logger.debug { 
            "Surge calculation for $h3Index: demand=$currentDemand, " +
            "drivers=$availableDrivers, multiplier=$surgeMultiplier" 
        }
        
        return surgeMultiplier
    }
    
    private fun shouldUpdateSurge(h3Index: String, newMultiplier: Double): Boolean {
        val currentMultiplier = currentSurgeMultipliers[h3Index] ?: MIN_SURGE_MULTIPLIER
        
        // Only update if change is significant (>= 0.2)
        return kotlin.math.abs(newMultiplier - currentMultiplier) >= SURGE_INCREMENT
    }
    
    private fun updateSurgePricing(h3Index: String, multiplier: Double) {
        try {
            currentSurgeMultipliers[h3Index] = multiplier
            
            // Store in Redis with expiration
            val key = "$SURGE_PRICING_KEY$h3Index"
            val surgeData = SurgePricingData(
                h3Index = h3Index,
                multiplier = multiplier,
                startTime = System.currentTimeMillis(),
                expiryTime = System.currentTimeMillis() + (SURGE_DURATION_MINUTES * 60 * 1000),
                reason = determineSurgeReason(h3Index)
            )
            
            redisTemplate.opsForValue().set(
                key,
                surgeData.toJson(),
                Duration.ofMinutes(SURGE_DURATION_MINUTES)
            )
            
            // Record history
            recordSurgeHistory(h3Index, multiplier)
            
            // Send event if surge is active
            if (multiplier > MIN_SURGE_MULTIPLIER) {
                eventProducer.publishSurgePricingUpdate(
                    h3Index = h3Index,
                    multiplier = multiplier,
                    reason = surgeData.reason
                )
            }
            
            // Record metrics
            meterRegistry.gauge("surge.pricing.multiplier", 
                listOf(io.micrometer.core.instrument.Tag.of("h3_index", h3Index)), 
                multiplier)
            
            logger.info { "Updated surge pricing for $h3Index to ${multiplier}x" }
            
        } catch (e: Exception) {
            logger.error(e) { "Failed to update surge pricing for $h3Index" }
        }
    }
    
    private fun getDemandBaseline(h3Index: String): DemandBaseline {
        return demandBaselines.computeIfAbsent(h3Index) {
            loadOrCreateBaseline(h3Index)
        }
    }
    
    private fun loadOrCreateBaseline(h3Index: String): DemandBaseline {
        return try {
            val key = "$DEMAND_BASELINE_KEY$h3Index"
            val data = redisTemplate.opsForValue().get(key)
            
            if (data != null) {
                DemandBaseline.fromJson(data)
            } else {
                // Create new baseline from historical data
                val history = driverAvailabilityCacheService.getH3DemandHistory(h3Index, 24)
                val avgDemand = if (history.isNotEmpty()) {
                    history.values.average()
                } else {
                    10.0 // Default baseline
                }
                
                DemandBaseline(
                    h3Index = h3Index,
                    avgDemand = avgDemand,
                    peakDemand = history.values.maxOrNull()?.toDouble() ?: avgDemand * 1.5,
                    lastUpdated = System.currentTimeMillis()
                )
            }
        } catch (e: Exception) {
            logger.error(e) { "Error loading baseline for $h3Index" }
            DemandBaseline(h3Index, 10.0, 15.0, emptyMap(), System.currentTimeMillis())
        }
    }
    
    private fun updateBaselineForCell(h3Index: String) {
        try {
            val history = driverAvailabilityCacheService.getH3DemandHistory(h3Index, 168) // 7 days
            
            if (history.size < 24) {
                logger.debug { "Insufficient data to update baseline for $h3Index" }
                return
            }
            
            // Calculate hourly averages
            val hourlyAverages = mutableMapOf<Int, MutableList<Int>>()
            
            history.forEach { (hourTimestamp, demand) ->
                val hour = LocalDateTime.ofEpochSecond(hourTimestamp * 3600, 0, 
                    java.time.ZoneOffset.UTC).hour
                hourlyAverages.computeIfAbsent(hour) { mutableListOf() }.add(demand)
            }
            
            // Update baseline
            val avgDemand = history.values.average()
            val peakDemand = history.values.maxOrNull()?.toDouble() ?: avgDemand * 1.5
            
            val baseline = DemandBaseline(
                h3Index = h3Index,
                avgDemand = avgDemand,
                peakDemand = peakDemand,
                hourlyAverages = hourlyAverages.mapValues { it.value.average() },
                lastUpdated = System.currentTimeMillis()
            )
            
            // Store in Redis
            val key = "$DEMAND_BASELINE_KEY$h3Index"
            redisTemplate.opsForValue().set(
                key,
                baseline.toJson(),
                Duration.ofDays(7)
            )
            
            demandBaselines[h3Index] = baseline
            
        } catch (e: Exception) {
            logger.error(e) { "Failed to update baseline for $h3Index" }
        }
    }
    
    private fun getTimeBasedMultiplier(): Double {
        val hour = LocalDateTime.now().hour
        
        return when (hour) {
            in 7..9 -> 1.2   // Morning rush
            in 17..19 -> 1.3 // Evening rush
            in 22..23 -> 1.2 // Late night
            in 0..4 -> 1.1   // Early morning
            else -> 1.0
        }
    }
    
    private fun determineSurgeReason(h3Index: String): String {
        val stats = driverAvailabilityCacheService.getH3CellStats()[h3Index]
            ?: return "Unknown"
        
        return when {
            stats.availableDrivers < LOW_SUPPLY_THRESHOLD -> "Low driver supply"
            stats.recentDemand > getDemandBaseline(h3Index).peakDemand -> "High demand"
            getTimeBasedMultiplier() > 1.0 -> "Peak hours"
            else -> "Supply-demand imbalance"
        }
    }
    
    private fun recordSurgeHistory(h3Index: String, multiplier: Double) {
        try {
            val key = "$SURGE_HISTORY_KEY$h3Index"
            val timestamp = System.currentTimeMillis()
            
            redisTemplate.opsForZSet().add(
                key,
                "$timestamp:$multiplier",
                timestamp.toDouble()
            )
            
            // Keep only last 7 days
            val cutoff = timestamp - (7 * 24 * 60 * 60 * 1000)
            redisTemplate.opsForZSet().removeRangeByScore(key, 0.0, cutoff.toDouble())
            
        } catch (e: Exception) {
            logger.error(e) { "Failed to record surge history" }
        }
    }
    
    private fun cleanupExpiredSurge() {
        val expiredCells = currentSurgeMultipliers.entries
            .filter { (h3Index, _) ->
                val key = "$SURGE_PRICING_KEY$h3Index"
                !redisTemplate.hasKey(key)
            }
            .map { it.key }
        
        expiredCells.forEach { h3Index ->
            currentSurgeMultipliers.remove(h3Index)
            
            // Send event to notify surge ended
            eventProducer.publishSurgePricingUpdate(
                h3Index = h3Index,
                multiplier = MIN_SURGE_MULTIPLIER,
                reason = "Surge pricing ended"
            )
        }
        
        if (expiredCells.isNotEmpty()) {
            logger.info { "Cleaned up surge pricing for ${expiredCells.size} cells" }
        }
    }
    
    data class SurgePricingData(
        val h3Index: String,
        val multiplier: Double,
        val startTime: Long,
        val expiryTime: Long,
        val reason: String
    ) {
        fun toJson(): String = """
            {
                "h3Index": "$h3Index",
                "multiplier": $multiplier,
                "startTime": $startTime,
                "expiryTime": $expiryTime,
                "reason": "$reason"
            }
        """.trimIndent()
    }
    
    data class DemandBaseline(
        val h3Index: String,
        val avgDemand: Double,
        val peakDemand: Double,
        val hourlyAverages: Map<Int, Double> = emptyMap(),
        val lastUpdated: Long
    ) {
        fun toJson(): String = """
            {
                "h3Index": "$h3Index",
                "avgDemand": $avgDemand,
                "peakDemand": $peakDemand,
                "hourlyAverages": ${hourlyAverages.entries.joinToString(",", "{", "}") { 
                    "\"${it.key}\": ${it.value}" 
                }},
                "lastUpdated": $lastUpdated
            }
        """.trimIndent()
        
        companion object {
            fun fromJson(json: String): DemandBaseline {
                // Simplified JSON parsing - in production use Jackson
                return DemandBaseline(
                    h3Index = "",
                    avgDemand = 10.0,
                    peakDemand = 15.0,
                    lastUpdated = System.currentTimeMillis()
                )
            }
        }
    }
}