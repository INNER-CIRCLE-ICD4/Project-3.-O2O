package com.ddakta.matching.service

import com.ddakta.matching.domain.vo.Fare
import com.ddakta.matching.domain.vo.Location
import java.math.BigDecimal

interface FareCalculationService {
    fun calculateEstimatedFare(
        pickupLocation: Location,
        dropoffLocation: Location,
        surgeMultiplier: Double = 1.0,
        vehicleType: String? = null
    ): Fare
    
    fun calculateFinalFare(
        baseFare: BigDecimal,
        surgeMultiplier: BigDecimal,
        distanceMeters: Int,
        durationSeconds: Int,
        vehicleType: String? = null
    ): BigDecimal
    
    fun getBaseFare(vehicleType: String?): BigDecimal
    
    fun getPerKilometerRate(vehicleType: String?): BigDecimal
    
    fun getPerMinuteRate(vehicleType: String?): BigDecimal
}