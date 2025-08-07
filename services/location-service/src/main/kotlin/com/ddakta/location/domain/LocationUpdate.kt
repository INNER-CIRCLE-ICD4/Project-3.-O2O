package com.ddakta.location.domain

data class LocationUpdate(
    val driverId: String,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long
)
