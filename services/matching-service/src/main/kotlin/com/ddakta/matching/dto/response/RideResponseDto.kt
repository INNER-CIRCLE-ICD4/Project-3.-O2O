package com.ddakta.matching.dto.response

import com.ddakta.matching.domain.entity.Ride
import com.ddakta.matching.domain.enum.CancellationReason
import com.ddakta.matching.domain.enum.RideStatus
import com.ddakta.matching.dto.request.LocationDto
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*

data class RideResponseDto(
    val id: UUID,
    val passengerId: UUID,
    val driverId: UUID?,
    val status: RideStatus,
    val pickupLocation: LocationDto,
    val dropoffLocation: LocationDto,
    val fare: FareDto?,
    val vehicleType: String?,
    val paymentMethodId: UUID?,
    val requestedAt: LocalDateTime,
    val matchedAt: LocalDateTime?,
    val pickedUpAt: LocalDateTime?,
    val completedAt: LocalDateTime?,
    val cancelledAt: LocalDateTime?,
    val cancellationReason: CancellationReason?,
    val cancelledBy: String?,
    val distanceMeters: Int?,
    val durationSeconds: Int?,
    val passengerRating: Int?,
    val driverRating: Int?,
    val updatedAt: LocalDateTime,
    val version: Long
) {
    companion object {
        fun from(ride: Ride): RideResponseDto {
            return RideResponseDto(
                id = ride.id!!,
                passengerId = ride.passengerId,
                driverId = ride.driverId,
                status = ride.status,
                pickupLocation = LocationDto(
                    latitude = ride.pickupLocation.latitude,
                    longitude = ride.pickupLocation.longitude,
                    address = ride.pickupLocation.address,
                    h3Index = ride.pickupLocation.h3Index
                ),
                dropoffLocation = LocationDto(
                    latitude = ride.dropoffLocation.latitude,
                    longitude = ride.dropoffLocation.longitude,
                    address = ride.dropoffLocation.address,
                    h3Index = ride.dropoffLocation.h3Index
                ),
                fare = ride.fare?.let {
                    FareDto(
                        baseFare = it.baseFare,
                        surgeMultiplier = it.surgeMultiplier,
                        totalFare = it.totalFare,
                        currency = it.currency
                    )
                },
                vehicleType = ride.vehicleType,
                paymentMethodId = ride.paymentMethodId,
                requestedAt = ride.requestedAt,
                matchedAt = ride.matchedAt,
                pickedUpAt = ride.pickedUpAt,
                completedAt = ride.completedAt,
                cancelledAt = ride.cancelledAt,
                cancellationReason = ride.cancellationReason,
                cancelledBy = ride.cancelledBy?.name,
                distanceMeters = ride.distanceMeters,
                durationSeconds = ride.durationSeconds,
                passengerRating = ride.ratingByPassenger,
                driverRating = ride.ratingByDriver,
                updatedAt = ride.updatedAt,
                version = ride.version
            )
        }
    }
}

data class FareDto(
    val baseFare: BigDecimal,
    val surgeMultiplier: BigDecimal,
    val totalFare: BigDecimal?,
    val currency: String
)