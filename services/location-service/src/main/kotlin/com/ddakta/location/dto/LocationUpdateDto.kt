package com.ddakta.location.dto

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull

data class LocationUpdateDto(
    @field:NotNull @field:Min(-90) @field:Max(90)
    val latitude: Double?,

    @field:NotNull @field:Min(-180) @field:Max(180)
    val longitude: Double?,

    @field:NotNull
    val timestamp: Long?
)
