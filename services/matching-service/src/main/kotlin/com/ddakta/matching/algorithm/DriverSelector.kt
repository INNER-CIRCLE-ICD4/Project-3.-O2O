package com.ddakta.matching.algorithm

import com.ddakta.matching.config.MatchingProperties
import com.ddakta.matching.domain.vo.Location
import com.ddakta.matching.dto.internal.AvailableDriver
import com.ddakta.matching.utils.DistanceCalculator
import mu.KotlinLogging
import org.springframework.stereotype.Component
import java.util.*

@Component
class DriverSelector(
    private val matchingScoreCalculator: MatchingScoreCalculator,
    private val distanceCalculator: DistanceCalculator,
    private val matchingProperties: MatchingProperties
) {

    private val logger = KotlinLogging.logger {}

    fun selectDriversForCall(
        availableDrivers: List<AvailableDriver>,
        pickupLocation: Location,
        excludedDriverIds: Set<UUID> = emptySet(),
        maxDrivers: Int = 3
    ): List<AvailableDriver> {
        
        if (availableDrivers.isEmpty()) {
            logger.warn { "No available drivers to select from" }
            return emptyList()
        }

        // Filter out excluded drivers and ineligible drivers
        val eligibleDrivers = availableDrivers
            .filter { it.driverId !in excludedDriverIds }
            .filter { matchingScoreCalculator.isDriverEligible(it) }
            .filter { driver ->
                val distance = distanceCalculator.calculate(pickupLocation, driver.currentLocation)
                distance <= matchingProperties.search.radiusKm * 1000 // Convert km to meters
            }

        if (eligibleDrivers.isEmpty()) {
            logger.warn { "No eligible drivers after filtering" }
            return emptyList()
        }

        // Calculate scores and distances for each driver
        val driversWithMetrics = eligibleDrivers.map { driver ->
            val distance = distanceCalculator.calculate(pickupLocation, driver.currentLocation)
            val estimatedArrival = distanceCalculator.estimateTravelTime(distance)
            
            DriverWithMetrics(
                driver = driver,
                distance = distance,
                estimatedArrivalSeconds = estimatedArrival,
                score = 0.0 // Will be calculated separately if needed
            )
        }

        // Apply selection strategy
        return when (SelectionStrategy.BALANCED) {
            SelectionStrategy.CLOSEST_FIRST -> selectClosestDrivers(driversWithMetrics, maxDrivers)
            SelectionStrategy.HIGHEST_RATED -> selectHighestRatedDrivers(driversWithMetrics, maxDrivers)
            SelectionStrategy.BALANCED -> selectBalancedDrivers(driversWithMetrics, maxDrivers)
        }
    }

    private fun selectClosestDrivers(
        drivers: List<DriverWithMetrics>,
        maxDrivers: Int
    ): List<AvailableDriver> {
        return drivers
            .sortedBy { it.distance }
            .take(maxDrivers)
            .map { it.driver }
    }

    private fun selectHighestRatedDrivers(
        drivers: List<DriverWithMetrics>,
        maxDrivers: Int
    ): List<AvailableDriver> {
        return drivers
            .sortedByDescending { it.driver.rating }
            .take(maxDrivers)
            .map { it.driver }
    }

    private fun selectBalancedDrivers(
        drivers: List<DriverWithMetrics>,
        maxDrivers: Int
    ): List<AvailableDriver> {
        // Sort by a combination of factors
        return drivers
            .sortedWith(
                compareBy(
                    // First tier: Very close drivers (< 500m) by rating
                    { if (it.distance < 500) -1 else 0 },
                    { -it.driver.rating },
                    // Second tier: Close drivers (< 1km) by acceptance rate
                    { if (it.distance < 1000) -1 else 0 },
                    { -it.driver.acceptanceRate },
                    // Third tier: All others by distance
                    { it.distance }
                )
            )
            .take(maxDrivers)
            .map { it.driver }
    }

    fun rankDrivers(
        drivers: List<AvailableDriver>,
        pickupLocation: Location
    ): List<RankedDriver> {
        return drivers.mapIndexed { index, driver ->
            val distance = distanceCalculator.calculate(pickupLocation, driver.currentLocation)
            val estimatedArrival = distanceCalculator.estimateTravelTime(distance)
            
            RankedDriver(
                driver = driver,
                rank = index + 1,
                distance = distance,
                estimatedArrivalSeconds = estimatedArrival
            )
        }
    }

    fun filterByBusinessRules(
        drivers: List<AvailableDriver>,
        rideRequirements: RideRequirements
    ): List<AvailableDriver> {
        return drivers.filter { driver ->
            // Check vehicle type if required
            if (rideRequirements.requiredVehicleType != null) {
                driver.vehicleType == rideRequirements.requiredVehicleType
            } else true
        }.filter { driver ->
            // Check minimum rating
            driver.rating >= rideRequirements.minimumRating
        }.filter { driver ->
            // Check minimum completed trips
            driver.completedTrips >= rideRequirements.minimumCompletedTrips
        }
    }

    data class DriverWithMetrics(
        val driver: AvailableDriver,
        val distance: Double,
        val estimatedArrivalSeconds: Int,
        val score: Double
    )

    data class RankedDriver(
        val driver: AvailableDriver,
        val rank: Int,
        val distance: Double,
        val estimatedArrivalSeconds: Int
    )

    data class RideRequirements(
        val requiredVehicleType: String? = null,
        val minimumRating: Double = 4.0,
        val minimumCompletedTrips: Int = 10
    )

    enum class SelectionStrategy {
        CLOSEST_FIRST,
        HIGHEST_RATED,
        BALANCED
    }
}