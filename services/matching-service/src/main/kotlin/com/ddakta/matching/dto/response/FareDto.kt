package com.ddakta.matching.dto.response

import java.math.BigDecimal

data class FareDto(
    val baseFare: BigDecimal,
    val surgeMultiplier: BigDecimal,
    val totalFare: BigDecimal?,
    val currency: String
)