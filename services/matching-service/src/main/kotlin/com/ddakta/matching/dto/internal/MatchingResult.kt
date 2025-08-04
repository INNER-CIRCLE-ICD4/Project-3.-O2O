package com.ddakta.matching.dto.internal

import java.util.*

data class MatchingResult(
    val rideId: UUID,
    val matchedDrivers: List<MatchedDriver>,
    val success: Boolean,
    val reason: String? = null,
    val matchingScore: Double? = null,
    val processingTimeMs: Long = 0
)