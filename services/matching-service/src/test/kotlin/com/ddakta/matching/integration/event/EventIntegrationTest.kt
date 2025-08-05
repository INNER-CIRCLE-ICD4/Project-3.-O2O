package com.ddakta.matching.integration.event

import com.ddakta.matching.domain.entity.Ride
import com.ddakta.matching.domain.enum.RideStatus
import com.ddakta.matching.domain.repository.RideRepository
import com.ddakta.matching.domain.vo.Fare
import com.ddakta.matching.domain.vo.Location
import com.ddakta.matching.dto.internal.LocationInfo
import com.ddakta.matching.event.producer.RideEventProducer
import com.ddakta.matching.integration.BaseIntegrationTest
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.readValue
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.test.utils.KafkaTestUtils
import org.springframework.test.context.ActiveProfiles
import java.math.BigDecimal
import java.time.Duration
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

@DisplayName("Event Integration Tests")
class EventIntegrationTest : BaseIntegrationTest() {

    @Autowired
    private lateinit var rideEventProducer: RideEventProducer

    @Autowired
    private lateinit var kafkaTemplate: KafkaTemplate<String, String>

    @Autowired
    private lateinit var rideRepository: RideRepository

    private lateinit var kafkaConsumer: KafkaConsumer<String, String>
    private lateinit var testRide: Ride

    @BeforeEach
    fun setUp() {
        setupKafkaConsumer()
        testRide = createTestRide()
    }

    @AfterEach
    fun tearDown() {
        kafkaConsumer.close()
    }

