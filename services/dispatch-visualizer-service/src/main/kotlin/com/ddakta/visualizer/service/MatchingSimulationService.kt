package com.ddakta.visualizer.service

import com.ddakta.visualizer.client.MatchingServiceClient
import mu.KotlinLogging
import org.springframework.stereotype.Service
import java.util.UUID
import kotlin.random.Random

@Service
class MatchingSimulationService(
    private val matchingServiceClient: MatchingServiceClient
) {

    private val logger = KotlinLogging.logger {}

    fun requestRide() {
        val passengerId = UUID.randomUUID().toString()
        val pickupLat = 37.5 + (Random.nextDouble() - 0.5) * 0.05
        val pickupLon = 127.0 + (Random.nextDouble() - 0.5) * 0.05
        val dropoffLat = 37.5 + (Random.nextDouble() - 0.5) * 0.05
        val dropoffLon = 127.0 + (Random.nextDouble() - 0.5) * 0.05

        matchingServiceClient.requestRide(
            passengerId,
            pickupLat, pickupLon,
            dropoffLat, dropoffLon
        )
        logger.info { "Ride request sent for passenger $passengerId" }
    }
}
