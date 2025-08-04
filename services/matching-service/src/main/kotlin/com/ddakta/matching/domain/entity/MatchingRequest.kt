package com.ddakta.matching.domain.entity

import com.ddakta.domain.base.BaseEntity
import com.ddakta.matching.domain.enum.MatchingRequestStatus
import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.*

@Entity
@Table(name = "matching_requests")
class MatchingRequest(
    @Column(nullable = false)
    val rideId: UUID,

    @Column(nullable = false)
    val passengerId: UUID,

    @Column(nullable = false)
    val pickupH3: String,

    @Column(nullable = false)
    val dropoffH3: String,

    @Column(nullable = false)
    val surgeMultiplier: java.math.BigDecimal = java.math.BigDecimal.ONE,

    @Column(nullable = false)
    val expiresAt: LocalDateTime = LocalDateTime.now().plusMinutes(5)
) : BaseEntity() {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: MatchingRequestStatus = MatchingRequestStatus.PENDING

    val requestedAt: LocalDateTime = LocalDateTime.now()

    var processingStartedAt: LocalDateTime? = null
        private set

    var processedAt: LocalDateTime? = null
        private set

    var batchId: UUID? = null

    var retryCount: Int = 0
        private set

    var errorMessage: String? = null

    var lastNoDriverAt: LocalDateTime? = null
        private set

    fun startProcessing(batchId: UUID) {
        require(status == MatchingRequestStatus.PENDING) {
            "Cannot start processing request in status $status"
        }
        this.status = MatchingRequestStatus.PROCESSING
        this.processingStartedAt = LocalDateTime.now()
        this.batchId = batchId
    }

    fun complete() {
        require(status == MatchingRequestStatus.PROCESSING) {
            "Cannot complete request in status $status"
        }
        this.status = MatchingRequestStatus.COMPLETED
        this.processedAt = LocalDateTime.now()
    }

    fun fail(errorMessage: String) {
        this.status = MatchingRequestStatus.FAILED
        this.processedAt = LocalDateTime.now()
        this.errorMessage = errorMessage
        this.retryCount++
        
        // 드라이버 없음 오류인 경우 타임스탬프 기록
        if (errorMessage.contains("no driver", ignoreCase = true)) {
            this.lastNoDriverAt = LocalDateTime.now()
        }
    }

    fun resetForRetry() {
        require(status == MatchingRequestStatus.FAILED) {
            "Cannot retry request in status $status"
        }
        this.status = MatchingRequestStatus.PENDING
        this.processingStartedAt = null
        this.processedAt = null
        this.batchId = null
    }

    fun isRetryable(): Boolean {
        return status == MatchingRequestStatus.FAILED && retryCount < 3
    }
    
    // TODO: 임시 추가 - 컴파일 오류 해결용
    fun markAsMatched() {
        this.status = MatchingRequestStatus.COMPLETED
        this.processedAt = LocalDateTime.now()
    }
    
    var matchedAt: LocalDateTime? = null
        private set

    // H3 인덱스 호환성을 위한 프로퍼티 (EventProducer에서 사용)
    val pickupH3Index: String get() = pickupH3
    val dropoffH3Index: String get() = dropoffH3
}