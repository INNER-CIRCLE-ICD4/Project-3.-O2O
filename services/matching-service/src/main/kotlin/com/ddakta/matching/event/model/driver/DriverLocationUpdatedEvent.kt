package com.ddakta.matching.event.model.driver

import java.time.LocalDateTime
import java.util.*

data class DriverLocationUpdatedEvent(
    val driverId: UUID,
    val latitude: Double,
    val longitude: Double,
    val h3Index: String,
    val heading: Int?,
    val speed: Double?,
    val accuracy: Double?,
    val timestamp: LocalDateTime,
    val isOnline: Boolean
)