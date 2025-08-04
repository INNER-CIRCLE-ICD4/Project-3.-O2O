package com.ddakta.matching.service

import com.ddakta.matching.domain.entity.SurgePrice
import java.math.BigDecimal

interface SurgePriceService {
    fun getCurrentSurgeMultiplier(h3Index: String): Double
    
    fun updateSurgeMultiplier(h3Index: String, multiplier: Double): SurgePrice
    
    fun calculateSurgeMultiplier(
        h3Index: String,
        demandCount: Int,
        supplyCount: Int
    ): Double
    
    fun batchUpdateSurgeMultipliers(h3Indexes: List<String>)
    
    fun getSurgePricesInArea(h3Indexes: List<String>): Map<String, Double>
}