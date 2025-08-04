package com.ddakta.matching.domain.repository

import com.ddakta.matching.domain.entity.Ride
import com.ddakta.matching.domain.enum.RideStatus
import com.ddakta.matching.domain.repository.custom.RideRepositoryCustom
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.*
import jakarta.persistence.LockModeType

@Repository
interface RideRepository : JpaRepository<Ride, UUID>, RideRepositoryCustom {

    @Query("SELECT r FROM Ride r WHERE r.passengerId = :passengerId AND r.status IN :statuses")
    fun findByPassengerIdAndStatusIn(
        @Param("passengerId") passengerId: UUID,
        @Param("statuses") statuses: List<RideStatus>
    ): List<Ride>

    @Query("""
        SELECT r FROM Ride r
        WHERE r.passengerId = :passengerId
        AND r.status NOT IN ('COMPLETED', 'CANCELLED')
    """)
    fun findActiveRideByPassenger(@Param("passengerId") passengerId: UUID): Ride?

    @Query("""
        SELECT r FROM Ride r
        WHERE r.driverId = :driverId
        AND r.status NOT IN ('COMPLETED', 'CANCELLED')
    """)
    fun findActiveRideByDriver(@Param("driverId") driverId: UUID): Ride?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Ride r WHERE r.id = :id")
    fun findByIdWithLock(@Param("id") id: UUID): Ride?

    @Query("""
        SELECT r FROM Ride r
        WHERE r.status = :status
        AND r.requestedAt BETWEEN :startTime AND :endTime
    """)
    fun findByStatusAndRequestedAtBetween(
        @Param("status") status: RideStatus,
        @Param("startTime") startTime: LocalDateTime,
        @Param("endTime") endTime: LocalDateTime
    ): List<Ride>

    @Query("""
        SELECT COUNT(r) FROM Ride r
        WHERE r.pickupLocation.h3Index = :h3Index
        AND r.status = 'REQUESTED'
        AND r.requestedAt > :since
    """)
    fun countPendingRidesInArea(
        @Param("h3Index") h3Index: String,
        @Param("since") since: LocalDateTime
    ): Long

    @Query("""
        SELECT r FROM Ride r
        WHERE r.status = :status
        AND r.updatedAt < :threshold
    """)
    fun findStaleRides(
        @Param("status") status: RideStatus,
        @Param("threshold") threshold: LocalDateTime
    ): List<Ride>

    @Query("""
        SELECT r FROM Ride r
        JOIN FETCH r.driverCalls dc
        WHERE r.id = :rideId
    """)
    fun findByIdWithDriverCalls(@Param("rideId") rideId: UUID): Ride?

    @Query("""
        SELECT r FROM Ride r
        JOIN FETCH r.stateTransitions st
        WHERE r.id = :rideId
        ORDER BY st.createdAt ASC
    """)
    fun findByIdWithStateTransitions(@Param("rideId") rideId: UUID): Ride?

    @Query("""
        SELECT r FROM Ride r
        WHERE r.status = :status
        AND r.updatedAt < :cutoffDate
        ORDER BY r.updatedAt ASC
        LIMIT :limit
    """)
    fun findOldRidesByStatus(
        @Param("status") status: String,
        @Param("cutoffDate") cutoffDate: LocalDateTime,
        @Param("limit") limit: Int
    ): List<Ride>
}
