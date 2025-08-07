package com.ddakta.location.event.publisher

data class DriverStatusChangedEvent(
    val driverId: String,
    val status: String,
    val h3Index: String
)
