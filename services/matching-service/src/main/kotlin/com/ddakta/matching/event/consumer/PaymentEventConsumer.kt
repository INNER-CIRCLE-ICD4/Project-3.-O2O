package com.ddakta.matching.event.consumer

import com.ddakta.matching.domain.enum.RideEvent
import com.ddakta.matching.dto.request.RideStatusUpdateDto
import com.ddakta.matching.event.model.payment.PaymentFailedEvent
import com.ddakta.matching.event.model.payment.PaymentProcessedEvent
import com.ddakta.matching.event.model.payment.PaymentRefundedEvent
import com.ddakta.matching.service.RideService
import mu.KotlinLogging
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.messaging.handler.annotation.Header
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Component
class PaymentEventConsumer(
    private val rideService: RideService
) {
    
    private val logger = KotlinLogging.logger {}
    private val processedPayments = mutableSetOf<UUID>() // Simple idempotency check
    
    @KafkaListener(
        topics = ["payment-processed"],
        groupId = "matching-service-payment",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    fun handlePaymentProcessed(
        @Payload event: PaymentProcessedEvent,
        @Header(KafkaHeaders.RECEIVED_TOPIC) topic: String,
        @Header(KafkaHeaders.RECEIVED_PARTITION) partition: Int,
        @Header(KafkaHeaders.OFFSET) offset: Long,
        acknowledgment: Acknowledgment
    ) {
        logger.info { 
            "Received payment processed event: paymentId=${event.paymentId}, " +
            "rideId=${event.rideId} from $topic[$partition:$offset]"
        }
        
        try {
            // Idempotency check
            if (processedPayments.contains(event.paymentId)) {
                logger.warn { "Payment ${event.paymentId} already processed, skipping" }
                acknowledgment.acknowledge()
                return
            }
            
            // Get ride information
            val ride = rideService.getRide(event.rideId)
            
            // Update ride status if payment was for completed ride
            if (ride.status.name == "COMPLETED" || ride.status.name == "ON_TRIP") {
                logger.info { "Payment processed for ride ${event.rideId}, updating payment status" }
                
                // Update ride with payment confirmation
                // This could trigger additional events or notifications
                val metadata = mapOf(
                    "paymentId" to event.paymentId.toString(),
                    "paymentMethod" to event.paymentMethod,
                    "amount" to event.amount.toString()
                )
                
                // Store processed payment ID
                processedPayments.add(event.paymentId)
            }
            
            acknowledgment.acknowledge()
            
        } catch (e: Exception) {
            logger.error(e) { "Error processing payment event for ride ${event.rideId}" }
            throw e // Will trigger retry/DLQ based on error handler configuration
        }
    }
    
    @KafkaListener(
        topics = ["payment-failed"],
        groupId = "matching-service-payment",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    fun handlePaymentFailed(
        @Payload event: PaymentFailedEvent,
        @Header(KafkaHeaders.RECEIVED_TOPIC) topic: String,
        acknowledgment: Acknowledgment
    ) {
        logger.warn { 
            "Payment failed for ride ${event.rideId}: ${event.reason}"
        }
        
        try {
            val ride = rideService.getRide(event.rideId)
            
            // Handle payment failure based on ride status
            when (ride.status.name) {
                "COMPLETED" -> {
                    // Notify driver and passenger about payment issue
                    logger.warn { "Payment failed for completed ride ${event.rideId}" }
                    // Could trigger notifications or status updates
                }
                "REQUESTED", "MATCHED" -> {
                    // Cancel ride if payment fails before trip starts
                    if (!event.retryable) {
                        logger.info { "Cancelling ride ${event.rideId} due to payment failure" }
                        // The ride service would handle the cancellation logic
                    }
                }
            }
            
            acknowledgment.acknowledge()
            
        } catch (e: Exception) {
            logger.error(e) { "Error handling payment failure for ride ${event.rideId}" }
            throw e
        }
    }
    
    @KafkaListener(
        topics = ["payment-refunded"],
        groupId = "matching-service-payment",
        containerFactory = "kafkaListenerContainerFactory"
    )
    fun handlePaymentRefunded(
        @Payload event: PaymentRefundedEvent,
        acknowledgment: Acknowledgment
    ) {
        logger.info { 
            "Payment refunded for ride ${event.rideId}: ${event.reason}"
        }
        
        try {
            // Log refund information
            // This might trigger additional notifications
            
            acknowledgment.acknowledge()
            
        } catch (e: Exception) {
            logger.error(e) { "Error handling payment refund for ride ${event.rideId}" }
            throw e
        }
    }
}