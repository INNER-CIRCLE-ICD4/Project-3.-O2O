package com.ddakta.matching.algorithm

import com.ddakta.matching.config.MatchingProperties
import com.ddakta.matching.domain.entity.MatchingRequest
import com.ddakta.matching.domain.vo.Location
import com.ddakta.matching.domain.vo.MatchingScore
import com.ddakta.matching.dto.internal.AvailableDriver
import com.ddakta.matching.utils.DistanceCalculator
import mu.KotlinLogging
import org.springframework.stereotype.Component
import kotlin.math.max
import kotlin.math.min

@Component
class MatchingScoreCalculator(
    private val distanceCalculator: DistanceCalculator,
    private val matchingProperties: MatchingProperties
) {

    private val logger = KotlinLogging.logger {}

    fun calculateScore(
        request: MatchingRequest,
        driver: AvailableDriver,
        pickupLocation: Location
    ): MatchingScore {
        val distance = distanceCalculator.calculate(
            pickupLocation,
            driver.currentLocation
        )

        val distanceScore = normalizeDistance(distance)
        val ratingScore = normalizeRating(driver.rating)
        val acceptanceScore = driver.acceptanceRate

        val totalScore = (distanceScore * matchingProperties.distanceWeight +
                         ratingScore * matchingProperties.ratingWeight +
                         acceptanceScore * matchingProperties.acceptanceWeight)

        logger.debug { 
            "Score calculation for driver ${driver.driverId}: " +
            "distance=$distance (score=$distanceScore), " +
            "rating=${driver.rating} (score=$ratingScore), " +
            "acceptance=${driver.acceptanceRate}, " +
            "total=$totalScore"
        }

        return MatchingScore.calculate(
            distanceScore = distanceScore,
            ratingScore = ratingScore,
            acceptanceScore = acceptanceScore,
            distanceWeight = matchingProperties.distanceWeight,
            ratingWeight = matchingProperties.ratingWeight,
            acceptanceWeight = matchingProperties.acceptanceWeight
        )
    }

    fun calculateCostMatrix(
        requests: List<Pair<MatchingRequest, Location>>,
        drivers: List<AvailableDriver>
    ): Array<DoubleArray> {
        val matrix = Array(requests.size) { DoubleArray(drivers.size) }

        for (i in requests.indices) {
            for (j in drivers.indices) {
                val score = calculateScore(
                    requests[i].first,
                    drivers[j],
                    requests[i].second
                )
                // Convert score to cost (higher score = lower cost)
                matrix[i][j] = 1.0 - score.totalScore
            }
        }

        return matrix
    }

    private fun normalizeDistance(distanceMeters: Double): Double {
        // Normalize distance to 0-1 scale
        // 0m = score 1.0, 5000m+ = score 0.0
        val maxDistance = 5000.0
        val normalized = 1.0 - min(distanceMeters / maxDistance, 1.0)
        return max(0.0, normalized)
    }

    private fun normalizeRating(rating: Double): Double {
        // Normalize rating from 1-5 scale to 0-1 scale
        // 1.0 = score 0.0, 5.0 = score 1.0
        return max(0.0, min(1.0, (rating - 1.0) / 4.0))
    }

    fun selectTopDrivers(
        drivers: List<Pair<AvailableDriver, MatchingScore>>,
        maxCount: Int = 3
    ): List<AvailableDriver> {
        return drivers
            .sortedByDescending { it.second.totalScore }
            .take(maxCount)
            .map { it.first }
    }

    fun isDriverEligible(driver: AvailableDriver, minAcceptanceRate: Double = 0.5): Boolean {
        return driver.acceptanceRate >= minAcceptanceRate && 
               driver.rating >= 3.0 &&
               driver.isAvailable
    }
    
    // TODO: 임시 추가 - 컴파일 오류 해결용
    fun calculateScore(distanceMeters: Double, driverRating: Double, acceptanceRate: Double, completionRate: Double): Double {
        val distanceScore = normalizeDistance(distanceMeters)
        val ratingScore = normalizeRating(driverRating)
        val acceptanceScore = acceptanceRate
        val completionScore = completionRate
        
        return (distanceScore * 0.4) + (ratingScore * 0.3) + (acceptanceScore * 0.2) + (completionScore * 0.1)
    }
}