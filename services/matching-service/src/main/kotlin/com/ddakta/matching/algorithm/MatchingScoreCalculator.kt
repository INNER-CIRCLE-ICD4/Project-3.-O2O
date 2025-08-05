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
                // 점수를 비용으로 변환 (높은 점수 = 낮은 비용)
                matrix[i][j] = 1.0 - score.totalScore
            }
        }

        return matrix
    }

    private fun normalizeDistance(distanceMeters: Double): Double {
        // 거리를 0-1 범위로 정규화
        // 0m = 점수 1.0, 5000m+ = 점수 0.0
        val maxDistance = 5000.0
        val normalized = 1.0 - min(distanceMeters / maxDistance, 1.0)
        return max(0.0, normalized)
    }

    private fun normalizeRating(rating: Double): Double {
        // 평점을 1-5 범위에서 0-1 범위로 정규화
        // 1.0 = 점수 0.0, 5.0 = 점수 1.0
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
    
    fun calculateScore(distanceMeters: Double, driverRating: Double, acceptanceRate: Double, completionRate: Double): Double {
        val distanceScore = normalizeDistance(distanceMeters)
        val ratingScore = normalizeRating(driverRating)
        val acceptanceScore = kotlin.math.max(0.0, kotlin.math.min(1.0, acceptanceRate))
        val completionScore = kotlin.math.max(0.0, kotlin.math.min(1.0, completionRate))
        
        // 속성에서 가중치를 사용하거나 기본값 사용
        val distanceWeight = matchingProperties.distanceWeight
        val ratingWeight = matchingProperties.ratingWeight
        val acceptanceWeight = matchingProperties.acceptanceWeight
        val completionWeight = 1.0 - distanceWeight - ratingWeight - acceptanceWeight
        
        return (distanceScore * distanceWeight) + 
               (ratingScore * ratingWeight) + 
               (acceptanceScore * acceptanceWeight) + 
               (completionScore * completionWeight)
    }
}