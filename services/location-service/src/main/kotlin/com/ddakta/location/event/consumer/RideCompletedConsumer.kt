package com.ddakta.location.event.consumer

import com.ddakta.location.event.model.RideCompletedEvent
import com.ddakta.location.event.publisher.RideCleanupEventProducer
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class RideCompletedConsumer(
    private val rideCleanupEventProducer: RideCleanupEventProducer,
    private val objectMapper: ObjectMapper
) {

    private val logger = LoggerFactory.getLogger(this::class.java)

    @KafkaListener(topics = ["ride-completed"], groupId = "location-service-ride-completed")
    fun handle(message: String) {
        try {
            val event = objectMapper.readValue(message, RideCompletedEvent::class.java)
            logger.info("Received ride completed event: {}", event)

            // RideCompletedEvent를 받아서 RideCleanupEvent를 발행한다.
            rideCleanupEventProducer.publish(event.rideId.toString(), event.driverId.toString())

        } catch (e: Exception) {
            logger.error("Error processing ride completed event: {}", message, e)
        }
    }
}
