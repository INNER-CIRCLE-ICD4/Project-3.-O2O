package com.ddakta.matching.domain.repository

import com.ddakta.matching.domain.entity.SurgePrice
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.*

@Repository
interface SurgePriceRepository : JpaRepository<SurgePrice, UUID> {

    @Query("""
        SELECT sp FROM SurgePrice sp
        WHERE sp.h3Index = :h3Index
        AND sp.effectiveFrom <= :now
        AND (sp.effectiveTo IS NULL OR sp.effectiveTo > :now)
        ORDER BY sp.effectiveFrom DESC
    """)
    fun findCurrentSurgePrice(
        @Param("h3Index") h3Index: String,
        @Param("now") now: LocalDateTime = LocalDateTime.now()
    ): SurgePrice?

    @Query("""
        SELECT sp FROM SurgePrice sp
        WHERE sp.h3Index IN :h3Indexes
        AND sp.effectiveFrom <= :now
        AND (sp.effectiveTo IS NULL OR sp.effectiveTo > :now)
    """)
    fun findCurrentSurgePricesForAreas(
        @Param("h3Indexes") h3Indexes: List<String>,
        @Param("now") now: LocalDateTime = LocalDateTime.now()
    ): List<SurgePrice>

    @Modifying
    @Query("""
        UPDATE SurgePrice sp
        SET sp.effectiveTo = :now
        WHERE sp.h3Index = :h3Index
        AND sp.effectiveTo IS NULL
    """)
    fun expireCurrentSurgePrice(
        @Param("h3Index") h3Index: String,
        @Param("now") now: LocalDateTime = LocalDateTime.now()
    ): Int

    @Query("""
        SELECT sp FROM SurgePrice sp
        WHERE sp.effectiveFrom BETWEEN :startTime AND :endTime
        ORDER BY sp.surgeMultiplier DESC
    """)
    fun findSurgePricesInTimeRange(
        @Param("startTime") startTime: LocalDateTime,
        @Param("endTime") endTime: LocalDateTime
    ): List<SurgePrice>

    @Query("""
        SELECT sp.h3Index, MAX(sp.surgeMultiplier)
        FROM SurgePrice sp
        WHERE sp.effectiveFrom > :since
        GROUP BY sp.h3Index
        ORDER BY MAX(sp.surgeMultiplier) DESC
    """)
    fun findHighestSurgeAreasSince(
        @Param("since") since: LocalDateTime,
        limit: Int = 10
    ): List<Array<Any>>

    @Modifying
    @Query("""
        DELETE FROM SurgePrice sp
        WHERE sp.effectiveTo < :before
    """)
    fun deleteExpiredSurgePrices(
        @Param("before") before: LocalDateTime = LocalDateTime.now().minusDays(30)
    ): Int

    @Query("""
        SELECT AVG(sp.surgeMultiplier)
        FROM SurgePrice sp
        WHERE sp.h3Index = :h3Index
        AND sp.effectiveFrom > :since
    """)
    fun calculateAverageSurgeMultiplier(
        @Param("h3Index") h3Index: String,
        @Param("since") since: LocalDateTime
    ): Double?
}
