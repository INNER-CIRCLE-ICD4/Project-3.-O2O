package com.ddakta.matching.event.producer

import com.ddakta.matching.domain.entity.DriverCall
import com.ddakta.matching.domain.entity.Ride
import com.ddakta.matching.dto.internal.LocationInfo
import com.ddakta.matching.event.model.ride.RideRequestedEvent
import com.ddakta.matching.event.model.ride.RideMatchedEvent
import com.ddakta.matching.event.model.ride.RideStatusChangedEvent
import com.ddakta.matching.event.model.ride.RideCancelledEvent
import com.ddakta.matching.event.model.ride.RideCompletedEvent
import com.ddakta.matching.event.model.ride.DriverCallRequestEvent
import com.ddakta.matching.event.model.ride.DriverCallInfo
import com.ddakta.matching.event.model.ride.EventLocationDto
import com.ddakta.matching.event.model.driver.DriverStatusChangedEvent
import com.ddakta.matching.exception.EventPublishException
import com.fasterxml.jackson.databind.ObjectMapper
import mu.KotlinLogging
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.util.*

@Component
class RideEventProducer(
    private val kafkaTemplate: KafkaTemplate<String, Any>,
    private val objectMapper: ObjectMapper
) {

    private val logger = KotlinLogging.logger {}

    companion object {
        const val RIDE_REQUESTED_TOPIC = "ride-requested"
        const val RIDE_MATCHED_TOPIC = "ride-matched"
        const val RIDE_STATUS_CHANGED_TOPIC = "ride-status-changed"
        const val RIDE_CANCELLED_TOPIC = "ride-cancelled"
        const val RIDE_COMPLETED_TOPIC = "ride-completed"
        const val DRIVER_CALL_REQUEST_TOPIC = "driver-call-request"
        const val DRIVER_STATUS_CHANGED_TOPIC = "driver-status-changed"
    }

    fun publishRideRequested(ride: Ride) {
        val event = RideRequestedEvent(
            rideId = ride.id ?: throw IllegalArgumentException("Ride ID cannot be null when publishing ride requested event"),
            passengerId = ride.passengerId,
            pickupLocation = EventLocationDto.from(ride.pickupLocation),
            dropoffLocation = EventLocationDto.from(ride.dropoffLocation),
            pickupH3 = ride.pickupLocation.h3Index,
            requestedAt = ride.requestedAt,
            estimatedFare = ride.fare?.baseFare,
            surgeMultiplier = ride.fare?.surgeMultiplier?.toDouble() ?: 1.0
        )

        publishEvent(RIDE_REQUESTED_TOPIC, ride.id.toString(), event)
    }

    fun publishRideMatched(ride: Ride, estimatedArrival: Int? = null, driverLocation: LocationInfo? = null) {
        val event = RideMatchedEvent(
            rideId = ride.id ?: throw IllegalArgumentException("Ride ID cannot be null when publishing ride matched event"),
            passengerId = ride.passengerId,
            driverId = ride.driverId ?: throw IllegalArgumentException("Driver ID cannot be null when publishing ride matched event"),
            matchedAt = ride.matchedAt ?: LocalDateTime.now(),
            estimatedArrivalSeconds = estimatedArrival,
            driverLocation = driverLocation?.let { EventLocationDto.from(it) }
        )

        publishEvent(RIDE_MATCHED_TOPIC, ride.id.toString(), event)
    }

    fun publishRideStatusChanged(ride: Ride, previousStatus: String, metadata: Map<String, Any>? = null) {
        val event = RideStatusChangedEvent(
            rideId = ride.id ?: throw IllegalArgumentException("Ride ID cannot be null when publishing ride status changed event"),
            passengerId = ride.passengerId,
            driverId = ride.driverId,
            previousStatus = previousStatus,
            newStatus = ride.status.name,
            changedAt = LocalDateTime.now(),
            metadata = metadata
        )

        publishEvent(RIDE_STATUS_CHANGED_TOPIC, ride.id.toString(), event)
    }

    fun publishRideCancelled(ride: Ride) {
        val event = RideCancelledEvent(
            rideId = ride.id ?: throw IllegalArgumentException("Ride ID cannot be null when publishing ride cancelled event"),
            passengerId = ride.passengerId,
            driverId = ride.driverId,
            cancellationReason = ride.cancellationReason?.name,
            cancelledBy = ride.cancelledBy?.name,
            cancelledAt = ride.cancelledAt ?: LocalDateTime.now()
        )

        publishEvent(RIDE_CANCELLED_TOPIC, ride.id.toString(), event)
    }

    fun publishRideCompleted(ride: Ride) {
        val event = RideCompletedEvent(
            rideId = ride.id ?: throw IllegalArgumentException("Ride ID cannot be null when publishing ride completed event"),
            passengerId = ride.passengerId,
            driverId = ride.driverId ?: throw IllegalArgumentException("Driver ID cannot be null when publishing ride completed event"),
            completedAt = ride.completedAt ?: LocalDateTime.now(),
            distanceMeters = ride.distanceMeters,
            durationSeconds = ride.durationSeconds,
            totalFare = ride.fare?.totalFare
        )

        publishEvent(RIDE_COMPLETED_TOPIC, ride.id.toString(), event)
    }

    fun publishDriverCallRequest(rideId: UUID, driverCalls: List<DriverCall>) {
        val event = DriverCallRequestEvent(
            rideId = rideId,
            drivers = driverCalls.map { call ->
                DriverCallInfo(
                    callId = call.id ?: throw IllegalArgumentException("Driver call ID cannot be null when publishing driver call request event"),
                    driverId = call.driverId,
                    expiresAt = call.expiresAt,
                    estimatedArrival = call.estimatedArrivalSeconds,
                    estimatedFare = call.estimatedFare
                )
            },
            requestedAt = LocalDateTime.now()
        )

        publishEvent(DRIVER_CALL_REQUEST_TOPIC, rideId.toString(), event)
    }

    fun publishDriverStatusChanged(
        driverId: UUID, 
        newStatus: String, 
        previousStatus: String = "UNKNOWN",
        h3Index: String? = null
    ) {
        val event = DriverStatusChangedEvent(
            driverId = driverId,
            previousStatus = previousStatus,
            newStatus = newStatus,
            h3Index = h3Index,
            changedAt = LocalDateTime.now()
        )

        publishEvent(DRIVER_STATUS_CHANGED_TOPIC, driverId.toString(), event)
    }

    private fun publishEvent(topic: String, key: String, event: Any) {
        try {
            kafkaTemplate.send(topic, key, event)

            // 비동기 발송 - 콜백 없이 단순화
            logger.debug { "Sent event to $topic: ${event.javaClass.simpleName}" }
        } catch (e: Exception) {
            logger.error(e) { "Error publishing event to $topic" }
            throw EventPublishException("Failed to publish event", e)
        }
    }

    private fun handlePublishFailure(
        topic: String,
        key: String,
        @Suppress("UNUSED_PARAMETER") event: Any,
        error: Throwable
    ) {
        // TODO: Implement dead letter queue or retry mechanism
        logger.error { "Failed to publish to $topic with key $key: ${error.message}" }
    }
}