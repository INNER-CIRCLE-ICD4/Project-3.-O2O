package com.ddakta.matching.domain.repository

import com.ddakta.matching.domain.entity.RideStateTransition
import com.ddakta.matching.domain.enum.RideStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.*

@Repository
interface RideStateTransitionRepository : JpaRepository<RideStateTransition, UUID> {

    @Query("SELECT rst FROM RideStateTransition rst WHERE rst.ride.id = :rideId ORDER BY rst.createdAt ASC")
    fun findByRideId(@Param("rideId") rideId: UUID): List<RideStateTransition>

    @Query("""
        SELECT rst FROM RideStateTransition rst
        WHERE rst.ride.id = :rideId
        AND rst.toStatus = :status
        ORDER BY rst.createdAt DESC
    """)
    fun findByRideIdAndToStatus(
        @Param("rideId") rideId: UUID,
        @Param("status") status: RideStatus
    ): RideStateTransition?

    @Query("""
        SELECT rst FROM RideStateTransition rst
        WHERE rst.createdAt BETWEEN :startTime AND :endTime
        ORDER BY rst.createdAt DESC
    """)
    fun findTransitionsInTimeRange(
        @Param("startTime") startTime: LocalDateTime,
        @Param("endTime") endTime: LocalDateTime
    ): List<RideStateTransition>

    @Query("""
        SELECT rst.toStatus, COUNT(rst)
        FROM RideStateTransition rst
        WHERE rst.fromStatus = :fromStatus
        AND rst.createdAt > :since
        GROUP BY rst.toStatus
    """)
    fun countTransitionsFromStatus(
        @Param("fromStatus") fromStatus: RideStatus,
        @Param("since") since: LocalDateTime
    ): List<Array<Any>>
}
