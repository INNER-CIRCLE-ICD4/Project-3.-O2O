package com.ddakta.location.repository

import org.springframework.data.geo.Point
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Repository
import java.util.concurrent.TimeUnit

@Repository
class RedisGeoLocationRepository(
    private val redisTemplate: StringRedisTemplate
) {
    companion object {
        const val DRIVERS_GEO_KEY = "drivers:geo"
        const val DRIVER_LOCATION_HASH_KEY = "driver:location:"
        const val H3_DRIVERS_SET_KEY = "h3:drivers:"
        const val DRIVER_STATUS_KEY = "driver:status:"
        const val DRIVER_TTL_MINUTES = 5L // 드라이버 위치 및 상태 정보 TTL
    }

    /**
     * 드라이버의 위치를 업데이트하고, H3 인덱스 기반 Set을 관리합니다.
     * @param driverId 드라이버 ID
     * @param latitude 위도
     * @param longitude 경도
     * @param newH3Index 새로운 H3 인덱스
     * @param oldH3Index 이전 H3 인덱스 (위치 변경이 없는 경우 null)
     */
    fun updateLocation(
        driverId: String,
        latitude: Double,
        longitude: Double,
        newH3Index: String,
        oldH3Index: String?
    ) {
        val ops = redisTemplate.opsForGeo()
        val hashOps = redisTemplate.opsForHash()
        val setOps = redisTemplate.opsForSet()

        // 1. Geo Set 업데이트 (반경 검색용)
        ops.add(DRIVERS_GEO_KEY, Point(longitude, latitude), driverId)
        redisTemplate.expire(DRIVERS_GEO_KEY, DRIVER_TTL_MINUTES, TimeUnit.MINUTES)

        // 2. 드라이버별 상세 위치 정보 Hash 업데이트
        val driverLocationHash = mapOf(
            "latitude" to latitude.toString(),
            "longitude" to longitude.toString(),
            "h3Index" to newH3Index
        )
        hashOps.putAll(DRIVER_LOCATION_HASH_KEY + driverId, driverLocationHash)
        redisTemplate.expire(DRIVER_LOCATION_HASH_KEY + driverId, DRIVER_TTL_MINUTES, TimeUnit.MINUTES)

        // 3. H3 인덱스 기반 드라이버 Set 업데이트
        if (oldH3Index != null && oldH3Index != newH3Index) {
            setOps.remove(H3_DRIVERS_SET_KEY + oldH3Index, driverId)
        }
        setOps.add(H3_DRIVERS_SET_KEY + newH3Index, driverId)
        redisTemplate.expire(H3_DRIVERS_SET_KEY + newH3Index, DRIVER_TTL_MINUTES, TimeUnit.MINUTES)
    }

    /**
     * 특정 H3 인덱스에 속한 드라이버 ID 목록을 조회합니다.
     * @param h3Index H3 인덱스
     * @return 해당 H3 인덱스에 있는 드라이버 ID Set
     */
    fun getDriversInH3(h3Index: String): Set<String> {
        return redisTemplate.opsForSet().members(H3_DRIVERS_SET_KEY + h3Index) ?: emptySet()
    }

    /**
     * 드라이버의 현재 위치 정보를 조회합니다.
     * @param driverId 드라이버 ID
     * @return 위도, 경도, H3 인덱스를 포함하는 Map
     */
    fun getDriverLocation(driverId: String): Map<Any, Any>? {
        return redisTemplate.opsForHash().entries(DRIVER_LOCATION_HASH_KEY + driverId)
    }

    /**
     * 드라이버의 상태를 업데이트합니다.
     * @param driverId 드라이버 ID
     * @param status 드라이버 상태 (예: ONLINE, OFFLINE, BUSY)
     */
    fun updateDriverStatus(driverId: String, status: String) {
        redisTemplate.opsForValue().set(DRIVER_STATUS_KEY + driverId, status)
        redisTemplate.expire(DRIVER_STATUS_KEY + driverId, DRIVER_TTL_MINUTES, TimeUnit.MINUTES)
    }

    /**
     * 드라이버의 상태를 조회합니다.
     * @param driverId 드라이버 ID
     * @return 드라이버 상태 문자열
     */
    fun getDriverStatus(driverId: String): String? {
        return redisTemplate.opsForValue().get(DRIVER_STATUS_KEY + driverId)
    }

    /**
     * 드라이버의 위치 정보를 Redis에서 삭제합니다.
     * @param driverId 드라이버 ID
     * @param h3Index 드라이버가 마지막으로 있었던 H3 인덱스
     */
    fun removeDriverLocation(driverId: String, h3Index: String?) {
        redisTemplate.opsForGeo().remove(DRIVERS_GEO_KEY, driverId)
        redisTemplate.delete(DRIVER_LOCATION_HASH_KEY + driverId)
        if (h3Index != null) {
            redisTemplate.opsForSet().remove(H3_DRIVERS_SET_KEY + h3Index, driverId)
        }
    }
}