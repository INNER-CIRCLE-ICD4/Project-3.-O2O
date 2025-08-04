package com.ddakta.matching.dto.request

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull

data class RideRatingRequest(
    @field:NotNull
    @field:Min(1)
    @field:Max(5)
    val rating: Int,
    
    @field:NotNull
    val isPassengerRating: Boolean,
    
    val comment: String? = null
)