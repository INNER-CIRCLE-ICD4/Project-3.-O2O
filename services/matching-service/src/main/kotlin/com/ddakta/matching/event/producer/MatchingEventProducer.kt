package com.ddakta.matching.event.producer

import com.ddakta.matching.domain.entity.MatchingRequest
import com.ddakta.matching.event.model.MatchingRequestCreatedEvent
import com.ddakta.matching.event.model.MatchingRequestExpiredEvent
import com.ddakta.matching.event.model.MatchingSuccessEvent
import com.ddakta.matching.exception.EventPublishException
import mu.KotlinLogging
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult
import org.springframework.stereotype.Component
import org.springframework.util.concurrent.ListenableFutureCallback
import java.time.LocalDateTime
import java.util.*

@Component
class MatchingEventProducer(
    private val kafkaTemplate: KafkaTemplate<String, Any>
) {

    private val logger = KotlinLogging.logger {}

    companion object {
        const val MATCHING_REQUEST_CREATED_TOPIC = "matching-request-created"
        const val MATCHING_SUCCESS_TOPIC = "matching-success"
        const val MATCHING_FAILED_TOPIC = "matching-failed"
        const val MATCHING_REQUEST_EXPIRED_TOPIC = "matching-request-expired"
        const val MATCHING_RETRY_TOPIC = "matching-retry"
        const val SURGE_PRICING_UPDATED_TOPIC = "surge-pricing-updated"
    }

    fun publishMatchingRequestCreated(matchingRequest: MatchingRequest) {
        val event = MatchingRequestCreatedEvent(
            requestId = matchingRequest.id!!,
            rideId = matchingRequest.rideId,
            passengerId = matchingRequest.passengerId,
            pickupH3Index = matchingRequest.pickupH3Index,
            dropoffH3Index = matchingRequest.dropoffH3Index,
            surgeMultiplier = matchingRequest.surgeMultiplier.toDouble(),
            requestedAt = matchingRequest.requestedAt,
            expiresAt = matchingRequest.expiresAt
        )

        publishEvent(MATCHING_REQUEST_CREATED_TOPIC, matchingRequest.rideId.toString(), event)
    }

    fun publishMatchingSuccess(
        rideId: UUID,
        passengerId: UUID,
        driverId: UUID,
        matchingScore: Double,
        estimatedArrival: Int
    ) {
        val event = MatchingSuccessEvent(
            rideId = rideId,
            passengerId = passengerId,
            matchedDriverId = driverId,
            matchingScore = matchingScore,
            estimatedArrivalSeconds = estimatedArrival,
            matchedAt = LocalDateTime.now()
        )

        publishEvent(MATCHING_SUCCESS_TOPIC, rideId.toString(), event)
    }

    fun publishMatchingFailed(
        rideId: UUID,
        passengerId: UUID,
        reason: String,
        retryable: Boolean = true
    ) {
        val event = mapOf(
            "rideId" to rideId,
            "passengerId" to passengerId,
            "reason" to reason,
            "retryable" to retryable,
            "failedAt" to LocalDateTime.now()
        )

        publishEvent(MATCHING_FAILED_TOPIC, rideId.toString(), event)
    }

    fun publishMatchingRequestExpired(matchingRequest: MatchingRequest) {
        val event = MatchingRequestExpiredEvent(
            requestId = matchingRequest.id!!,
            rideId = matchingRequest.rideId,
            passengerId = matchingRequest.passengerId,
            expiredAt = LocalDateTime.now(),
            retryCount = matchingRequest.retryCount,
            lastNoDriverAt = matchingRequest.lastNoDriverAt
        )

        publishEvent(MATCHING_REQUEST_EXPIRED_TOPIC, matchingRequest.rideId.toString(), event)
    }

    fun publishMatchingRetry(
        rideId: UUID,
        passengerId: UUID,
        retryCount: Int,
        reason: String
    ) {
        val event = mapOf(
            "rideId" to rideId,
            "passengerId" to passengerId,
            "retryCount" to retryCount,
            "reason" to reason,
            "retriedAt" to LocalDateTime.now()
        )

        publishEvent(MATCHING_RETRY_TOPIC, rideId.toString(), event)
    }

    fun publishSurgePricingUpdate(
        h3Index: String,
        multiplier: Double,
        reason: String
    ) {
        val event = mapOf(
            "h3Index" to h3Index,
            "multiplier" to multiplier,
            "reason" to reason,
            "updatedAt" to LocalDateTime.now()
        )

        publishEvent(SURGE_PRICING_UPDATED_TOPIC, h3Index, event)
    }

    private fun publishEvent(topic: String, key: String, event: Any) {
        try {
            val future = kafkaTemplate.send(topic, key, event)

            // 비동기 발송 - 콜백 없이 단순화
            logger.debug { "Sent matching event to $topic: ${event::class.simpleName}" }
        } catch (e: Exception) {
            logger.error(e) { "Error publishing matching event to $topic" }
            throw EventPublishException("Failed to publish matching event", e)
        }
    }

    private fun handlePublishFailure(
        topic: String,
        key: String,
        event: Any,
        error: Throwable
    ) {
        // TODO: Implement dead letter queue or retry mechanism
        logger.error { "Failed to publish to $topic with key $key: ${error.message}" }
    }
}