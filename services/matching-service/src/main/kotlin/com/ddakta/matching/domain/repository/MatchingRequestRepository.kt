package com.ddakta.matching.domain.repository

import com.ddakta.matching.domain.entity.MatchingRequest
import com.ddakta.matching.domain.enum.MatchingRequestStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Duration
import java.time.LocalDateTime
import java.util.*

@Repository
interface MatchingRequestRepository : JpaRepository<MatchingRequest, UUID> {

    @Query("""
        SELECT mr FROM MatchingRequest mr
        WHERE mr.status = 'PENDING'
        AND mr.requestedAt > :maxAge
        ORDER BY mr.requestedAt ASC
    """)
    fun findPendingRequests(
        @Param("maxAge") maxAge: LocalDateTime = LocalDateTime.now().minusSeconds(10),
        @Param("limit") limit: Int = 100
    ): List<MatchingRequest>

    fun findPendingRequests(limit: Int, maxAge: Duration): List<MatchingRequest> {
        return findPendingRequests(LocalDateTime.now().minus(maxAge), limit)
    }

    @Query("SELECT mr FROM MatchingRequest mr WHERE mr.rideId = :rideId")
    fun findByRideId(@Param("rideId") rideId: UUID): MatchingRequest?

    @Query("SELECT mr FROM MatchingRequest mr WHERE mr.batchId = :batchId")
    fun findByBatchId(@Param("batchId") batchId: UUID): List<MatchingRequest>

    @Query("SELECT mr FROM MatchingRequest mr WHERE mr.status = :status")
    fun findByStatus(@Param("status") status: MatchingRequestStatus): List<MatchingRequest>

    @Modifying
    @Query("""
        UPDATE MatchingRequest mr
        SET mr.status = 'FAILED',
            mr.errorMessage = 'Request timeout'
        WHERE mr.status = 'PENDING'
        AND mr.requestedAt < :timeout
    """)
    fun failTimedOutRequests(
        @Param("timeout") timeout: LocalDateTime = LocalDateTime.now().minusMinutes(5)
    ): Int

    @Query("""
        SELECT mr FROM MatchingRequest mr
        WHERE mr.status = 'FAILED'
        AND mr.retryCount < 3
        AND mr.processedAt > :since
    """)
    fun findRetryableFailedRequests(
        @Param("since") since: LocalDateTime = LocalDateTime.now().minusMinutes(10)
    ): List<MatchingRequest>

    @Query("""
        SELECT COUNT(mr) FROM MatchingRequest mr
        WHERE mr.pickupH3 = :h3Index
        AND mr.status = 'PENDING'
        AND mr.requestedAt > :since
    """)
    fun countPendingRequestsInArea(
        @Param("h3Index") h3Index: String,
        @Param("since") since: LocalDateTime
    ): Long

    @Modifying
    @Query("DELETE FROM MatchingRequest mr WHERE mr.processedAt < :before")
    fun deleteOldProcessedRequests(
        @Param("before") before: LocalDateTime = LocalDateTime.now().minusDays(7)
    ): Int

    @Modifying
    @Query("""
        DELETE FROM MatchingRequest mr
        WHERE mr.id IN (
            SELECT mr2.id FROM MatchingRequest mr2
            WHERE mr2.status IN ('EXPIRED', 'FAILED', 'CANCELLED')
            AND mr2.updatedAt < :cutoffDate
            ORDER BY mr2.updatedAt ASC
            LIMIT :limit
        )
    """)
    fun deleteExpiredRequestsBefore(
        @Param("cutoffDate") cutoffDate: LocalDateTime,
        @Param("limit") limit: Int
    ): Int
    
    // 만료된 매칭 요청 조회
    @Query("""
        SELECT mr FROM MatchingRequest mr
        WHERE mr.status = 'PENDING'
        AND mr.requestedAt < :expiredBefore
        ORDER BY mr.requestedAt ASC
    """)
    fun findExpiredRequests(@Param("expiredBefore") expiredBefore: LocalDateTime): List<MatchingRequest>
    
    // 활성 H3 인덱스 조회
    @Query("""
        SELECT DISTINCT mr.pickupH3 FROM MatchingRequest mr
        WHERE mr.status = 'PENDING'
        AND mr.requestedAt > :since
    """)
    fun findActiveH3Indexes(@Param("since") since: LocalDateTime): List<String>
    
    // 오래된 요청 삭제
    @Modifying
    @Query("DELETE FROM MatchingRequest mr WHERE mr.processedAt < :before")
    fun deleteOldRequests(@Param("before") before: LocalDateTime): Int
}
