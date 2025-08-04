package com.ddakta.matching.dto.response

import java.time.LocalDateTime
import java.util.*

data class RideLocationUpdateDto(
    val rideId: UUID,
    val driverId: UUID,
    val latitude: Double,
    val longitude: Double,
    val heading: Int? = null,
    val speed: Double? = null,
    val accuracy: Double? = null,
    val timestamp: LocalDateTime
)