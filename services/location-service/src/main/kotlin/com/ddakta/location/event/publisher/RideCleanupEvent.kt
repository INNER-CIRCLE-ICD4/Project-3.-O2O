package com.ddakta.location.event.publisher

data class RideCleanupEvent(
    val rideId: String,
    val driverId: String,
    val endedAt: Long
)
