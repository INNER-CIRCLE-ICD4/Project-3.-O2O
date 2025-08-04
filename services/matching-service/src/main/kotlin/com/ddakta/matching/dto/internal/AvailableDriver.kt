package com.ddakta.matching.dto.internal

import com.ddakta.matching.domain.vo.Location
import java.math.BigDecimal
import java.util.*

data class AvailableDriver(
    val driverId: UUID,
    val currentLocation: Location,
    val rating: Double,
    val acceptanceRate: Double,
    val isAvailable: Boolean = true,
    val vehicleType: String? = null,
    val completedTrips: Int = 0,
    
    // 매칭 관련 정보
    val estimatedArrivalMinutes: Int? = null,
    val estimatedFare: BigDecimal? = null,
    val distanceToPickupMeters: Double? = null,
    val completionRate: Double = 0.95 // 완료율 (기본 95%)
)