package com.ddakta.matching.dto.request

import jakarta.validation.constraints.NotNull

data class RequestLocationDto(
    @field:NotNull
    val latitude: Double,
    
    @field:NotNull
    val longitude: Double,
    
    val address: String? = null,
    
    @field:NotNull
    val h3Index: String
)