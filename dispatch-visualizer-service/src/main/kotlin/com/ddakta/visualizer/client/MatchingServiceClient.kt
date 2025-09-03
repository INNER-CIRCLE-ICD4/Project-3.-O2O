package com.ddakta.visualizer.client

import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import java.util.UUID

@Component
class MatchingServiceClient(
    private val restTemplate: RestTemplate,
) {
    @Value("\${matching.service.api.url}")
    private lateinit var matchingServiceApiUrl: String

    private val logger = KotlinLogging.logger {}

    fun requestRide(
        passengerId: String,
        pickupLat: Double, pickupLon: Double,
        dropoffLat: Double, dropoffLon: Double
    ) {
        val requestBody = mapOf(
            "passengerId" to passengerId,
            "pickupLocation" to mapOf(
                "latitude" to pickupLat,
                "longitude" to pickupLon
            ),
            "dropoffLocation" to mapOf(
                "latitude" to dropoffLat,
                "longitude" to dropoffLon
            )
        )

        try {
            val response = restTemplate.postForEntity(
                "$matchingServiceApiUrl/api/v1/rides",
                requestBody,
                String::class.java
            )
            logger.info { "Ride request to matching-service successful: ${response.statusCode}" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to send ride request to matching-service: ${e.message}" }
        }
    }
}
