package com.ddakta.location.event.consumer

import com.ddakta.location.event.publisher.RideCleanupEvent
import com.ddakta.location.repository.RedisGeoLocationRepository
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class RideCleanupConsumer(
    private val redisGeoLocationRepository: RedisGeoLocationRepository
) {

    private val logger = LoggerFactory.getLogger(this::class.java)

    @KafkaListener(topics = ["ride-cleanup"], groupId = "location-service-group")
    fun handle(event: RideCleanupEvent) {
        logger.info("Received ride cleanup event: {}", event)
        try {
            val driverLocation = redisGeoLocationRepository.getDriverLocation(event.driverId)
            val h3Index = driverLocation?.get("h3Index") as? String

            redisGeoLocationRepository.removeDriverLocation(event.driverId, h3Index)
            logger.info("Successfully removed driver location for driverId: {}", event.driverId)
        } catch (e: Exception) {
            logger.error("Error while cleaning up driver location for driverId: {}", event.driverId, e)
            // 재시도 로직 또는 에러 큐로 전송 등의 추가적인 에러 처리 고려
        }
    }
}
