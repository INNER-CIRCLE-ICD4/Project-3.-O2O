package com.ddakta.common.user.dto

data class VehicleInfo(
    val licensePlate: String,
    val model: String,
    val color: String,
    val type: VehicleType
)

enum class VehicleType {
    SEDAN,
    SUV,
    HATCHBACK,
    MINIVAN,
    LUXURY
}