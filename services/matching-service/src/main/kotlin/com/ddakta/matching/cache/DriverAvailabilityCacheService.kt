package com.ddakta.matching.cache

import com.ddakta.matching.domain.vo.Location
import com.ddakta.matching.dto.internal.AvailableDriver
import com.ddakta.matching.dto.internal.LocationInfo
import com.fasterxml.jackson.databind.ObjectMapper
import mu.KotlinLogging
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.*
import java.util.concurrent.TimeUnit

@Service
class DriverAvailabilityCacheService(
    private val redisTemplate: RedisTemplate<String, String>,
    private val objectMapper: ObjectMapper
) {
    
    private val logger = KotlinLogging.logger {}
    
    companion object {
        // H3 기반 캐시 키
        const val DRIVERS_BY_H3_KEY = "drivers:h3:"
        const val DRIVER_LOCATION_KEY = "driver:location:"
        const val DRIVER_STATUS_KEY = "driver:status:"
        const val H3_DRIVER_COUNT_KEY = "h3:driver:count:"
        const val H3_DEMAND_KEY = "h3:demand:"
        
        // TTL 설정
        const val LOCATION_TTL_SECONDS = 300L // 5분
        const val STATUS_TTL_SECONDS = 600L // 10분
        const val COUNT_TTL_SECONDS = 60L // 1분
        
        // H3 해상도 (강남 지역 최적화)
        const val H3_RESOLUTION = 8 // 약 460m 육각형
        const val NEIGHBOR_RING_SIZE = 2 // 인접 2개 링까지 검색
    }
    
    fun cacheDriverLocation(driverId: UUID, location: LocationInfo, isAvailable: Boolean) {
        try {
            val locationKey = "$DRIVER_LOCATION_KEY$driverId"
            val locationData = DriverLocationData(
                location = location,
                isAvailable = isAvailable,
                lastUpdated = System.currentTimeMillis()
            )
            
            val json = objectMapper.writeValueAsString(locationData)
            redisTemplate.opsForValue().set(
                locationKey,
                json,
                Duration.ofSeconds(LOCATION_TTL_SECONDS)
            )
            
            // H3 인덱스별로 드라이버 추가/제거
            if (isAvailable) {
                addDriverToH3Cell(location.h3Index, driverId)
            } else {
                removeDriverFromH3Cell(location.h3Index, driverId)
            }
            
            // 드라이버 수 업데이트
            updateH3DriverCount(location.h3Index)
            
            logger.debug { "Cached driver $driverId location at ${location.h3Index}" }
            
        } catch (e: Exception) {
            logger.error(e) { "Failed to cache driver location for $driverId" }
        }
    }
    
    fun getAvailableDriversInH3Cells(h3Indexes: List<String>): List<AvailableDriver> {
        val drivers = mutableListOf<AvailableDriver>()
        
        try {
            // 각 H3 셀에서 드라이버 조회
            h3Indexes.forEach { h3Index ->
                val key = "$DRIVERS_BY_H3_KEY$h3Index"
                val driverIds = redisTemplate.opsForSet().members(key)
                
                driverIds?.forEach { driverId ->
                    try {
                        getDriverDetails(UUID.fromString(driverId))?.let { driver ->
                            drivers.add(driver)
                        }
                    } catch (e: Exception) {
                        logger.error { "Invalid driver ID format: $driverId" }
                    }
                }
            }
            
            logger.debug { "Found ${drivers.size} available drivers in ${h3Indexes.size} H3 cells" }
            
        } catch (e: Exception) {
            logger.error(e) { "Error getting available drivers from H3 cells" }
        }
        
        return drivers
    }
    
    fun getCachedDriverLocation(driverId: UUID): LocationInfo? {
        return try {
            val key = "$DRIVER_LOCATION_KEY$driverId"
            val json = redisTemplate.opsForValue().get(key)
            
            if (json != null) {
                val locationData = objectMapper.readValue(json, DriverLocationData::class.java)
                
                // 위치 데이터가 너무 오래된 경우 null 반환
                val age = System.currentTimeMillis() - locationData.lastUpdated
                if (age > LOCATION_TTL_SECONDS * 1000) {
                    logger.debug { "Driver $driverId location data is stale" }
                    return null
                }
                
                return locationData.location
            }
            
            null
            
        } catch (e: Exception) {
            logger.error(e) { "Error getting cached driver location for $driverId" }
            null
        }
    }
    
    fun updateDriverAvailability(driverId: UUID, h3Index: String, isAvailable: Boolean) {
        try {
            val statusKey = "$DRIVER_STATUS_KEY$driverId"
            val status = if (isAvailable) "AVAILABLE" else "UNAVAILABLE"
            
            redisTemplate.opsForValue().set(
                statusKey,
                status,
                Duration.ofSeconds(STATUS_TTL_SECONDS)
            )
            
            if (isAvailable) {
                addDriverToH3Cell(h3Index, driverId)
            } else {
                removeDriverFromH3Cell(h3Index, driverId)
            }
            
            updateH3DriverCount(h3Index)
            
            logger.debug { "Updated driver $driverId availability to $status" }
            
        } catch (e: Exception) {
            logger.error(e) { "Failed to update driver availability for $driverId" }
        }
    }
    
    fun getH3CellDriverCount(h3Index: String): Int {
        return try {
            val key = "$H3_DRIVER_COUNT_KEY$h3Index"
            redisTemplate.opsForValue().get(key)?.toIntOrNull() ?: 0
        } catch (e: Exception) {
            logger.error(e) { "Error getting driver count for H3 cell $h3Index" }
            0
        }
    }
    
    fun recordH3Demand(h3Index: String) {
        try {
            val key = "$H3_DEMAND_KEY$h3Index"
            val currentHour = System.currentTimeMillis() / (1000 * 60 * 60)
            val member = currentHour.toString()
            
            // 시간별 수요를 Sorted Set으로 저장
            redisTemplate.opsForZSet().incrementScore(key, member, 1.0)
            redisTemplate.expire(key, Duration.ofDays(7))
            
            logger.debug { "Recorded demand for H3 cell $h3Index" }
            
        } catch (e: Exception) {
            logger.error(e) { "Failed to record demand for H3 cell $h3Index" }
        }
    }
    
    fun getH3DemandHistory(h3Index: String, hours: Int = 24): Map<Long, Int> {
        return try {
            val key = "$H3_DEMAND_KEY$h3Index"
            val currentHour = System.currentTimeMillis() / (1000 * 60 * 60)
            val startHour = currentHour - hours
            
            val scores = redisTemplate.opsForZSet()
                .rangeByScoreWithScores(key, startHour.toDouble(), currentHour.toDouble())
            
            scores?.associate { typedTuple ->
                val hour = typedTuple.value?.toLongOrNull() ?: 0L
                val count = typedTuple.score?.toInt() ?: 0
                hour to count
            } ?: emptyMap()
            
        } catch (e: Exception) {
            logger.error(e) { "Error getting demand history for H3 cell $h3Index" }
            emptyMap()
        }
    }
    
    fun invalidateH3Cell(h3Index: String) {
        try {
            val keys = listOf(
                "$DRIVERS_BY_H3_KEY$h3Index",
                "$H3_DRIVER_COUNT_KEY$h3Index"
            )
            
            redisTemplate.delete(keys)
            logger.debug { "Invalidated cache for H3 cell $h3Index" }
            
        } catch (e: Exception) {
            logger.error(e) { "Failed to invalidate H3 cell $h3Index" }
        }
    }
    
    fun getH3CellStats(): Map<String, H3CellStats> {
        val stats = mutableMapOf<String, H3CellStats>()
        
        try {
            // 모든 H3 셀 조회
            val h3Keys = redisTemplate.keys("$DRIVERS_BY_H3_KEY*")
            
            h3Keys.forEach { key ->
                val h3Index = key.removePrefix(DRIVERS_BY_H3_KEY)
                val driverCount = redisTemplate.opsForSet().size(key) ?: 0
                val demandKey = "$H3_DEMAND_KEY$h3Index"
                val recentDemand = redisTemplate.opsForZSet().zCard(demandKey) ?: 0
                
                stats[h3Index] = H3CellStats(
                    h3Index = h3Index,
                    availableDrivers = driverCount.toInt(),
                    recentDemand = recentDemand.toInt()
                )
            }
            
        } catch (e: Exception) {
            logger.error(e) { "Error getting H3 cell stats" }
        }
        
        return stats
    }
    
    private fun addDriverToH3Cell(h3Index: String, driverId: UUID) {
        val key = "$DRIVERS_BY_H3_KEY$h3Index"
        redisTemplate.opsForSet().add(key, driverId.toString())
        redisTemplate.expire(key, Duration.ofSeconds(LOCATION_TTL_SECONDS))
    }
    
    private fun removeDriverFromH3Cell(h3Index: String, driverId: UUID) {
        val key = "$DRIVERS_BY_H3_KEY$h3Index"
        redisTemplate.opsForSet().remove(key, driverId.toString())
    }
    
    private fun updateH3DriverCount(h3Index: String) {
        val driversKey = "$DRIVERS_BY_H3_KEY$h3Index"
        val countKey = "$H3_DRIVER_COUNT_KEY$h3Index"
        
        val count = redisTemplate.opsForSet().size(driversKey) ?: 0
        redisTemplate.opsForValue().set(
            countKey,
            count.toString(),
            Duration.ofSeconds(COUNT_TTL_SECONDS)
        )
    }
    
    private fun getDriverDetails(driverId: UUID): AvailableDriver? {
        return try {
            val locationKey = "$DRIVER_LOCATION_KEY$driverId"
            val json = redisTemplate.opsForValue().get(locationKey)
            
            if (json != null) {
                val locationData = objectMapper.readValue(json, DriverLocationData::class.java)
                
                if (locationData.isAvailable) {
                    // 캐시된 드라이버 정보로 AvailableDriver 생성
                    return AvailableDriver(
                        driverId = driverId,
                        currentLocation = Location(
                            latitude = locationData.location.latitude,
                            longitude = locationData.location.longitude,
                            h3Index = locationData.location.h3Index
                        ),
                        rating = 4.5, // 기본값 또는 다른 소스에서 가져오기
                        acceptanceRate = 0.8,
                        completedTrips = 95
                    )
                }
            }
            
            null
            
        } catch (e: Exception) {
            logger.error(e) { "Error getting driver details for $driverId" }
            null
        }
    }
    
    data class DriverLocationData(
        val location: LocationInfo,
        val isAvailable: Boolean,
        val lastUpdated: Long
    )
    
    data class H3CellStats(
        val h3Index: String,
        val availableDrivers: Int,
        val recentDemand: Int
    )
}