package com.ddakta.matching.domain.vo

import jakarta.persistence.Embeddable
import java.io.Serializable
import java.math.BigDecimal

@Embeddable
data class Fare(
    val baseFare: BigDecimal,
    val surgeMultiplier: BigDecimal = BigDecimal.ONE,
    val totalFare: BigDecimal? = null,
    val currency: String = "KRW"
) : Serializable {
    
    init {
        require(baseFare >= BigDecimal.ZERO) { "Base fare cannot be negative" }
        require(surgeMultiplier >= BigDecimal.ONE) { "Surge multiplier must be at least 1.0" }
        require(surgeMultiplier <= BigDecimal.valueOf(5)) { "Surge multiplier cannot exceed 5.0" }
        totalFare?.let {
            require(it >= BigDecimal.ZERO) { "Total fare cannot be negative" }
        }
    }
    
    fun calculateEstimatedFare(): BigDecimal {
        return baseFare.multiply(surgeMultiplier)
    }
}