package com.ddakta.matching.dto.internal

import java.util.*

data class MatchedDriver(
    val driverId: UUID,
    val estimatedArrivalSeconds: Int,
    val score: Double,
    val rank: Int
)