    @Nested
    @DisplayName("Ride Event Publishing Tests")
    inner class RideEventPublishingTests {

        @Test
        @DisplayName("Should publish ride requested event")
        fun shouldPublishRideRequestedEvent() {
            // Given
            val savedRide = rideRepository.save(testRide)

            // When
            rideEventProducer.publishRideRequested(savedRide)

            // Then
            val eventMessage = waitForKafkaMessage("ride-events") { message ->
                val event = objectMapper.readValue<JsonNode>(message)
                event.get("eventType")?.asText() == "RIDE_REQUESTED" &&
                event.get("rideId")?.asText() == savedRide.id.toString()
            }

            assertThat(eventMessage).isNotNull
            val event = objectMapper.readValue<JsonNode>(eventMessage!!)
            
            assertThat(event.get("eventType").asText()).isEqualTo("RIDE_REQUESTED")
            assertThat(event.get("rideId").asText()).isEqualTo(savedRide.id.toString())
            assertThat(event.get("passengerId").asText()).isEqualTo(savedRide.passengerId.toString())
            assertThat(event.get("timestamp")).isNotNull()
            assertThat(event.get("pickupLocation")).isNotNull()
            assertThat(event.get("dropoffLocation")).isNotNull()
            assertThat(event.get("fare")).isNotNull()
        }

        @Test
        @DisplayName("Should publish ride matched event")
        fun shouldPublishRideMatchedEvent() {
            // Given
            val savedRide = rideRepository.save(testRide)
            val estimatedArrivalSeconds = 300
            val driverLocation = LocationInfo(37.5660, 126.9775, "8830e1d8dffffff")

            // When
            rideEventProducer.publishRideMatched(savedRide, estimatedArrivalSeconds, driverLocation)

            // Then
            val eventMessage = waitForKafkaMessage("ride-events") { message ->
                val event = objectMapper.readValue<JsonNode>(message)
                event.get("eventType")?.asText() == "RIDE_MATCHED" &&
                event.get("rideId")?.asText() == savedRide.id.toString()
            }

            assertThat(eventMessage).isNotNull
            val event = objectMapper.readValue<JsonNode>(eventMessage!!)
            
            assertThat(event.get("eventType").asText()).isEqualTo("RIDE_MATCHED")
            assertThat(event.get("rideId").asText()).isEqualTo(savedRide.id.toString())
            assertThat(event.get("estimatedArrivalSeconds").asInt()).isEqualTo(estimatedArrivalSeconds)
            
            if (event.has("driverLocation")) {
                assertThat(event.get("driverLocation").get("latitude").asDouble())
                    .isEqualTo(driverLocation.latitude)
            }
        }

        @Test
        @DisplayName("Should publish ride status changed event")
        fun shouldPublishRideStatusChangedEvent() {
            // Given
            val savedRide = rideRepository.save(testRide)
            val previousStatus = "REQUESTED"
            val metadata = mapOf("reason" to "driver_accepted", "timestamp" to System.currentTimeMillis())

            // When
            rideEventProducer.publishRideStatusChanged(savedRide, previousStatus, metadata)

            // Then
            val eventMessage = waitForKafkaMessage("ride-events") { message ->
                val event = objectMapper.readValue<JsonNode>(message)
                event.get("eventType")?.asText() == "RIDE_STATUS_CHANGED" &&
                event.get("rideId")?.asText() == savedRide.id.toString()
            }

            assertThat(eventMessage).isNotNull
            val event = objectMapper.readValue<JsonNode>(eventMessage!!)
            
            assertThat(event.get("eventType").asText()).isEqualTo("RIDE_STATUS_CHANGED")
            assertThat(event.get("rideId").asText()).isEqualTo(savedRide.id.toString())
            assertThat(event.get("previousStatus").asText()).isEqualTo(previousStatus)
            assertThat(event.get("currentStatus").asText()).isEqualTo(savedRide.status.name)
            assertThat(event.get("metadata")).isNotNull()
        }

        @Test
        @DisplayName("Should publish ride cancelled event")
        fun shouldPublishRideCancelledEvent() {
            // Given
            val savedRide = rideRepository.save(testRide)
            savedRide.updateStatus(RideStatus.CANCELLED)

            // When
            rideEventProducer.publishRideCancelled(savedRide)

            // Then
            val eventMessage = waitForKafkaMessage("ride-events") { message ->
                val event = objectMapper.readValue<JsonNode>(message)
                event.get("eventType")?.asText() == "RIDE_CANCELLED" &&
                event.get("rideId")?.asText() == savedRide.id.toString()
            }

            assertThat(eventMessage).isNotNull
            val event = objectMapper.readValue<JsonNode>(eventMessage!!)
            
            assertThat(event.get("eventType").asText()).isEqualTo("RIDE_CANCELLED")
            assertThat(event.get("rideId").asText()).isEqualTo(savedRide.id.toString())
            assertThat(event.get("status").asText()).isEqualTo("CANCELLED")
        }

        @Test
        @DisplayName("Should publish ride completed event")
        fun shouldPublishRideCompletedEvent() {
            // Given
            val savedRide = rideRepository.save(testRide)
            savedRide.updateStatus(RideStatus.COMPLETED)

            // When
            rideEventProducer.publishRideCompleted(savedRide)

            // Then
            val eventMessage = waitForKafkaMessage("ride-events") { message ->
                val event = objectMapper.readValue<JsonNode>(message)
                event.get("eventType")?.asText() == "RIDE_COMPLETED" &&
                event.get("rideId")?.asText() == savedRide.id.toString()
            }

            assertThat(eventMessage).isNotNull
            val event = objectMapper.readValue<JsonNode>(eventMessage!!)
            
            assertThat(event.get("eventType").asText()).isEqualTo("RIDE_COMPLETED")
            assertThat(event.get("rideId").asText()).isEqualTo(savedRide.id.toString())
            assertThat(event.get("status").asText()).isEqualTo("COMPLETED")
            assertThat(event.get("completedAt")).isNotNull()
        }
    }

    @Nested
    @DisplayName("Driver Call Event Publishing Tests")
    inner class DriverCallEventPublishingTests {

        @Test
        @DisplayName("Should publish driver call request event")
        fun shouldPublishDriverCallRequestEvent() {
            // Given
            val savedRide = rideRepository.save(testRide)
            val driverCalls = listOf(
                createTestDriverCallEntity(savedRide.id!!, UUID.randomUUID()),
                createTestDriverCallEntity(savedRide.id!!, UUID.randomUUID())
            )

            // When
            rideEventProducer.publishDriverCallRequest(savedRide.id!!, driverCalls)

            // Then
            val eventMessage = waitForKafkaMessage("driver-call-events") { message ->
                val event = objectMapper.readValue<JsonNode>(message)
                event.get("eventType")?.asText() == "DRIVER_CALL_REQUEST" &&
                event.get("rideId")?.asText() == savedRide.id.toString()
            }

            assertThat(eventMessage).isNotNull
            val event = objectMapper.readValue<JsonNode>(eventMessage!!)
            
            assertThat(event.get("eventType").asText()).isEqualTo("DRIVER_CALL_REQUEST")
            assertThat(event.get("rideId").asText()).isEqualTo(savedRide.id.toString())
            assertThat(event.get("driverCalls").isArray).isTrue()
            assertThat(event.get("driverCalls").size()).isEqualTo(2)
            
            val firstCall = event.get("driverCalls").get(0)
            assertThat(firstCall.get("driverId")).isNotNull()
            assertThat(firstCall.get("sequenceNumber")).isNotNull()
            assertThat(firstCall.get("estimatedArrivalSeconds")).isNotNull()
        }
    }

