package com.ddakta.location.event.consumer

import com.ddakta.location.event.publisher.DriverStatusChangedEvent
import com.ddakta.location.repository.RedisGeoDriverRepository
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class DriverStatusChangedConsumer(
    private val redisGeoDriverRepository: RedisGeoDriverRepository
) {
    @KafkaListener(topics = ["driver-status-changed"], groupId = "location-service-group")
    fun handle(event: DriverStatusChangedEvent) {
        when (event.status.lowercase()) {
            "online"  -> redisGeoDriverRepository.addDriver(event.h3Index, event.driverId)
            "offline", "busy" -> redisGeoDriverRepository.removeDriver(event.h3Index, event.driverId)
        }
    }
}
