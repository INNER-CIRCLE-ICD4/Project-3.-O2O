package com.ddakta.matching.client.fallback

import com.ddakta.matching.client.LocationServiceClient
import com.ddakta.matching.domain.vo.Location
import com.ddakta.matching.dto.internal.AvailableDriver
import com.ddakta.matching.dto.internal.LocationInfo
import mu.KotlinLogging
import org.springframework.stereotype.Component
import java.util.*

@Component
class LocationServiceFallback(
    private val redisTemplate: org.springframework.data.redis.core.RedisTemplate<String, String>? = null
) : LocationServiceClient {
    
    private val logger = KotlinLogging.logger {}
    
    override fun findNearbyDrivers(
        h3Index: String,
        radiusKm: Double,
        limit: Int
    ): List<AvailableDriver> {
        logger.warn { "Location service unavailable, checking cache for nearby drivers" }
        
        // Redis 캐시에서 조회 시도
        redisTemplate?.let {
            val cachedDrivers = getCachedAvailableDrivers(h3Index)
            if (cachedDrivers.isNotEmpty()) {
                logger.info { "Returning ${cachedDrivers.size} cached drivers" }
                return cachedDrivers.take(limit)
            }
        }
        
        return emptyList()
    }
    
    override fun getAvailableDrivers(h3Indexes: List<String>): List<AvailableDriver> {
        logger.warn { "Location service unavailable, checking cache for available drivers" }
        
        redisTemplate?.let {
            val allDrivers = mutableListOf<AvailableDriver>()
            h3Indexes.forEach { h3Index ->
                allDrivers.addAll(getCachedAvailableDrivers(h3Index))
            }
            if (allDrivers.isNotEmpty()) {
                logger.info { "Returning ${allDrivers.size} cached drivers from ${h3Indexes.size} areas" }
                return allDrivers
            }
        }
        
        return emptyList()
    }
    
    override fun getAvailableDriverCount(h3Index: String): Int {
        logger.warn { "Location service unavailable, returning cached driver count" }
        
        redisTemplate?.let {
            val key = "drivers:available:$h3Index"
            return it.opsForSet().size(key)?.toInt() ?: 0
        }
        
        return 0
    }
    
    override fun getTripSummary(rideId: UUID): LocationServiceClient.TripSummary {
        logger.warn { "Location service unavailable, returning default trip summary" }
        return LocationServiceClient.TripSummary(
            rideId = rideId,
            distanceMeters = 0,
            durationSeconds = 0
        )
    }
    
    override fun getDriverLocation(driverId: UUID): LocationInfo? {
        logger.warn { "Location service unavailable, checking cache for driver location" }
        
        redisTemplate?.let {
            val key = "driver:location:$driverId"
            val locationData = it.opsForValue().get(key)
            if (locationData != null) {
                val parts = locationData.split(",")
                if (parts.size >= 3) {
                    return LocationInfo(
                        latitude = parts[0].toDouble(),
                        longitude = parts[1].toDouble(),
                        h3Index = parts[2]
                    )
                }
            }
        }
        
        return null
    }
    
    override fun getNeighboringH3Indexes(h3Index: String, ringSize: Int): List<String> {
        logger.warn { "Location service unavailable, returning only the provided H3 index" }
        // 폴백 모드에서는 기본 H3 인덱스만 반환
        return listOf(h3Index)
    }
    
    override fun calculateDistance(
        fromLat: Double,
        fromLng: Double,
        toLat: Double,
        toLng: Double
    ): LocationServiceClient.DistanceInfo {
        logger.warn { "Location service unavailable, calculating approximate distance" }
        
        // 폴백 거리 계산에 하버사인 공식 사용
        val earthRadius = 6371000.0 // 미터
        val dLat = Math.toRadians(toLat - fromLat)
        val dLng = Math.toRadians(toLng - fromLng)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(fromLat)) * Math.cos(Math.toRadians(toLat)) *
                Math.sin(dLng / 2) * Math.sin(dLng / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        val distance = (earthRadius * c).toInt()
        
        // 평균 속도 30km/h로 예상 시간 계산
        val estimatedDuration = (distance / 1000.0 * 120).toInt() // km당 2분
        
        return LocationServiceClient.DistanceInfo(
            distanceMeters = distance,
            estimatedDurationSeconds = estimatedDuration,
            trafficCondition = "UNKNOWN"
        )
    }
    
    private fun getCachedAvailableDrivers(h3Index: String): List<AvailableDriver> {
        val drivers = mutableListOf<AvailableDriver>()
        
        redisTemplate?.let { redis ->
            val key = "drivers:available:$h3Index"
            val driverIds = redis.opsForSet().members(key) ?: emptySet()
            
            driverIds.forEach { driverIdStr ->
                try {
                    val driverId = UUID.fromString(driverIdStr)
                    val locationKey = "driver:location:$driverId"
                    val locationData = redis.opsForValue().get(locationKey)
                    
                    if (locationData != null) {
                        val parts = locationData.split(",")
                        if (parts.size >= 3) {
                            drivers.add(
                                AvailableDriver(
                                    driverId = driverId,
                                    currentLocation = Location(
                                        latitude = parts[0].toDouble(),
                                        longitude = parts[1].toDouble(),
                                        h3Index = parts[2]
                                    ),
                                    rating = 4.5, // 폴백 기본 평점
                                    acceptanceRate = 0.8, // 폴백 기본 수락률
                                    completedTrips = 95 // 폴백 기본 완료 운행 수
                                )
                            )
                        }
                    }
                } catch (e: Exception) {
                    logger.error { "Error parsing cached driver data: ${e.message}" }
                }
            }
        }
        
        return drivers
    }
}