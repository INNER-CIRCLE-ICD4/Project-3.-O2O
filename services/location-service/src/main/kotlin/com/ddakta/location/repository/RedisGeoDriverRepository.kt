package com.ddakta.location.repository

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Repository

@Repository
class RedisGeoDriverRepository(
    private val redisTemplate: StringRedisTemplate
) {
    private fun key(h3Index: String) = "drivers:available:$h3Index"

    fun addDriver(h3Index: String, driverId: String) =
        redisTemplate.opsForSet().add(key(h3Index), driverId)

    fun removeDriver(h3Index: String, driverId: String) =
        redisTemplate.opsForSet().remove(key(h3Index), driverId)

    fun getDriversInCell(h3Index: String): Set<String> =
        redisTemplate.opsForSet().members(key(h3Index)) ?: emptySet()
}
