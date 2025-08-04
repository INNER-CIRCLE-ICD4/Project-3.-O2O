package com.ddakta.matching.dto.request

import jakarta.validation.constraints.NotNull
import java.util.*

data class RideRequestDto(
    @field:NotNull
    val passengerId: UUID,
    
    @field:NotNull
    val pickupLocation: RequestLocationDto,
    
    @field:NotNull
    val dropoffLocation: RequestLocationDto,
    
    val vehicleType: String? = null,
    
    val paymentMethodId: String? = null,
    
    val estimatedFare: FareEstimateDto? = null
)