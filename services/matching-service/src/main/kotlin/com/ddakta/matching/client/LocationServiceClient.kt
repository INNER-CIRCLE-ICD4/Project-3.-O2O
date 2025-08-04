package com.ddakta.matching.client

import com.ddakta.matching.client.fallback.LocationServiceFallback
import com.ddakta.matching.dto.internal.AvailableDriver
import com.ddakta.matching.dto.internal.LocationInfo
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import java.util.*

@FeignClient(
    name = "location-service",
    url = "\${ddakta.location-service.url:http://location-service:8083}",
    fallback = LocationServiceFallback::class
)
interface LocationServiceClient {
    
    @GetMapping("/internal/drivers/nearby")
    fun findNearbyDrivers(
        @RequestParam h3Index: String,
        @RequestParam radiusKm: Double,
        @RequestParam limit: Int
    ): List<AvailableDriver>
    
    @GetMapping("/internal/drivers/available")
    fun getAvailableDrivers(
        @RequestParam h3Indexes: List<String>
    ): List<AvailableDriver>
    
    @GetMapping("/internal/drivers/count")
    fun getAvailableDriverCount(
        @RequestParam h3Index: String
    ): Int
    
    @GetMapping("/internal/rides/{rideId}/summary")
    fun getTripSummary(@PathVariable rideId: UUID): TripSummary
    
    @GetMapping("/internal/drivers/{driverId}/location")
    fun getDriverLocation(@PathVariable driverId: UUID): LocationInfo?
    
    @GetMapping("/internal/h3/{h3Index}/neighbors")
    fun getNeighboringH3Indexes(
        @PathVariable h3Index: String,
        @RequestParam(defaultValue = "1") ringSize: Int
    ): List<String>
    
    @GetMapping("/internal/distance")
    fun calculateDistance(
        @RequestParam fromLat: Double,
        @RequestParam fromLng: Double,
        @RequestParam toLat: Double,
        @RequestParam toLng: Double
    ): DistanceInfo
    
    data class TripSummary(
        val rideId: UUID,
        val distanceMeters: Int,
        val durationSeconds: Int,
        val route: List<LocationInfo> = emptyList()
    )
    
    data class DistanceInfo(
        val distanceMeters: Int,
        val estimatedDurationSeconds: Int,
        val trafficCondition: String? = null
    )
}