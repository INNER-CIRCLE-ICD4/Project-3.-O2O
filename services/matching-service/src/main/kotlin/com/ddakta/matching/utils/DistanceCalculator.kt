package com.ddakta.matching.utils

import com.ddakta.matching.domain.vo.Location
import org.springframework.stereotype.Component
import kotlin.math.*

@Component
class DistanceCalculator {
    
    companion object {
        private const val EARTH_RADIUS_KM = 6371.0
        
        // TODO: 임시 추가 - 컴파일 오류 해결용
        fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val deltaLatRad = Math.toRadians(lat2 - lat1)
            val deltaLonRad = Math.toRadians(lon2 - lon1)
            val lat1Rad = Math.toRadians(lat1)
            val lat2Rad = Math.toRadians(lat2)
            
            val a = sin(deltaLatRad / 2) * sin(deltaLatRad / 2) +
                    cos(lat1Rad) * cos(lat2Rad) *
                    sin(deltaLonRad / 2) * sin(deltaLonRad / 2)
            
            val c = 2 * atan2(sqrt(a), sqrt(1 - a))
            
            return EARTH_RADIUS_KM * c * 1000 // 미터 단위로 반환
        }
    }
    
    /**
     * 하버사인 공식을 사용하여 두 위치 간의 거리 계산
     * @return 미터 단위의 거리
     */
    fun calculate(from: Location, to: Location): Double {
        val lat1Rad = Math.toRadians(from.latitude)
        val lat2Rad = Math.toRadians(to.latitude)
        val deltaLatRad = Math.toRadians(to.latitude - from.latitude)
        val deltaLonRad = Math.toRadians(to.longitude - from.longitude)
        
        val a = sin(deltaLatRad / 2) * sin(deltaLatRad / 2) +
                cos(lat1Rad) * cos(lat2Rad) *
                sin(deltaLonRad / 2) * sin(deltaLonRad / 2)
        
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        
        return EARTH_RADIUS_KM * c * 1000 // 미터 단위로 반환
    }
    
    /**
     * 거리와 평균 속도를 기반으로 예상 이동 시간 계산
     * @param distanceMeters 미터 단위의 거리
     * @param averageSpeedKmh km/h 단위의 평균 속도 (기본값: 도심 운전 30 km/h)
     * @return 초 단위의 예상 시간
     */
    fun estimateTravelTime(distanceMeters: Double, averageSpeedKmh: Double = 30.0): Int {
        val distanceKm = distanceMeters / 1000.0
        val timeHours = distanceKm / averageSpeedKmh
        return (timeHours * 3600).toInt()
    }
    
    /**
     * 한 위치가 다른 위치의 특정 반경 내에 있는지 확인
     */
    fun isWithinRadius(center: Location, point: Location, radiusMeters: Double): Boolean {
        return calculate(center, point) <= radiusMeters
    }
    
    /**
     * 한 위치에서 다른 위치로의 방위각(방향) 계산
     * @return 도 단위의 방위각 (0-360)
     */
    fun calculateBearing(from: Location, to: Location): Double {
        val lat1Rad = Math.toRadians(from.latitude)
        val lat2Rad = Math.toRadians(to.latitude)
        val deltaLonRad = Math.toRadians(to.longitude - from.longitude)
        
        val x = sin(deltaLonRad) * cos(lat2Rad)
        val y = cos(lat1Rad) * sin(lat2Rad) - sin(lat1Rad) * cos(lat2Rad) * cos(deltaLonRad)
        
        val bearing = atan2(x, y)
        
        return (Math.toDegrees(bearing) + 360) % 360
    }
}