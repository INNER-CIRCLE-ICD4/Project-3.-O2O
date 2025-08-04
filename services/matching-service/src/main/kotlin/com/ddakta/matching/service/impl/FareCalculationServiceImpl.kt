package com.ddakta.matching.service.impl

import com.ddakta.matching.config.MatchingProperties
import com.ddakta.matching.domain.vo.Fare
import com.ddakta.matching.domain.vo.Location
import com.ddakta.matching.service.FareCalculationService
import com.ddakta.matching.utils.DistanceCalculator
import mu.KotlinLogging
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode

@Service
class FareCalculationServiceImpl(
    private val matchingProperties: MatchingProperties
) : FareCalculationService {

    private val logger = KotlinLogging.logger {}

    companion object {
        // 기본 요금 체계 (KRW)
        const val DEFAULT_BASE_FARE = 4000.0
        const val DEFAULT_PER_KM_RATE = 1000.0
        const val DEFAULT_PER_MINUTE_RATE = 200.0
        const val MINIMUM_FARE = 5000.0
        
        // 차량 타입별 요금 배수
        val VEHICLE_TYPE_MULTIPLIERS = mapOf(
            "STANDARD" to 1.0,
            "PREMIUM" to 1.5,
            "BLACK" to 2.0,
            "VAN" to 1.8,
            "ELECTRIC" to 1.2
        )
        
        // 시간대별 요금 배수
        val TIME_OF_DAY_MULTIPLIERS = mapOf(
            "PEAK_MORNING" to 1.2,    // 07:00-09:00
            "PEAK_EVENING" to 1.3,    // 18:00-20:00
            "LATE_NIGHT" to 1.4,      // 00:00-05:00
            "NORMAL" to 1.0
        )
    }

    override fun calculateEstimatedFare(
        pickupLocation: Location,
        dropoffLocation: Location,
        surgeMultiplier: Double,
        vehicleType: String?
    ): Fare {
        // 거리 계산 (미터)
        val distanceMeters = DistanceCalculator.calculateDistance(
            pickupLocation.latitude,
            pickupLocation.longitude,
            dropoffLocation.latitude,
            dropoffLocation.longitude
        )
        
        // 예상 시간 계산 (초) - 평균 속도 30km/h 가정
        val estimatedDurationSeconds = (distanceMeters / 1000.0 * 120).toInt() // 2분/km
        
        // 기본 요금
        val baseFare = getBaseFare(vehicleType)
        
        // 거리 요금
        val distanceFare = getPerKilometerRate(vehicleType)
            .multiply(BigDecimal.valueOf(distanceMeters / 1000.0))
        
        // 시간 요금
        val timeFare = getPerMinuteRate(vehicleType)
            .multiply(BigDecimal.valueOf(estimatedDurationSeconds / 60.0))
        
        // 시간대 배수
        val timeOfDayMultiplier = getTimeOfDayMultiplier()
        
        // 총 예상 요금 계산
        var totalFare = baseFare.add(distanceFare).add(timeFare)
        totalFare = totalFare.multiply(BigDecimal.valueOf(timeOfDayMultiplier))
        totalFare = totalFare.multiply(BigDecimal.valueOf(surgeMultiplier))
        
        // 최소 요금 적용
        totalFare = totalFare.max(BigDecimal.valueOf(MINIMUM_FARE))
        
        // 100원 단위로 반올림
        totalFare = totalFare.divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100))
        
        logger.debug { 
            "Estimated fare: distance=${distanceMeters}m, duration=${estimatedDurationSeconds}s, " +
            "surge=$surgeMultiplier, total=$totalFare"
        }
        
        return Fare(
            baseFare = baseFare,
            surgeMultiplier = BigDecimal.valueOf(surgeMultiplier),
            totalFare = totalFare,
            currency = "KRW"
        )
    }

    override fun calculateFinalFare(
        baseFare: BigDecimal,
        surgeMultiplier: BigDecimal,
        distanceMeters: Int,
        durationSeconds: Int,
        vehicleType: String?
    ): BigDecimal {
        // 차량 타입 배수
        val vehicleMultiplier = VEHICLE_TYPE_MULTIPLIERS[vehicleType ?: "STANDARD"] ?: 1.0
        
        // 거리 요금
        val distanceFare = getPerKilometerRate(vehicleType)
            .multiply(BigDecimal.valueOf(distanceMeters / 1000.0))
        
        // 시간 요금
        val timeFare = getPerMinuteRate(vehicleType)
            .multiply(BigDecimal.valueOf(durationSeconds / 60.0))
        
        // 시간대 배수
        val timeOfDayMultiplier = getTimeOfDayMultiplier()
        
        // 총 요금 계산
        var totalFare = baseFare.add(distanceFare).add(timeFare)
        totalFare = totalFare.multiply(BigDecimal.valueOf(timeOfDayMultiplier))
        totalFare = totalFare.multiply(surgeMultiplier)
        
        // 최소 요금 적용
        totalFare = totalFare.max(BigDecimal.valueOf(MINIMUM_FARE))
        
        // 100원 단위로 반올림
        totalFare = totalFare.divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100))
        
        logger.info { 
            "Final fare calculated: distance=${distanceMeters}m, duration=${durationSeconds}s, " +
            "surge=$surgeMultiplier, total=$totalFare"
        }
        
        return totalFare
    }

    override fun getBaseFare(vehicleType: String?): BigDecimal {
        // 기본 요금 계산 - 차량 타입별 배수 적용
        val baseRate = DEFAULT_BASE_FARE
        val vehicleMultiplier = VEHICLE_TYPE_MULTIPLIERS[vehicleType ?: "STANDARD"] ?: 1.0
        
        return BigDecimal.valueOf(baseRate * vehicleMultiplier)
    }

    override fun getPerKilometerRate(vehicleType: String?): BigDecimal {
        // 킬로미터당 요금 계산 - 차량 타입별 배수 적용
        val kmRate = DEFAULT_PER_KM_RATE
        val vehicleMultiplier = VEHICLE_TYPE_MULTIPLIERS[vehicleType ?: "STANDARD"] ?: 1.0
        
        return BigDecimal.valueOf(kmRate * vehicleMultiplier)
    }

    override fun getPerMinuteRate(vehicleType: String?): BigDecimal {
        // 분당 요금 계산 - 차량 타입별 배수 적용
        val minuteRate = DEFAULT_PER_MINUTE_RATE
        val vehicleMultiplier = VEHICLE_TYPE_MULTIPLIERS[vehicleType ?: "STANDARD"] ?: 1.0
        
        return BigDecimal.valueOf(minuteRate * vehicleMultiplier)
    }

    private fun getTimeOfDayMultiplier(): Double {
        val hour = java.time.LocalTime.now().hour
        
        return when (hour) {
            in 7..8 -> TIME_OF_DAY_MULTIPLIERS["PEAK_MORNING"]!!
            in 18..19 -> TIME_OF_DAY_MULTIPLIERS["PEAK_EVENING"]!!
            in 0..4 -> TIME_OF_DAY_MULTIPLIERS["LATE_NIGHT"]!!
            else -> TIME_OF_DAY_MULTIPLIERS["NORMAL"]!!
        }
    }
}