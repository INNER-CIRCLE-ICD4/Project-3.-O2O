package com.ddakta.matching.event.model.location

data class TrafficIncident(
    val type: String, // ACCIDENT, CONSTRUCTION, EVENT
    val severity: String, // MINOR, MAJOR, CRITICAL
    val location: TrafficLocationInfo,
    val estimatedDelay: Int? // seconds
)