package com.ddakta.matching.event.model.location

import java.time.LocalDateTime

data class TrafficConditionsUpdatedEvent(
    val h3Index: String,
    val congestionLevel: String, // LOW, MEDIUM, HIGH, SEVERE
    val averageSpeed: Double,
    val incidents: List<TrafficIncident>?,
    val updatedAt: LocalDateTime
)