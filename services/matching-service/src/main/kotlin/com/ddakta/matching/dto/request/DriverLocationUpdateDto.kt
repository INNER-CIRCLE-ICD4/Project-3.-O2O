package com.ddakta.matching.dto.request

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import java.time.LocalDateTime

data class DriverLocationUpdateDto(
    @field:NotNull
    @field:Min(-90)
    @field:Max(90)
    val latitude: Double,
    
    @field:NotNull
    @field:Min(-180)
    @field:Max(180)
    val longitude: Double,
    
    @field:Min(0)
    @field:Max(360)
    val heading: Int? = null,
    
    @field:Min(0)
    val speed: Double? = null,
    
    @field:Min(0)
    val accuracy: Double? = null,
    
    val timestamp: LocalDateTime = LocalDateTime.now()
)