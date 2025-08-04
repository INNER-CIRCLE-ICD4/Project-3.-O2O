package com.ddakta.matching.dto.request

data class FareEstimateDto(
    val baseFare: Double,
    val surgeMultiplier: Double = 1.0,
    val estimatedTotal: Double? = null,
    val currency: String = "KRW"
)