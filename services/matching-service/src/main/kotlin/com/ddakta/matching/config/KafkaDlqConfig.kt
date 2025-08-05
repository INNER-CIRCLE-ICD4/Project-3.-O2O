package com.ddakta.matching.config

import mu.KotlinLogging
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.*
import org.springframework.kafka.listener.ContainerProperties
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer
import org.springframework.kafka.support.serializer.JsonDeserializer
import org.springframework.kafka.support.serializer.JsonSerializer
import org.springframework.util.backoff.ExponentialBackOff
import java.time.Duration

@Configuration
class KafkaDlqConfig(
    @Value("\${spring.kafka.bootstrap-servers}") private val bootstrapServers: String,
    @Value("\${spring.kafka.consumer.group-id}") private val groupId: String
) {
    
    private val logger = KotlinLogging.logger {}
    
    companion object {
        const val DLQ_SUFFIX = "-dlq"
        const val RETRY_ATTEMPTS = 3
        const val INITIAL_INTERVAL_MS = 1000L // 1 second
        const val MULTIPLIER = 5.0 // 1s -> 5s -> 25s (max 30s)
        const val MAX_INTERVAL_MS = 30000L // 30 seconds
    }
    
    @Bean
    fun dlqProducerFactory(): ProducerFactory<String, Any> {
        val configs = HashMap<String, Any>()
        configs[ProducerConfig.BOOTSTRAP_SERVERS_CONFIG] = bootstrapServers
        configs[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
        configs[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = JsonSerializer::class.java
        configs[ProducerConfig.ACKS_CONFIG] = "all"
        configs[ProducerConfig.RETRIES_CONFIG] = 3
        configs[ProducerConfig.BATCH_SIZE_CONFIG] = 16384
        configs[ProducerConfig.LINGER_MS_CONFIG] = 10
        configs[ProducerConfig.BUFFER_MEMORY_CONFIG] = 33554432
        
        return DefaultKafkaProducerFactory(configs)
    }
    
    @Bean
    fun dlqKafkaTemplate(): KafkaTemplate<String, Any> {
        return KafkaTemplate(dlqProducerFactory())
    }
    
    @Bean
    fun kafkaListenerContainerFactoryWithDlq(
        consumerFactory: ConsumerFactory<String, Any>,
        dlqKafkaTemplate: KafkaTemplate<String, Any>
    ): ConcurrentKafkaListenerContainerFactory<String, Any> {
        val factory = ConcurrentKafkaListenerContainerFactory<String, Any>()
        factory.consumerFactory = consumerFactory
        
        // Configure exponential backoff
        val backOff = ExponentialBackOff().apply {
            initialInterval = INITIAL_INTERVAL_MS
            multiplier = MULTIPLIER
            maxInterval = MAX_INTERVAL_MS
        }
        
        // Configure error handler with DLQ
        val errorHandler = DefaultErrorHandler({ consumerRecord, exception ->
            logger.error { 
                "Failed to process message after $RETRY_ATTEMPTS attempts. " +
                "Topic: ${consumerRecord.topic()}, " +
                "Partition: ${consumerRecord.partition()}, " +
                "Offset: ${consumerRecord.offset()}, " +
                "Key: ${consumerRecord.key()}" 
            }
            
            // Send to DLQ
            val dlqTopic = "${consumerRecord.topic()}$DLQ_SUFFIX"
            try {
                dlqKafkaTemplate.send(
                    dlqTopic,
                    consumerRecord.key()?.toString() ?: "",
                    DlqMessage(
                        originalTopic = consumerRecord.topic(),
                        originalPartition = consumerRecord.partition(),
                        originalOffset = consumerRecord.offset(),
                        originalKey = consumerRecord.key() as String?,
                        originalValue = consumerRecord.value(),
                        errorMessage = exception.message ?: "Unknown error",
                        errorClass = exception.javaClass.name,
                        failedAt = System.currentTimeMillis(),
                        retryCount = RETRY_ATTEMPTS
                    )
                ).get()
                
                logger.info { "Message sent to DLQ topic: $dlqTopic" }
            } catch (e: Exception) {
                logger.error(e) { "Failed to send message to DLQ: $dlqTopic" }
            }
        }, backOff)
        
        factory.setCommonErrorHandler(errorHandler)
        factory.containerProperties.ackMode = ContainerProperties.AckMode.RECORD
        
        return factory
    }
    
    @Bean
    fun dlqConsumerFactory(): ConsumerFactory<String, DlqMessage> {
        val configs = HashMap<String, Any>()
        configs[ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG] = bootstrapServers
        configs[ConsumerConfig.GROUP_ID_CONFIG] = "$groupId-dlq"
        configs[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
        configs[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = JsonDeserializer::class.java
        configs[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest"
        configs[ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG] = false
        configs[JsonDeserializer.TRUSTED_PACKAGES] = "*"
        configs[JsonDeserializer.VALUE_DEFAULT_TYPE] = DlqMessage::class.java
        
        val deserializer = ErrorHandlingDeserializer(JsonDeserializer(DlqMessage::class.java))
        
        return DefaultKafkaConsumerFactory(
            configs,
            StringDeserializer(),
            deserializer
        )
    }
    
    @Bean
    fun dlqKafkaListenerContainerFactory(): ConcurrentKafkaListenerContainerFactory<String, DlqMessage> {
        val factory = ConcurrentKafkaListenerContainerFactory<String, DlqMessage>()
        factory.consumerFactory = dlqConsumerFactory()
        factory.containerProperties.ackMode = ContainerProperties.AckMode.MANUAL
        return factory
    }
}

data class DlqMessage(
    val originalTopic: String,
    val originalPartition: Int,
    val originalOffset: Long,
    val originalKey: String?,
    val originalValue: Any?,
    val errorMessage: String,
    val errorClass: String,
    val failedAt: Long,
    val retryCount: Int,
    val metadata: Map<String, String> = emptyMap()
)