    @Nested
    @DisplayName("Event Error Handling Tests")
    inner class EventErrorHandlingTests {

        @Test
        @DisplayName("Should handle event publishing failures gracefully")
        fun shouldHandleEventPublishingFailuresGracefully() {
            // Given - Invalid ride (null ID)
            val invalidRide = createTestRide()
            // Don't save the ride, so ID will be null

            // When & Then - Should not throw exception
            assertDoesNotThrow {
                rideEventProducer.publishRideRequested(invalidRide)
            }
        }

        @Test
        @DisplayName("Should retry failed event publishing")
        fun shouldRetryFailedEventPublishing() {
            // Given
            val savedRide = rideRepository.save(testRide)

            // When - Publish event multiple times to test idempotency
            repeat(3) {
                rideEventProducer.publishRideRequested(savedRide)
            }

            // Then - Should receive events (might be multiple due to retries)
            val eventMessages = mutableListOf<String>()
            
            repeat(3) {
                try {
                    val message = waitForKafkaMessage("ride-events", 2000) { message ->
                        val event = objectMapper.readValue<JsonNode>(message)
                        event.get("eventType")?.asText() == "RIDE_REQUESTED" &&
                        event.get("rideId")?.asText() == savedRide.id.toString()
                    }
                    if (message != null) {
                        eventMessages.add(message)
                    }
                } catch (e: Exception) {
                    // Expected if no more messages
                    return@repeat
                }
            }

            assertThat(eventMessages).isNotEmpty
            
            // All messages should have same content
            val firstEvent = objectMapper.readValue<JsonNode>(eventMessages[0])
            eventMessages.forEach { message ->
                val event = objectMapper.readValue<JsonNode>(message)
                assertThat(event.get("rideId").asText()).isEqualTo(firstEvent.get("rideId").asText())
                assertThat(event.get("eventType").asText()).isEqualTo("RIDE_REQUESTED")
            }
        }
    }

    @Nested
    @DisplayName("Event Ordering Tests")
    inner class EventOrderingTests {

        @Test
        @DisplayName("Should maintain event ordering for ride lifecycle")
        fun shouldMaintainEventOrderingForRideLifecycle() {
            // Given
            val savedRide = rideRepository.save(testRide)
            val events = mutableListOf<JsonNode>()

            // When - Publish events in lifecycle order
            rideEventProducer.publishRideRequested(savedRide)
            Thread.sleep(100) // Small delay to ensure ordering
            
            rideEventProducer.publishRideMatched(savedRide, 300, null)
            Thread.sleep(100)
            
            savedRide.updateStatus(RideStatus.EN_ROUTE_TO_PICKUP)
            rideEventProducer.publishRideStatusChanged(savedRide, "MATCHED", mapOf())
            Thread.sleep(100)
            
            savedRide.updateStatus(RideStatus.COMPLETED)
            rideEventProducer.publishRideCompleted(savedRide)

            // Then - Collect events in order
            val timeoutMs = 5000L
            val startTime = System.currentTimeMillis()
            
            while (events.size < 4 && (System.currentTimeMillis() - startTime) < timeoutMs) {
                val records = kafkaConsumer.poll(Duration.ofMillis(100))
                for (record in records) {
                    if (record.topic() == "ride-events") {
                        val event = objectMapper.readValue<JsonNode>(record.value())
                        if (event.get("rideId")?.asText() == savedRide.id.toString()) {
                            events.add(event)
                        }
                    }
                }
            }

            // Verify event ordering
            assertThat(events.size).isGreaterThanOrEqualTo(4)
            
            val eventTypes = events.map { it.get("eventType").asText() }
            assertThat(eventTypes).contains("RIDE_REQUESTED")
            assertThat(eventTypes).contains("RIDE_MATCHED")
            assertThat(eventTypes).contains("RIDE_STATUS_CHANGED")
            assertThat(eventTypes).contains("RIDE_COMPLETED")
            
            // Verify timestamps are in order
            val timestamps = events.map { it.get("timestamp").asLong() }
            for (i in 1 until timestamps.size) {
                assertThat(timestamps[i]).isGreaterThanOrEqualTo(timestamps[i - 1])
            }
        }
    }

