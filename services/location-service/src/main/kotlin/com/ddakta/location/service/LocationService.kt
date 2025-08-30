package com.ddakta.location.service

import com.ddakta.location.dto.NearbyDriverDto
import com.ddakta.location.repository.RedisGeoLocationRepository
import com.uber.h3core.H3Core
import mu.KotlinLogging
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class LocationService(
    private val redisGeoLocationRepository: RedisGeoLocationRepository
) {

    private val logger = KotlinLogging.logger {}
    private val h3 = H3Core.newInstance()

    /**
     * 드라이버의 위치를 업데이트하고 Redis에 저장합니다.
     * @param driverId 드라이버 ID
     * @param latitude 위도
     * @param longitude 경도
     * @param h3Index H3 인덱스
     * @param timestamp 위치 업데이트 시간
     */
    fun updateLocation(
        driverId: String,
        latitude: Double,
        longitude: Double,
        h3Index: String,
        timestamp: Long
    ) {
        // 이전 H3 인덱스 조회
        val oldLocation = redisGeoLocationRepository.getDriverLocation(driverId)
        val oldH3Index = oldLocation?.get("h3Index") as? String

        redisGeoLocationRepository.updateLocation(
            driverId, latitude, longitude, h3Index, oldH3Index
        )
        logger.debug { "Driver $driverId location updated to H3: $h3Index" }
    }

    /**
     * H3 인덱스 기반으로 주변 드라이버를 검색합니다.
     * @param h3Index 중심 H3 인덱스
     * @param kRingSize 검색할 인접 지역의 범위 (k-ring 크기)
     * @return 주변 드라이버 리스트
     */
    fun findDriversInH3Cells(h3Index: String, kRingSize: Int): List<NearbyDriverDto> {
        val nearbyH3Indexes = h3.kRing(h3Index, kRingSize)
        val nearbyDriverIds = mutableSetOf<String>()

        nearbyH3Indexes.forEach { h3Cell ->
            redisGeoLocationRepository.getDriversInH3(h3Cell).forEach { driverId ->
                // 드라이버가 ONLINE 상태인지 확인
                if (redisGeoLocationRepository.getDriverStatus(driverId) == "ONLINE") {
                    nearbyDriverIds.add(driverId)
                }
            }
        }

        val nearbyDrivers = mutableListOf<NearbyDriverDto>()
        nearbyDriverIds.forEach { driverId ->
            val locationData = redisGeoLocationRepository.getDriverLocation(driverId)
            if (locationData != null) {
                nearbyDrivers.add(
                    NearbyDriverDto(
                        driverId = UUID.fromString(driverId),
                        latitude = (locationData["latitude"] as String).toDouble(),
                        longitude = (locationData["longitude"] as String).toDouble()
                    )
                )
            }
        }
        logger.info { "Found ${nearbyDrivers.size} nearby drivers for H3 $h3Index (k=$kRingSize)" }
        return nearbyDrivers
    }

    /**
     * 드라이버의 상태를 업데이트합니다.
     * @param driverId 드라이버 ID
     * @param status 드라이버 상태 (예: ONLINE, OFFLINE, BUSY)
     */
    fun updateDriverStatus(driverId: String, status: String) {
        redisGeoLocationRepository.updateDriverStatus(driverId, status)
        logger.info { "Driver $driverId status updated to $status" }

        // OFFLINE 또는 BUSY 상태가 되면 위치 정보도 삭제
        if (status == "OFFLINE" || status == "BUSY") {
            val locationData = redisGeoLocationRepository.getDriverLocation(driverId)
            val h3Index = locationData?.get("h3Index") as? String
            redisGeoLocationRepository.removeDriverLocation(driverId, h3Index)
            logger.info { "Removed location data for driver $driverId due to status change" }
        }
    }
}