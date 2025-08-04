package com.ddakta.matching.dto.request

import jakarta.validation.constraints.NotNull
import java.util.*

data class RideRequestDto(
    @field:NotNull
    val passengerId: UUID,
    
    @field:NotNull
    val pickupLocation: LocationDto,
    
    @field:NotNull
    val dropoffLocation: LocationDto,
    
    val vehicleType: String? = null,
    
    val paymentMethodId: String? = null,
    
    val estimatedFare: FareEstimateDto? = null
)

data class LocationDto(
    @field:NotNull
    val latitude: Double,
    
    @field:NotNull
    val longitude: Double,
    
    val address: String? = null,
    
    @field:NotNull
    val h3Index: String
)

data class FareEstimateDto(
    val baseFare: Double,
    val surgeMultiplier: Double = 1.0,
    val estimatedTotal: Double? = null,
    val currency: String = "KRW"
)