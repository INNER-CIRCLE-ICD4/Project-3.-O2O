package com.ddakta.matching.event.consumer

import com.ddakta.matching.domain.repository.RideRepository
import com.ddakta.matching.event.model.ride.RideRequestedEvent
import com.ddakta.matching.service.MatchingService
import mu.KotlinLogging
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class RideEventConsumer(
    private val matchingService: MatchingService,
    private val rideRepository: RideRepository
) {

    private val logger = KotlinLogging.logger {}

    @Transactional
    @KafkaListener(
        topics = ["ride-requested"],
        groupId = "matching-service-ride-request",
        containerFactory = "kafkaListenerContainerFactory"
    )
    fun handleRideRequested(
        event: RideRequestedEvent,
        acknowledgment: Acknowledgment
    ) {
        logger.info { "Received ride requested event for rideId: ${event.rideId}" }

        try {
            // Fetch the full Ride entity using the ID from the event
            val ride = rideRepository.findById(event.rideId)
                .orElse(null)

            if (ride == null) {
                logger.warn { "Ride with id ${event.rideId} not found. Acknowledging message to avoid retries." }
                acknowledgment.acknowledge()
                return
            }

            // Call the service to create a matching request
            matchingService.createMatchingRequest(ride)

            logger.info { "Successfully created matching request for rideId: ${event.rideId}" }

            // Acknowledge the message to mark it as processed
            acknowledgment.acknowledge()

        } catch (e: Exception) {
            logger.error(e) { "Error processing ride requested event for rideId: ${event.rideId}" }
            // Let the default error handler manage retries and DLQ
            throw e
        }
    }
}
