package com.ddakta.matching.domain.repository

import com.ddakta.matching.domain.entity.DriverCall
import com.ddakta.matching.domain.enum.DriverCallStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.*

@Repository
interface DriverCallRepository : JpaRepository<DriverCall, UUID> {

    @Query("SELECT dc FROM DriverCall dc WHERE dc.ride.id = :rideId ORDER BY dc.sequenceNumber")
    fun findByRideId(@Param("rideId") rideId: UUID): List<DriverCall>

    @Query("SELECT dc FROM DriverCall dc WHERE dc.driverId = :driverId AND dc.status = :status")
    fun findByDriverIdAndStatus(
        @Param("driverId") driverId: UUID,
        @Param("status") status: DriverCallStatus
    ): List<DriverCall>

    @Query("""
        SELECT dc FROM DriverCall dc
        WHERE dc.driverId = :driverId
        AND dc.status = 'PENDING'
        AND dc.expiresAt > :now
    """)
    fun findActivePendingCallsForDriver(
        @Param("driverId") driverId: UUID,
        @Param("now") now: LocalDateTime = LocalDateTime.now()
    ): List<DriverCall>

    @Modifying
    @Query("""
        UPDATE DriverCall dc
        SET dc.status = 'CANCELLED'
        WHERE dc.ride.id = :rideId
        AND dc.status = 'PENDING'
        AND dc.driverId != :excludeDriverId
    """)
    fun cancelPendingCallsForRide(
        @Param("rideId") rideId: UUID,
        @Param("excludeDriverId") excludeDriverId: UUID
    ): Int

    @Modifying
    @Query("""
        UPDATE DriverCall dc
        SET dc.status = 'EXPIRED'
        WHERE dc.status = 'PENDING'
        AND dc.expiresAt < :now
    """)
    fun expireOldCalls(@Param("now") now: LocalDateTime = LocalDateTime.now()): Int

    @Query("""
        SELECT dc FROM DriverCall dc
        WHERE dc.ride.id = :rideId
        AND dc.status = 'ACCEPTED'
    """)
    fun findAcceptedCallForRide(@Param("rideId") rideId: UUID): DriverCall?

    @Query("""
        SELECT COUNT(dc) FROM DriverCall dc
        WHERE dc.driverId = :driverId
        AND dc.status = :status
        AND dc.respondedAt BETWEEN :startTime AND :endTime
    """)
    fun countDriverResponsesByStatus(
        @Param("driverId") driverId: UUID,
        @Param("status") status: DriverCallStatus,
        @Param("startTime") startTime: LocalDateTime,
        @Param("endTime") endTime: LocalDateTime
    ): Long

    @Query("""
        SELECT
            COUNT(CASE WHEN dc.status = 'ACCEPTED' THEN 1 END) * 1.0 /
            NULLIF(COUNT(dc), 0) as acceptanceRate
        FROM DriverCall dc
        WHERE dc.driverId = :driverId
        AND dc.offeredAt > :since
    """)
    fun calculateDriverAcceptanceRate(
        @Param("driverId") driverId: UUID,
        @Param("since") since: LocalDateTime
    ): Double?

    @Query("""
        SELECT dc FROM DriverCall dc
        JOIN FETCH dc.ride r
        WHERE dc.id = :id
    """)
    fun findByIdWithRide(@Param("id") id: UUID): DriverCall?
    
    // 락을 사용하여 드라이버 호출 조회
    @Query("""
        SELECT dc FROM DriverCall dc
        WHERE dc.id = :id
        FOR UPDATE
    """)
    fun findByIdWithLock(@Param("id") id: UUID): DriverCall?
    
    // 드라이버의 활성 호출 목록 조회
    @Query("""
        SELECT dc FROM DriverCall dc
        WHERE dc.driverId = :driverId
        AND dc.status = 'PENDING'
        AND dc.expiresAt > :now
        ORDER BY dc.createdAt DESC
    """)
    fun findActiveCallsByDriverId(
        @Param("driverId") driverId: UUID,
        @Param("now") now: LocalDateTime
    ): List<DriverCall>
    
    // 운행의 대기 중인 호출 목록 조회
    @Query("""
        SELECT dc FROM DriverCall dc
        WHERE dc.rideId = :rideId
        AND dc.status = 'PENDING'
        ORDER BY dc.createdAt
    """)
    fun findPendingCallsByRideId(@Param("rideId") rideId: UUID): List<DriverCall>
    
    // 드라이버 호출 통계 조회용 데이터 클래스
    interface DriverCallStats {
        val totalCalls: Long
        val acceptedCalls: Long
    }
    
    // 드라이버 호출 통계 조회
    @Query("""
        SELECT 
            COUNT(dc) as totalCalls,
            COUNT(CASE WHEN dc.status = 'ACCEPTED' THEN 1 END) as acceptedCalls
        FROM DriverCall dc
        WHERE dc.driverId = :driverId
        AND dc.createdAt >= :startDate
    """)
    fun getDriverCallStats(
        @Param("driverId") driverId: UUID,
        @Param("startDate") startDate: LocalDateTime
    ): DriverCallStats
}
