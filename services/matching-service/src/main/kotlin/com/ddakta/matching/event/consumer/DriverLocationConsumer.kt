package com.ddakta.matching.event.consumer

import com.ddakta.matching.dto.response.RideLocationUpdateDto
import com.ddakta.matching.event.model.driver.DriverAvailabilityChangedEvent
import com.ddakta.matching.event.model.driver.DriverLocationUpdatedEvent
import com.ddakta.matching.event.model.driver.DriverStatusChangedEvent
import com.ddakta.matching.service.RideService
import mu.KotlinLogging
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.kafka.annotation.KafkaListener
import java.util.*
import org.springframework.kafka.support.Acknowledgment
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.messaging.handler.annotation.Header
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class DriverLocationConsumer(
    private val rideService: RideService,
    private val redisTemplate: RedisTemplate<String, String>,
    private val messagingTemplate: SimpMessagingTemplate
) {

    private val logger = KotlinLogging.logger {}

    companion object {
        const val DRIVER_LOCATION_KEY = "driver:location:"
        const val DRIVER_STATUS_KEY = "driver:status:"
        const val LOCATION_TTL_MINUTES = 5L
    }

    @KafkaListener(
        topics = ["driver-location-updated"],
        groupId = "matching-service-location",
        containerFactory = "kafkaListenerContainerFactoryWithDlq"
    )
    fun handleDriverLocationUpdate(
        @Payload event: DriverLocationUpdatedEvent,
        @Header(KafkaHeaders.RECEIVED_TOPIC) topic: String,
        @Header(KafkaHeaders.RECEIVED_TIMESTAMP) timestamp: Long,
        acknowledgment: Acknowledgment
    ) {
        logger.debug {
            "Driver ${event.driverId} location update: ${event.latitude}, ${event.longitude}"
        }

        try {
            // Redis에 드라이버 위치 캐시
            cacheDriverLocation(event)

            // 드라이버가 활성 운행을 가지고 있는지 확인
            val activeRide = rideService.getActiveRideForDriver(event.driverId)

            if (activeRide != null) {
                // WebSocket을 통해 운행 참가자에게 위치 업데이트 브로드캐스트
                val locationUpdate = RideLocationUpdateDto(
                    rideId = activeRide.id,
                    driverId = event.driverId,
                    latitude = event.latitude,
                    longitude = event.longitude,
                    heading = event.heading,
                    speed = event.speed,
                    accuracy = event.accuracy,
                    timestamp = event.timestamp
                )

                messagingTemplate.convertAndSend(
                    "/topic/ride/${activeRide.id}/location",
                    locationUpdate
                )

                logger.debug {
                    "Broadcasted location update for ride ${activeRide.id}"
                }
            }

            acknowledgment.acknowledge()

        } catch (e: Exception) {
            logger.error(e) { "Error processing location update for driver ${event.driverId}" }
            throw e
        }
    }

    @KafkaListener(
        topics = ["driver-status-changed"],
        groupId = "matching-service-location",
        containerFactory = "kafkaListenerContainerFactory"
    )
    fun handleDriverStatusChanged(
        @Payload event: DriverStatusChangedEvent,
        acknowledgment: Acknowledgment
    ) {
        logger.info {
            "Driver ${event.driverId} status changed: ${event.previousStatus} -> ${event.newStatus}"
        }

        try {
            // 캐시에서 드라이버 상태 업데이트
            val key = "$DRIVER_STATUS_KEY${event.driverId}"
            redisTemplate.opsForValue().set(
                key,
                event.newStatus,
                LOCATION_TTL_MINUTES,
                TimeUnit.MINUTES
            )

            // 상태별 로직 처리
            when (event.newStatus) {
                "OFFLINE" -> {
                    // 가용 풀에서 드라이버 제거
                    removeDriverFromAvailablePool(event.driverId)
                }
                "ONLINE" -> {
                    // 운행 중이 아니면 가용 풀에 드라이버 추가
                    val activeRide = rideService.getActiveRideForDriver(event.driverId)
                    if (activeRide == null) {
                        addDriverToAvailablePool(event.driverId, event.h3Index)
                    }
                }
                "ON_TRIP" -> {
                    // 드라이버가 활성 운행 중
                    removeDriverFromAvailablePool(event.driverId)
                }
            }

            acknowledgment.acknowledge()

        } catch (e: Exception) {
            logger.error(e) { "Error processing status change for driver ${event.driverId}" }
            throw e
        }
    }

    @KafkaListener(
        topics = ["driver-availability-changed"],
        groupId = "matching-service-location",
        containerFactory = "kafkaListenerContainerFactory"
    )
    fun handleDriverAvailabilityChanged(
        @Payload event: DriverAvailabilityChangedEvent,
        acknowledgment: Acknowledgment
    ) {
        logger.info {
            "Driver ${event.driverId} availability changed: ${event.isAvailable}"
        }

        try {
            if (event.isAvailable) {
                event.h3Index?.let { h3Index ->
                    addDriverToAvailablePool(event.driverId, h3Index)
                }
            } else {
                removeDriverFromAvailablePool(event.driverId)
            }

            acknowledgment.acknowledge()

        } catch (e: Exception) {
            logger.error(e) { "Error processing availability change for driver ${event.driverId}" }
            throw e
        }
    }

    private fun cacheDriverLocation(event: DriverLocationUpdatedEvent) {
        val locationData = "${event.latitude},${event.longitude},${event.h3Index}"
        val key = "$DRIVER_LOCATION_KEY${event.driverId}"

        redisTemplate.opsForValue().set(
            key,
            locationData,
            LOCATION_TTL_MINUTES,
            TimeUnit.MINUTES
        )

        // H3 인덱스 기반 위치 추적도 업데이트
        if (event.isOnline) {
            val h3Key = "drivers:h3:${event.h3Index}"
            redisTemplate.opsForSet().add(h3Key, event.driverId.toString())
            redisTemplate.expire(h3Key, LOCATION_TTL_MINUTES, TimeUnit.MINUTES)
        }
    }

    private fun addDriverToAvailablePool(driverId: UUID, h3Index: String?) {
        if (h3Index != null) {
            val availableKey = "drivers:available:$h3Index"
            redisTemplate.opsForSet().add(availableKey, driverId.toString())
            redisTemplate.expire(availableKey, LOCATION_TTL_MINUTES, TimeUnit.MINUTES)

            logger.debug { "Added driver $driverId to available pool in $h3Index" }
        }
    }

    private fun removeDriverFromAvailablePool(driverId: UUID) {
        // 모든 H3 인덱스에서 제거 (어느 것인지 모를 수 있음)
        val pattern = "drivers:available:*"
        val keys = redisTemplate.keys(pattern)

        keys.forEach { key ->
            redisTemplate.opsForSet().remove(key, driverId.toString())
        }

        logger.debug { "Removed driver $driverId from available pools" }
    }
}
