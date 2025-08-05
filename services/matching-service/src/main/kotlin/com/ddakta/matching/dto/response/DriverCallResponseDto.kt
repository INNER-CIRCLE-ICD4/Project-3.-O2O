package com.ddakta.matching.dto.response

import com.ddakta.matching.domain.entity.DriverCall
import com.ddakta.matching.domain.enum.DriverCallStatus
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*

data class DriverCallResponseDto(
    val id: UUID,
    val rideId: UUID,
    val driverId: UUID,
    val status: DriverCallStatus,
    val estimatedArrivalSeconds: Int?,
    val estimatedFare: BigDecimal?,
    val expiresAt: LocalDateTime,
    val respondedAt: LocalDateTime?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val isExpired: Boolean
) {
    companion object {
        fun from(driverCall: DriverCall): DriverCallResponseDto {
            return DriverCallResponseDto(
                id = driverCall.id ?: throw IllegalArgumentException("Driver call ID cannot be null"),
                rideId = driverCall.ride.id ?: throw IllegalArgumentException("Ride ID cannot be null in driver call"),
                driverId = driverCall.driverId,
                status = driverCall.status,
                estimatedArrivalSeconds = driverCall.estimatedArrivalSeconds,
                estimatedFare = driverCall.estimatedFare,
                expiresAt = driverCall.expiresAt,
                respondedAt = driverCall.respondedAt,
                createdAt = driverCall.createdAt,
                updatedAt = driverCall.updatedAt,
                isExpired = driverCall.isExpired()
            )
        }
    }
}