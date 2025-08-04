package com.ddakta.matching.domain.vo

import jakarta.persistence.Embeddable
import java.io.Serializable

@Embeddable
data class Location(
    val latitude: Double,
    val longitude: Double,
    val address: String? = null,
    val h3Index: String
) : Serializable {
    
    init {
        require(latitude in -90.0..90.0) { "Latitude must be between -90 and 90" }
        require(longitude in -180.0..180.0) { "Longitude must be between -180 and 180" }
        require(h3Index.isNotBlank()) { "H3 index cannot be blank" }
    }
    
    fun distanceTo(other: Location): Double {
        val earthRadius = 6371.0 // Earth's radius in kilometers
        
        val lat1Rad = Math.toRadians(latitude)
        val lat2Rad = Math.toRadians(other.latitude)
        val deltaLatRad = Math.toRadians(other.latitude - latitude)
        val deltaLonRad = Math.toRadians(other.longitude - longitude)
        
        val a = Math.sin(deltaLatRad / 2) * Math.sin(deltaLatRad / 2) +
                Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                Math.sin(deltaLonRad / 2) * Math.sin(deltaLonRad / 2)
        
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        
        return earthRadius * c * 1000 // Return distance in meters
    }
}