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

data class MatchedDriver(
    val driverId: UUID,
    val estimatedArrivalSeconds: Int,
    val score: Double,
    val rank: Int
)