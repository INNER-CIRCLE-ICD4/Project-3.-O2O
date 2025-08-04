package com.ddakta.matching.dto.internal

import java.time.LocalDateTime

data class LocationInfo(
    val latitude: Double,
    val longitude: Double,
    val h3Index: String,
    val heading: Double? = null,
    val speed: Double? = null,
    val accuracy: Double? = null,
    val timestamp: LocalDateTime = LocalDateTime.now()
)