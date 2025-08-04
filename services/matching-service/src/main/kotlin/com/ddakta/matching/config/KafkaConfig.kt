package com.ddakta.matching.config

import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.*
import org.springframework.kafka.listener.ContainerProperties
import org.springframework.kafka.support.serializer.JsonDeserializer
import org.springframework.kafka.support.serializer.JsonSerializer
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.util.backoff.FixedBackOff
import mu.KotlinLogging

@Configuration
@EnableKafka
class KafkaConfig {
    
    private val logger = KotlinLogging.logger {}
    
    @Bean
    fun producerFactory(): ProducerFactory<String, Any> {
        val configs = mapOf(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to "kafka:9092",
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to JsonSerializer::class.java,
            ProducerConfig.ACKS_CONFIG to "all",
            ProducerConfig.RETRIES_CONFIG to 3,
            ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG to true,
            ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION to 5,
            ProducerConfig.COMPRESSION_TYPE_CONFIG to "snappy"
        )
        return DefaultKafkaProducerFactory(configs)
    }

    @Bean
    fun kafkaTemplate(): KafkaTemplate<String, Any> {
        return KafkaTemplate(producerFactory())
    }

    @Bean
    fun consumerFactory(): ConsumerFactory<String, Any> {
        val deserializer = JsonDeserializer<Any>()
        deserializer.addTrustedPackages("com.ddakta.*")
        
        val configs = mapOf(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to "kafka:9092",
            ConsumerConfig.GROUP_ID_CONFIG to "matching-service",
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to deserializer,
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
            ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to false,
            ConsumerConfig.MAX_POLL_RECORDS_CONFIG to 500
        )
        
        return DefaultKafkaConsumerFactory(configs, StringDeserializer(), deserializer)
    }

    @Bean
    fun kafkaListenerContainerFactory(): ConcurrentKafkaListenerContainerFactory<String, Any> {
        val factory = ConcurrentKafkaListenerContainerFactory<String, Any>()
        factory.consumerFactory = consumerFactory()
        factory.setConcurrency(3)
        factory.containerProperties.ackMode = ContainerProperties.AckMode.MANUAL_IMMEDIATE
        factory.setCommonErrorHandler(errorHandler())
        return factory
    }
    
    @Bean
    fun errorHandler(): DefaultErrorHandler {
        // Configure retry with backoff: 3 attempts, 1 second interval
        val backOff = FixedBackOff(1000L, 3)
        
        // Dead letter publishing recoverer for failed messages
        val recoverer = DeadLetterPublishingRecoverer(kafkaTemplate()) { record, _ ->
            logger.error { "Sending message to DLQ: ${record.topic()}.dlq" }
            record.headers().add("x-exception-timestamp", System.currentTimeMillis().toString().toByteArray())
            // Return TopicPartition for DLQ
            org.apache.kafka.common.TopicPartition(record.topic() + ".dlq", record.partition())
        }
        
        return DefaultErrorHandler(recoverer, backOff).apply {
            // Add specific exceptions that should not be retried
            addNotRetryableExceptions(
                IllegalArgumentException::class.java,
                NullPointerException::class.java
            )
        }
    }
}