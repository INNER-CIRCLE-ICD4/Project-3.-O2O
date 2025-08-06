package com.ddakta.location.repository

import org.springframework.data.geo.Point
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Repository

@Repository
class RedisGeoLocationRepository(
    private val redisTemplate: StringRedisTemplate
) {
    private fun key() = "drivers:locations"

    fun updateLocation(driverId: String, latitude: Double, longitude: Double) {
        redisTemplate.opsForGeo()
            .add(key(), Point(longitude, latitude), driverId)
    }
}
