package com.ddakta.location.service

import com.ddakta.location.domain.LocationUpdate
import com.ddakta.location.dto.NearbyDriverDto
import com.ddakta.location.repository.RedisGeoLocationRepository
import org.springframework.data.geo.GeoResults
import org.springframework.data.redis.connection.RedisGeoCommands
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

    open fun findNearbyDrivers(
        latitude: Double,
        longitude: Double,
        radius: Double
    ): List<NearbyDriverDto> {
        // (1) Redis에서 GeoResults<GeoLocation> 반환
        val geoResults = redisGeoLocationRepository.findNearby(latitude, longitude, radius)

        // (2) content → List<GeoResult<GeoLocation>> → map
        return geoResults.content.map { result ->
            // GeoResult<T>.content 가 실제 RedisGeoCommands.GeoLocation<String>
            val loc = result.content
            val p   = loc.point

            NearbyDriverDto(
                driverId  = loc.name,   // Redis에 저장된 member (driverId)
                latitude  = p.y,        // Point.y = latitude
                longitude = p.x         // Point.x = longitude
            )
        }
    }

}
