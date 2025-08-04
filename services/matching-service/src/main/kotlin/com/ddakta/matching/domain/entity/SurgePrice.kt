package com.ddakta.matching.domain.entity

import com.ddakta.domain.base.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "surge_prices")
class SurgePrice(
    @Column(nullable = false)
    val h3Index: String,

    @Column(nullable = false)
    val surgeMultiplier: BigDecimal,

    @Column(nullable = false)
    val demandCount: Int = 0,

    @Column(nullable = false)
    val supplyCount: Int = 0
) : BaseEntity() {

    init {
        require(surgeMultiplier >= BigDecimal.ONE) {
            "Surge multiplier must be at least 1.0"
        }
        require(surgeMultiplier <= BigDecimal.valueOf(5)) {
            "Surge multiplier cannot exceed 5.0"
        }
        require(demandCount >= 0) {
            "Demand count cannot be negative"
        }
        require(supplyCount >= 0) {
            "Supply count cannot be negative"
        }
    }

    @Column(nullable = false)
    val effectiveFrom: LocalDateTime = LocalDateTime.now()

    var effectiveTo: LocalDateTime? = null
        private set

    fun expire() {
        this.effectiveTo = LocalDateTime.now()
    }

    fun isActive(): Boolean {
        val now = LocalDateTime.now()
        return now.isAfter(effectiveFrom) && 
               (effectiveTo == null || now.isBefore(effectiveTo))
    }

    companion object {
        fun calculateSurgeMultiplier(demandCount: Int, supplyCount: Int): BigDecimal {
            if (supplyCount == 0) return BigDecimal.valueOf(5) // Max surge
            
            val ratio = demandCount.toDouble() / supplyCount.toDouble()
            
            return when {
                ratio <= 1.0 -> BigDecimal.ONE
                ratio <= 1.5 -> BigDecimal.valueOf(1.2)
                ratio <= 2.0 -> BigDecimal.valueOf(1.5)
                ratio <= 3.0 -> BigDecimal.valueOf(2.0)
                ratio <= 4.0 -> BigDecimal.valueOf(3.0)
                else -> BigDecimal.valueOf(5.0)
            }.setScale(2, BigDecimal.ROUND_HALF_UP)
        }
    }
}