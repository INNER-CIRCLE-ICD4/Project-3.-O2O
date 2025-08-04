package com.ddakta.matching.dto.response

import java.util.*

data class DriverStatsResponseDto(
    val driverId: UUID,
    val acceptanceRate: Double,
    val acceptanceRatePercentage: String
)