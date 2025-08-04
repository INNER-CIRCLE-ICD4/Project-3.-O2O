package com.ddakta.matching.event.model.driver

import java.time.LocalDateTime
import java.util.*

data class DriverStatusChangedEvent(
    val driverId: UUID,
    val previousStatus: String,
    val newStatus: String,
    val h3Index: String?,
    val changedAt: LocalDateTime
)