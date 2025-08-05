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

        // 제외된 드라이버와 부적격 드라이버 필터링
        val eligibleDrivers = availableDrivers
            .filter { it.driverId !in excludedDriverIds }
            .filter { matchingScoreCalculator.isDriverEligible(it) }
            .filter { driver ->
                val distance = distanceCalculator.calculate(pickupLocation, driver.currentLocation)
                distance <= matchingProperties.search.radiusKm * 1000 // km를 미터로 변환
            }

        if (eligibleDrivers.isEmpty()) {
            logger.warn { "No eligible drivers after filtering" }
            return emptyList()
        }

        // 각 드라이버의 점수와 거리 계산
        val driversWithMetrics = eligibleDrivers.map { driver ->
            val distance = distanceCalculator.calculate(pickupLocation, driver.currentLocation)
            val estimatedArrival = distanceCalculator.estimateTravelTime(distance)
            
            DriverWithMetrics(
                driver = driver,
                distance = distance,
                estimatedArrivalSeconds = estimatedArrival,
                score = 0.0 // 필요시 별도로 계산될 예정
            )
        }

        // 선택 전략 적용
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
        // 여러 요소를 조합하여 정렬
        return drivers
            .sortedWith(
                compareBy(
                    // 1순위: 매우 가까운 드라이버 (< 500m) 평점순
                    { if (it.distance < 500) -1 else 0 },
                    { -it.driver.rating },
                    // 2순위: 가까운 드라이버 (< 1km) 수락률순
                    { if (it.distance < 1000) -1 else 0 },
                    { -it.driver.acceptanceRate },
                    // 3순위: 나머지 모두 거리순
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
            // 필요시 차량 유형 확인
            if (rideRequirements.requiredVehicleType != null) {
                driver.vehicleType == rideRequirements.requiredVehicleType
            } else true
        }.filter { driver ->
            // 최소 평점 확인
            driver.rating >= rideRequirements.minimumRating
        }.filter { driver ->
            // 최소 완료 여행 수 확인
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