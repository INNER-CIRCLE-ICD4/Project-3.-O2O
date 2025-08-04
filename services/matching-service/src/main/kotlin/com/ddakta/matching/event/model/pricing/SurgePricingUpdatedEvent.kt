package com.ddakta.matching.event.model.pricing

import java.time.LocalDateTime

data class SurgePricingUpdatedEvent(
    val h3Index: String,
    val previousMultiplier: Double,
    val newMultiplier: Double,
    val reason: String, // DEMAND_INCREASE, SUPPLY_DECREASE, SCHEDULED, MANUAL
    val effectiveFrom: LocalDateTime,
    val effectiveTo: LocalDateTime?
)