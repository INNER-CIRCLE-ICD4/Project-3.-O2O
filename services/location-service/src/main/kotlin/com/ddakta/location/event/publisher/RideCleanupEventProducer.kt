package com.ddakta.location.event.publisher

import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

@Component
class RideCleanupEventProducer(
    private val kafkaTemplate: KafkaTemplate<String, Any>
) {

    private val logger = LoggerFactory.getLogger(this::class.java)

    companion object {
        const val TOPIC = "ride-cleanup"
    }

    fun publish(rideId: String, driverId: String) {
        val event = RideCleanupEvent(
            rideId = rideId,
            driverId = driverId,
            endedAt = System.currentTimeMillis()
        )
        try {
            kafkaTemplate.send(TOPIC, event)
            logger.info("Published ride cleanup event: {}", event)
        } catch (e: Exception) {
            logger.error("Failed to publish ride cleanup event for rideId: {}", rideId, e)
        }
    }
}
