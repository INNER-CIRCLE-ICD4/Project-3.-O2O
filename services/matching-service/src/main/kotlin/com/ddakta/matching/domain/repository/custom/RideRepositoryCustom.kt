package com.ddakta.matching.domain.repository.custom

import com.ddakta.matching.domain.entity.Ride
import com.ddakta.matching.domain.enum.RideStatus
import java.time.LocalDateTime
import java.util.*

interface RideRepositoryCustom {
    fun findRidesForMatching(
        h3Indexes: List<String>,
        maxAge: LocalDateTime,
        limit: Int
    ): List<Ride>

    fun findCompletedRidesInTimeRange(
        startTime: LocalDateTime,
        endTime: LocalDateTime,
        passengerId: UUID? = null,
        driverId: UUID? = null
    ): List<Ride>

    fun updateRideStatusBulk(
        rideIds: List<UUID>,
        newStatus: RideStatus
    ): Int

    fun findRidesNearLocation(
        h3Index: String,
        neighboringH3Indexes: List<String>,
        statuses: List<RideStatus>,
        limit: Int
    ): List<Ride>

    fun getRideStatistics(
        h3Index: String,
        timeWindow: LocalDateTime
    ): RideStatistics
}

data class RideStatistics(
    val totalRides: Long,
    val completedRides: Long,
    val cancelledRides: Long,
    val averageWaitTime: Double,
    val averageTripDuration: Double,
    val demandCount: Int,
    val supplyCount: Int
)
