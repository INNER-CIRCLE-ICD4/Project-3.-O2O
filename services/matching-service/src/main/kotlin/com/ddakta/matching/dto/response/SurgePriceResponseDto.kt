package com.ddakta.matching.dto.response

import java.time.LocalDateTime

data class SurgePriceResponseDto(
    val h3Index: String,
    val multiplier: Double,
    val isActive: Boolean,
    val effectiveFrom: LocalDateTime? = null,
    val effectiveTo: LocalDateTime? = null
)