    @Nested
    @DisplayName("Event Schema Validation Tests")
    inner class EventSchemaValidationTests {

        @Test
        @DisplayName("Should validate ride event schema")
        fun shouldValidateRideEventSchema() {
            // Given
            val savedRide = rideRepository.save(testRide)

            // When
            rideEventProducer.publishRideRequested(savedRide)

            // Then
            val eventMessage = waitForKafkaMessage("ride-events") { message ->
                val event = objectMapper.readValue<JsonNode>(message)
                event.get("eventType")?.asText() == "RIDE_REQUESTED"
            }

            assertThat(eventMessage).isNotNull
            val event = objectMapper.readValue<JsonNode>(eventMessage!!)
            
            // Validate required fields
            assertThat(event.has("eventType")).isTrue()
            assertThat(event.has("rideId")).isTrue()
            assertThat(event.has("passengerId")).isTrue()
            assertThat(event.has("timestamp")).isTrue()
            assertThat(event.has("pickupLocation")).isTrue()
            assertThat(event.has("dropoffLocation")).isTrue()
            assertThat(event.has("fare")).isTrue()
            
            // Validate data types
            assertThat(event.get("timestamp").isNumber).isTrue()
            assertThat(event.get("pickupLocation").isObject).isTrue()
            assertThat(event.get("dropoffLocation").isObject).isTrue()
            assertThat(event.get("fare").isObject).isTrue()
            
            // Validate nested objects
            val pickupLocation = event.get("pickupLocation")
            assertThat(pickupLocation.has("latitude")).isTrue()
            assertThat(pickupLocation.has("longitude")).isTrue()
            assertThat(pickupLocation.get("latitude").isNumber).isTrue()
            assertThat(pickupLocation.get("longitude").isNumber).isTrue()
            
            val fare = event.get("fare")
            assertThat(fare.has("baseFare")).isTrue()
            assertThat(fare.has("totalFare")).isTrue()
            assertThat(fare.has("currency")).isTrue()
        }
    }

    private fun setupKafkaConsumer() {
        val consumerProps = KafkaTestUtils.consumerProps("integration-test-consumer", "false", kafkaContainer.bootstrapServers)
        consumerProps[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
        consumerProps[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
        consumerProps[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest"
        
        kafkaConsumer = KafkaConsumer(consumerProps)
        kafkaConsumer.subscribe(listOf("ride-events", "matching-events", "driver-call-events"))
    }

    private fun waitForKafkaMessage(
        topic: String, 
        timeoutMs: Long = 5000,
        predicate: (String) -> Boolean
    ): String? {
        val endTime = System.currentTimeMillis() + timeoutMs
        
        while (System.currentTimeMillis() < endTime) {
            val records = kafkaConsumer.poll(Duration.ofMillis(100))
            for (record in records) {
                if (record.topic() == topic && predicate(record.value())) {
                    return record.value()
                }
            }
        }
        
        return null
    }

    private fun createTestRide(): Ride {
        return Ride(
            passengerId = UUID.randomUUID(),
            pickupLocation = Location(37.5665, 126.9780, "Seoul Station", "8830e1d8dffffff"),
            dropoffLocation = Location(37.5759, 126.9768, "Myeongdong", "8830e1d89ffffff"),
            fare = Fare(
                baseFare = BigDecimal("5000"),
                surgeMultiplier = BigDecimal("1.0"),
                totalFare = BigDecimal("5000"),
                currency = "KRW"
            )
        )
    }

    private fun createTestDriverCall(rideId: UUID, driverId: UUID): Map<String, Any> {
        return mapOf(
            "rideId" to rideId,
            "driverId" to driverId,
            "sequenceNumber" to 1,
            "estimatedArrivalSeconds" to 300,
            "estimatedFare" to 5000,
            "driverLocation" to mapOf(
                "latitude" to 37.5660,
                "longitude" to 126.9775,
                "h3Index" to "8830e1d8dffffff"
            )
        )
    }

    private fun createTestDriverCallEntity(rideId: UUID, driverId: UUID): com.ddakta.matching.domain.entity.DriverCall {
        val ride = rideRepository.findById(rideId).orElseThrow { 
            IllegalArgumentException("Ride not found: $rideId") 
        }
        return com.ddakta.matching.domain.entity.DriverCall(
            ride = ride,
            driverId = driverId,
            sequenceNumber = 1,
            estimatedArrivalSeconds = 300,
            estimatedFare = BigDecimal("5000"),
            expiresAt = java.time.LocalDateTime.now().plusMinutes(5),
            driverLocation = Location(37.5660, 126.9775, null, "8830e1d8dffffff")
        )
    }
}