package com.ddakta.location.repository

import org.springframework.data.geo.Circle
import org.springframework.data.geo.Distance
import org.springframework.data.geo.Metrics
import org.springframework.data.geo.Point
import org.springframework.data.geo.GeoResults
import org.springframework.data.redis.connection.RedisGeoCommands
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Repository

@Repository
class RedisGeoLocationRepository(
    private val redisTemplate: StringRedisTemplate
) {
    private fun key() = "drivers:locations"

    // (1) 위치 갱신: GEO.ADD
    fun updateLocation(driverId: String, latitude: Double, longitude: Double) {
        redisTemplate.opsForGeo()
            .add(key(), Point(longitude, latitude), driverId)
    }

    // (2) 반경 검색: 미터 단위
    open fun findNearby(
        latitude: Double,
        longitude: Double,
        radiusInMeters: Double
    ): GeoResults<RedisGeoCommands.GeoLocation<String?>?>? {
        // (1) 미터 → 킬로미터 단위로 변환
        val radiusInKm = radiusInMeters / 1_000.0

        // (2) KILOMETERS 사용
        val circle = Circle(
            Point(longitude, latitude),
            Distance(radiusInKm, Metrics.KILOMETERS)
        )

        // (3) positional 호출
        return redisTemplate.opsForGeo()
            .search(key(), circle)
    }

    // (3) 위치 삭제: GEO.REMOVE
    fun removeLocation(driverId: String) {
        // 여기서도 named-arg 쓰지 말고 positional 호출
        redisTemplate.opsForGeo()
            .remove(key(), driverId)
    }
}
