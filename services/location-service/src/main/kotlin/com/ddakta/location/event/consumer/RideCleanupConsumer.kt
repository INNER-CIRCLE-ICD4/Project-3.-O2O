package com.ddakta.location.event.consumer

import com.ddakta.location.event.publisher.RideCleanupEvent
import com.ddakta.location.repository.RedisGeoLocationRepository
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class RideCleanupConsumer(
    private val redisGeoLocationRepository: RedisGeoLocationRepository
) {
    @KafkaListener(topics = ["ride-cleanup"], groupId = "location-service-group")
    fun handle(event: RideCleanupEvent) {
        // Redis GEO에서 위치 삭제
        redisGeoLocationRepository.removeLocation(event.driverId)
        // (필요시) 로컬 캐시도 초기화
    }
}
