package com.ddakta.matching.dto.response

import com.ddakta.matching.domain.entity.MatchingRequest
import com.ddakta.matching.domain.enum.MatchingRequestStatus
import java.time.LocalDateTime
import java.util.*

data class MatchingRequestResponseDto(
    val id: UUID,
    val rideId: UUID,
    val pickupH3: String,
    val status: MatchingRequestStatus,
    val requestedAt: LocalDateTime,
    val processingStartedAt: LocalDateTime?,
    val processedAt: LocalDateTime?,
    val batchId: UUID?,
    val retryCount: Int,
    val errorMessage: String?
) {
    companion object {
        fun from(request: MatchingRequest): MatchingRequestResponseDto {
            return MatchingRequestResponseDto(
                id = request.id!!,
                rideId = request.rideId,
                pickupH3 = request.pickupH3,
                status = request.status,
                requestedAt = request.requestedAt,
                processingStartedAt = request.processingStartedAt,
                processedAt = request.processedAt,
                batchId = request.batchId,
                retryCount = request.retryCount,
                errorMessage = request.errorMessage
            )
        }
    }
}