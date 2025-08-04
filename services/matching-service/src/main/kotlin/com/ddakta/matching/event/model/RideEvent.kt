package com.ddakta.matching.event.model

import com.ddakta.matching.domain.vo.Location
import com.ddakta.matching.dto.internal.LocationInfo
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*

data class RideRequestedEvent(
    val rideId: UUID,
    val passengerId: UUID,
    val pickupLocation: LocationDto,
    val dropoffLocation: LocationDto,
    val pickupH3: String,
    val requestedAt: LocalDateTime,
    val estimatedFare: BigDecimal?,
    val surgeMultiplier: Double
)

data class RideMatchedEvent(
    val rideId: UUID,
    val passengerId: UUID,
    val driverId: UUID,
    val matchedAt: LocalDateTime,
    val estimatedArrivalSeconds: Int?,
    val driverLocation: LocationDto?
)

data class RideStatusChangedEvent(
    val rideId: UUID,
    val passengerId: UUID,
    val driverId: UUID?,
    val previousStatus: String,
    val newStatus: String,
    val changedAt: LocalDateTime,
    val metadata: Map<String, Any>?
)

data class RideCancelledEvent(
    val rideId: UUID,
    val passengerId: UUID,
    val driverId: UUID?,
    val cancellationReason: String?,
    val cancelledBy: String?,
    val cancelledAt: LocalDateTime
)

data class RideCompletedEvent(
    val rideId: UUID,
    val passengerId: UUID,
    val driverId: UUID,
    val completedAt: LocalDateTime,
    val distanceMeters: Int?,
    val durationSeconds: Int?,
    val totalFare: BigDecimal?
)

data class DriverCallRequestEvent(
    val rideId: UUID,
    val drivers: List<DriverCallInfo>,
    val requestedAt: LocalDateTime
)

data class DriverCallInfo(
    val callId: UUID,
    val driverId: UUID,
    val expiresAt: LocalDateTime,
    val estimatedArrival: Int?,
    val estimatedFare: BigDecimal?
)

data class LocationDto(
    val latitude: Double,
    val longitude: Double,
    val address: String?,
    val h3Index: String
) {
    companion object {
        fun from(location: Location): LocationDto {
            return LocationDto(
                latitude = location.latitude,
                longitude = location.longitude,
                address = location.address,
                h3Index = location.h3Index
            )
        }
        
        fun from(locationInfo: LocationInfo): LocationDto {
            return LocationDto(
                latitude = locationInfo.latitude,
                longitude = locationInfo.longitude,
                address = null,
                h3Index = locationInfo.h3Index
            )
        }
    }
}