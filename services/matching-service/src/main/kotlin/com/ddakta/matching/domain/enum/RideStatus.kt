package com.ddakta.matching.domain.enum

enum class RideStatus {
    REQUESTED,
    MATCHED,
    DRIVER_ASSIGNED,
    EN_ROUTE_TO_PICKUP,
    ARRIVED_AT_PICKUP,
    ON_TRIP,
    COMPLETED,
    CANCELLED,
    FAILED;

    fun canTransitionTo(newStatus: RideStatus): Boolean {
        return when (this) {
            REQUESTED -> newStatus in listOf(MATCHED, CANCELLED, FAILED)
            MATCHED -> newStatus in listOf(DRIVER_ASSIGNED, CANCELLED, FAILED)
            DRIVER_ASSIGNED -> newStatus in listOf(EN_ROUTE_TO_PICKUP, CANCELLED, FAILED)
            EN_ROUTE_TO_PICKUP -> newStatus in listOf(ARRIVED_AT_PICKUP, CANCELLED, FAILED)
            ARRIVED_AT_PICKUP -> newStatus in listOf(ON_TRIP, CANCELLED, FAILED)
            ON_TRIP -> newStatus in listOf(COMPLETED, CANCELLED, FAILED)
            COMPLETED -> false
            CANCELLED -> false
            FAILED -> false
        }
    }

    fun isCancellable(): Boolean {
        return this !in listOf(COMPLETED, CANCELLED, FAILED)
    }

    fun isActive(): Boolean {
        return this !in listOf(COMPLETED, CANCELLED, FAILED)
    }

    fun isTerminal(): Boolean {
        return this in listOf(COMPLETED, CANCELLED, FAILED)
    }
}