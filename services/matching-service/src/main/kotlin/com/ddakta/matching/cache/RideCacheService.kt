package com.ddakta.matching.cache

import com.ddakta.matching.domain.entity.Ride
import com.ddakta.matching.dto.response.RideResponseDto
import com.fasterxml.jackson.databind.ObjectMapper
import mu.KotlinLogging
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.*
import java.util.concurrent.TimeUnit

@Service
class RideCacheService(
    private val redisTemplate: RedisTemplate<String, String>,
    private val objectMapper: ObjectMapper
) {
    
    private val logger = KotlinLogging.logger {}
    
    companion object {
        // Cache keys
        const val RIDE_KEY = "ride:"
        const val ACTIVE_RIDE_BY_PASSENGER_KEY = "ride:active:passenger:"
        const val ACTIVE_RIDE_BY_DRIVER_KEY = "ride:active:driver:"
        const val RIDE_HISTORY_KEY = "ride:history:"
        const val RIDE_STATE_KEY = "ride:state:"
        
        // TTL strategies based on ride status
        val STATUS_TTL_MAP = mapOf(
            "REQUESTED" to Duration.ofMinutes(5),
            "MATCHED" to Duration.ofMinutes(10),
            "DRIVER_ASSIGNED" to Duration.ofMinutes(15),
            "EN_ROUTE_TO_PICKUP" to Duration.ofMinutes(20),
            "ARRIVED_AT_PICKUP" to Duration.ofMinutes(10),
            "ON_TRIP" to Duration.ofHours(2),
            "COMPLETED" to Duration.ofDays(7),
            "CANCELLED" to Duration.ofHours(1),
            "FAILED" to Duration.ofHours(1)
        )
        
        const val DEFAULT_TTL_MINUTES = 60L
        const val CACHE_VERSION = "v1"
    }
    
    fun cacheRide(ride: Ride, response: RideResponseDto? = null) {
        try {
            val key = "$RIDE_KEY${ride.id}"
            val ttl = STATUS_TTL_MAP[ride.status.name] ?: Duration.ofMinutes(DEFAULT_TTL_MINUTES)
            
            val cacheData = RideCacheData(
                ride = response ?: RideResponseDto.from(ride),
                version = CACHE_VERSION,
                cachedAt = System.currentTimeMillis()
            )
            
            val json = objectMapper.writeValueAsString(cacheData)
            redisTemplate.opsForValue().set(key, json, ttl)
            
            // Update status-specific caches
            updateStatusCaches(ride)
            
            // Cache state for state machine
            cacheRideState(ride.id!!, ride.status.name)
            
            logger.debug { "Cached ride ${ride.id} with TTL ${ttl.toMinutes()} minutes" }
            
        } catch (e: Exception) {
            logger.error(e) { "Failed to cache ride ${ride.id}" }
        }
    }
    
    fun getCachedRide(rideId: UUID): RideResponseDto? {
        return try {
            val key = "$RIDE_KEY$rideId"
            val json = redisTemplate.opsForValue().get(key)
            
            if (json != null) {
                val cacheData = objectMapper.readValue(json, RideCacheData::class.java)
                
                // Check cache version
                if (cacheData.version == CACHE_VERSION) {
                    logger.debug { "Cache hit for ride $rideId" }
                    return cacheData.ride
                }
            }
            
            logger.debug { "Cache miss for ride $rideId" }
            null
            
        } catch (e: Exception) {
            logger.error(e) { "Error retrieving cached ride $rideId" }
            null
        }
    }
    
    fun evictRide(rideId: UUID) {
        try {
            val keys = mutableListOf<String>()
            keys.add("$RIDE_KEY$rideId")
            keys.add("$RIDE_STATE_KEY$rideId")
            
            // Find and remove from active ride caches
            redisTemplate.keys("$ACTIVE_RIDE_BY_PASSENGER_KEY*").forEach { key ->
                val cachedRideId = redisTemplate.opsForValue().get(key)
                if (cachedRideId == rideId.toString()) {
                    keys.add(key)
                }
            }
            
            redisTemplate.keys("$ACTIVE_RIDE_BY_DRIVER_KEY*").forEach { key ->
                val cachedRideId = redisTemplate.opsForValue().get(key)
                if (cachedRideId == rideId.toString()) {
                    keys.add(key)
                }
            }
            
            if (keys.isNotEmpty()) {
                redisTemplate.delete(keys)
                logger.debug { "Evicted ${keys.size} cache entries for ride $rideId" }
            }
            
        } catch (e: Exception) {
            logger.error(e) { "Error evicting cache for ride $rideId" }
        }
    }
    
    fun cacheActiveRide(ride: Ride) {
        try {
            if (ride.isActive()) {
                // Cache for passenger
                val passengerKey = "$ACTIVE_RIDE_BY_PASSENGER_KEY${ride.passengerId}"
                redisTemplate.opsForValue().set(
                    passengerKey,
                    ride.id.toString(),
                    Duration.ofHours(2)
                )
                
                // Cache for driver if assigned
                ride.driverId?.let { driverId ->
                    val driverKey = "$ACTIVE_RIDE_BY_DRIVER_KEY$driverId"
                    redisTemplate.opsForValue().set(
                        driverKey,
                        ride.id.toString(),
                        Duration.ofHours(2)
                    )
                }
                
                logger.debug { "Cached active ride ${ride.id} for quick lookup" }
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to cache active ride ${ride.id}" }
        }
    }
    
    fun getCachedActiveRideId(userId: UUID, isDriver: Boolean): UUID? {
        return try {
            val key = if (isDriver) {
                "$ACTIVE_RIDE_BY_DRIVER_KEY$userId"
            } else {
                "$ACTIVE_RIDE_BY_PASSENGER_KEY$userId"
            }
            
            val rideId = redisTemplate.opsForValue().get(key)
            rideId?.let { UUID.fromString(it) }
            
        } catch (e: Exception) {
            logger.error(e) { "Error getting cached active ride for user $userId" }
            null
        }
    }
    
    fun cacheRideHistory(userId: UUID, rides: List<RideResponseDto>, isDriver: Boolean) {
        try {
            val key = "$RIDE_HISTORY_KEY${if (isDriver) "driver" else "passenger"}:$userId"
            val json = objectMapper.writeValueAsString(rides)
            
            redisTemplate.opsForValue().set(key, json, Duration.ofMinutes(30))
            
            logger.debug { "Cached ${rides.size} rides in history for user $userId" }
            
        } catch (e: Exception) {
            logger.error(e) { "Failed to cache ride history for user $userId" }
        }
    }
    
    fun getCachedRideHistory(userId: UUID, isDriver: Boolean): List<RideResponseDto>? {
        return try {
            val key = "$RIDE_HISTORY_KEY${if (isDriver) "driver" else "passenger"}:$userId"
            val json = redisTemplate.opsForValue().get(key)
            
            if (json != null) {
                val type = objectMapper.typeFactory.constructCollectionType(
                    List::class.java,
                    RideResponseDto::class.java
                )
                return objectMapper.readValue(json, type)
            }
            
            null
            
        } catch (e: Exception) {
            logger.error(e) { "Error getting cached ride history for user $userId" }
            null
        }
    }
    
    fun warmUpCache(rides: List<Ride>) {
        logger.info { "Warming up cache with ${rides.size} rides" }
        
        rides.forEach { ride ->
            try {
                cacheRide(ride)
            } catch (e: Exception) {
                logger.error(e) { "Failed to warm cache for ride ${ride.id}" }
            }
        }
    }
    
    private fun updateStatusCaches(ride: Ride) {
        when {
            ride.isActive() -> cacheActiveRide(ride)
            ride.status.name in listOf("COMPLETED", "CANCELLED", "FAILED") -> {
                // Remove from active caches
                removeFromActiveCaches(ride)
            }
        }
    }
    
    private fun removeFromActiveCaches(ride: Ride) {
        val keys = mutableListOf<String>()
        keys.add("$ACTIVE_RIDE_BY_PASSENGER_KEY${ride.passengerId}")
        ride.driverId?.let { driverId ->
            keys.add("$ACTIVE_RIDE_BY_DRIVER_KEY$driverId")
        }
        redisTemplate.delete(keys)
    }
    
    private fun cacheRideState(rideId: UUID, state: String) {
        val key = "$RIDE_STATE_KEY$rideId"
        redisTemplate.opsForValue().set(key, state, Duration.ofHours(1))
    }
    
    fun getCachedRideState(rideId: UUID): String? {
        val key = "$RIDE_STATE_KEY$rideId"
        return redisTemplate.opsForValue().get(key)
    }
    
    fun getCacheStats(): CacheStats {
        val totalKeys = redisTemplate.keys("$RIDE_KEY*").size
        val activeRides = redisTemplate.keys("${ACTIVE_RIDE_BY_PASSENGER_KEY}*").size + 
                         redisTemplate.keys("${ACTIVE_RIDE_BY_DRIVER_KEY}*").size
        val historyEntries = redisTemplate.keys("$RIDE_HISTORY_KEY*").size
        
        return CacheStats(
            totalRides = totalKeys,
            activeRides = activeRides,
            historyEntries = historyEntries,
            cacheVersion = CACHE_VERSION
        )
    }
    
    data class RideCacheData(
        val ride: RideResponseDto,
        val version: String,
        val cachedAt: Long
    )
    
    data class CacheStats(
        val totalRides: Int,
        val activeRides: Int,
        val historyEntries: Int,
        val cacheVersion: String
    )
}