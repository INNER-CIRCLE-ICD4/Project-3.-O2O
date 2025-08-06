package com.ddakta.location.service

import com.ddakta.location.domain.LocationUpdate
import com.ddakta.location.repository.RedisGeoLocationRepository
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service

@Service
class LocationService(
    private val redisGeoLocationRepository: RedisGeoLocationRepository,
    private val kafkaTemplate: KafkaTemplate<String, LocationUpdate>
) {
    fun updateLocation(update: LocationUpdate) {
        redisGeoLocationRepository.updateLocation(
            update.driverId, update.latitude, update.longitude
        )
        kafkaTemplate.send("driver-location-updated", update.driverId, update)
    }
}
