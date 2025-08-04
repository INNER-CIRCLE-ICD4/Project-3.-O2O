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
     * Calculate distance between two locations using Haversine formula
     * @return distance in meters
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
        
        return EARTH_RADIUS_KM * c * 1000 // Return in meters
    }
    
    /**
     * Calculate estimated travel time based on distance and average speed
     * @param distanceMeters distance in meters
     * @param averageSpeedKmh average speed in km/h (default: 30 km/h for city driving)
     * @return estimated time in seconds
     */
    fun estimateTravelTime(distanceMeters: Double, averageSpeedKmh: Double = 30.0): Int {
        val distanceKm = distanceMeters / 1000.0
        val timeHours = distanceKm / averageSpeedKmh
        return (timeHours * 3600).toInt()
    }
    
    /**
     * Check if a location is within a certain radius of another location
     */
    fun isWithinRadius(center: Location, point: Location, radiusMeters: Double): Boolean {
        return calculate(center, point) <= radiusMeters
    }
    
    /**
     * Calculate bearing (direction) from one location to another
     * @return bearing in degrees (0-360)
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