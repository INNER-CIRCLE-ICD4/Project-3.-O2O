package com.ddakta.matching.event.model.driver

import java.time.LocalDateTime
import java.util.*

data class DriverAvailabilityChangedEvent(
    val driverId: UUID,
    val isAvailable: Boolean,
    val h3Index: String?,
    val reason: String?,
    val changedAt: LocalDateTime
)