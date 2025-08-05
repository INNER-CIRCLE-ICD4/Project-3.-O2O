package com.ddakta.matching.event.dlq

import com.ddakta.matching.config.DlqMessage
import com.fasterxml.jackson.databind.ObjectMapper
import mu.KotlinLogging
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.Acknowledgment
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.messaging.handler.annotation.Header
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

@Component
class DlqConsumer(
    private val kafkaTemplate: KafkaTemplate<String, Any>,
    private val objectMapper: ObjectMapper
) {
    
    private val logger = KotlinLogging.logger {}
    private val poisonMessageCounter = ConcurrentHashMap<String, AtomicLong>()
    
    companion object {
        const val POISON_MESSAGE_THRESHOLD = 5
        const val DLQ_GROUP_ID = "matching-service-dlq-consumer"
    }
    
    @KafkaListener(
        topics = [
            "ride-requested-dlq",
            "ride-matched-dlq",
            "ride-completed-dlq",
            "ride-cancelled-dlq",
            "driver-location-updated-dlq",
            "driver-status-changed-dlq",
            "driver-availability-changed-dlq",
            "payment-processed-dlq",
            "payment-failed-dlq",
            "surge-pricing-updated-dlq"
        ],
        groupId = DLQ_GROUP_ID,
        containerFactory = "dlqKafkaListenerContainerFactory"
    )
    fun handleDlqMessage(
        @Payload dlqMessage: DlqMessage,
        @Header(KafkaHeaders.RECEIVED_TOPIC) topic: String,
        @Header(KafkaHeaders.RECEIVED_TIMESTAMP) timestamp: Long,
        acknowledgment: Acknowledgment
    ) {
        logger.warn { 
            "Processing DLQ message from topic: $topic, " +
            "Original topic: ${dlqMessage.originalTopic}, " +
            "Error: ${dlqMessage.errorMessage}"
        }
        
        try {
            // Check if this is a poison message
            if (isPoisonMessage(dlqMessage)) {
                logger.error { "Poison message detected. Moving to poison message store." }
                storePoisonMessage(dlqMessage, topic)
                acknowledgment.acknowledge()
                return
            }
            
            // Log for monitoring
            logDlqMetrics(dlqMessage, topic)
            
            // Store for manual replay
            storeDlqMessage(dlqMessage, topic)
            
            // Acknowledge the message
            acknowledgment.acknowledge()
            
        } catch (e: Exception) {
            logger.error(e) { "Error processing DLQ message" }
            // Don't acknowledge - let it be retried
        }
    }
    
    fun replayDlqMessage(dlqMessage: DlqMessage): Boolean {
        return try {
            logger.info { 
                "Replaying message to original topic: ${dlqMessage.originalTopic}" 
            }
            
            kafkaTemplate.send(
                dlqMessage.originalTopic,
                dlqMessage.originalKey ?: "",
                dlqMessage.originalValue
            ).get()
            
            logger.info { "Message successfully replayed" }
            true
        } catch (e: Exception) {
            logger.error(e) { "Failed to replay message" }
            false
        }
    }
    
    fun replayAllMessages(originalTopic: String, limit: Int = 100): Int {
        logger.info { "Replaying up to $limit messages for topic: $originalTopic" }
        
        // In a real implementation, this would fetch from a persistent store
        // For now, we'll return a placeholder
        var replayedCount = 0
        
        // TODO: Implement actual replay logic from persistent store
        
        logger.info { "Replayed $replayedCount messages" }
        return replayedCount
    }
    
    private fun isPoisonMessage(dlqMessage: DlqMessage): Boolean {
        // Check specific error patterns that indicate poison messages
        val poisonPatterns = listOf(
            "JsonParseException",
            "JsonMappingException",
            "ClassCastException",
            "IllegalArgumentException",
            "NullPointerException"
        )
        
        val isPoison = poisonPatterns.any { pattern ->
            dlqMessage.errorClass.contains(pattern) || 
            dlqMessage.errorMessage.contains(pattern)
        }
        
        if (isPoison) {
            val messageKey = "${dlqMessage.originalTopic}:${dlqMessage.originalKey}"
            val counter = poisonMessageCounter.computeIfAbsent(messageKey) { AtomicLong(0) }
            return counter.incrementAndGet() >= POISON_MESSAGE_THRESHOLD
        }
        
        return false
    }
    
    private fun storePoisonMessage(dlqMessage: DlqMessage, dlqTopic: String) {
        // In a real implementation, store to a persistent poison message store
        logger.error { 
            "Storing poison message - Topic: ${dlqMessage.originalTopic}, " +
            "Key: ${dlqMessage.originalKey}, " +
            "Error: ${dlqMessage.errorClass}"
        }
        
        // Send metrics
        // metricsService.incrementPoisonMessageCount(dlqMessage.originalTopic)
    }
    
    private fun storeDlqMessage(dlqMessage: DlqMessage, dlqTopic: String) {
        // In a real implementation, store to a persistent DLQ store (e.g., database)
        logger.info { 
            "Storing DLQ message for potential replay - " +
            "Original topic: ${dlqMessage.originalTopic}"
        }
        
        // TODO: Implement persistent storage
    }
    
    private fun logDlqMetrics(dlqMessage: DlqMessage, dlqTopic: String) {
        // Log metrics for monitoring
        logger.info { 
            "DLQ_METRIC - " +
            "topic=${dlqMessage.originalTopic}, " +
            "error_class=${dlqMessage.errorClass}, " +
            "retry_count=${dlqMessage.retryCount}, " +
            "age_ms=${System.currentTimeMillis() - dlqMessage.failedAt}"
        }
    }
}