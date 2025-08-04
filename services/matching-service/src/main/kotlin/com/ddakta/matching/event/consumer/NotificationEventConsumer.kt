package com.ddakta.matching.event.consumer

import com.ddakta.matching.event.model.notification.NotificationDeliveredEvent
import com.ddakta.matching.event.model.notification.NotificationFailedEvent
import mu.KotlinLogging
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

@Component
class NotificationEventConsumer {
    
    private val logger = KotlinLogging.logger {}
    
    @KafkaListener(
        topics = ["notification-delivered"],
        groupId = "matching-service-notification",
        containerFactory = "kafkaListenerContainerFactory"
    )
    fun handleNotificationDelivered(
        event: NotificationDeliveredEvent,
        acknowledgment: Acknowledgment
    ) {
        logger.debug { 
            "Notification ${event.notificationId} delivered to ${event.recipientType} " +
            "${event.recipientId} for ${event.referenceType} ${event.referenceId}"
        }
        
        try {
            // Log successful notification delivery
            // Could update metrics or tracking information
            
            when (event.referenceType) {
                "RIDE" -> {
                    logger.debug { "Ride notification delivered for ${event.referenceId}" }
                }
                "DRIVER_CALL" -> {
                    logger.debug { "Driver call notification delivered for ${event.referenceId}" }
                }
            }
            
            acknowledgment.acknowledge()
            
        } catch (e: Exception) {
            logger.error(e) { "Error processing notification delivery event" }
            throw e
        }
    }
    
    @KafkaListener(
        topics = ["notification-failed"],
        groupId = "matching-service-notification",
        containerFactory = "kafkaListenerContainerFactory"
    )
    fun handleNotificationFailed(
        event: NotificationFailedEvent,
        acknowledgment: Acknowledgment
    ) {
        logger.warn { 
            "Notification ${event.notificationId} failed for ${event.recipientType} " +
            "${event.recipientId}: ${event.reason} (retry ${event.retryCount})"
        }
        
        try {
            // Handle notification failure
            // Could trigger alternative notification methods
            
            if (event.retryCount >= 3) {
                logger.error { 
                    "Notification ${event.notificationId} failed after ${event.retryCount} retries"
                }
                // Could implement fallback notification strategy
            }
            
            acknowledgment.acknowledge()
            
        } catch (e: Exception) {
            logger.error(e) { "Error processing notification failure event" }
            throw e
        }
    }
}