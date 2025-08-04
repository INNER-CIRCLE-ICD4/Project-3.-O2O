package com.ddakta.matching.service.impl

import com.ddakta.matching.config.MatchingProperties
import com.ddakta.matching.domain.entity.SurgePrice
import com.ddakta.matching.domain.repository.MatchingRequestRepository
import com.ddakta.matching.domain.repository.SurgePriceRepository
import com.ddakta.matching.client.LocationServiceClient
import com.ddakta.matching.service.SurgePriceService
import mu.KotlinLogging
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

@Service
@Transactional
class SurgePriceServiceImpl(
    private val surgePriceRepository: SurgePriceRepository,
    private val matchingRequestRepository: MatchingRequestRepository,
    private val locationServiceClient: LocationServiceClient,
    private val matchingProperties: MatchingProperties,
    private val redisTemplate: RedisTemplate<String, String>
) : SurgePriceService {

    private val logger = KotlinLogging.logger {}

    companion object {
        const val SURGE_CACHE_KEY = "surge:"
        const val SURGE_CACHE_TTL_MINUTES = 5L
        const val MIN_SURGE_MULTIPLIER = 1.0
        const val MAX_SURGE_MULTIPLIER = 5.0
        const val BASE_DEMAND_THRESHOLD = 5
        const val DEMAND_SUPPLY_RATIO_THRESHOLD = 1.5
        const val SURGE_INCREMENT = 0.1
        const val RAPID_DEMAND_MULTIPLIER = 1.2
    }

    @Cacheable(value = ["surgePrice"], key = "#h3Index")
    override fun getCurrentSurgeMultiplier(h3Index: String): Double {
        // Redis 캐시 확인
        val cacheKey = "$SURGE_CACHE_KEY$h3Index"
        val cachedValue = redisTemplate.opsForValue().get(cacheKey)

        if (cachedValue != null) {
            return cachedValue.toDouble()
        }

        // DB에서 조회
        // TODO: 임시 수정 - findActiveByH3Index 메서드 없음
        val surgePrice: SurgePrice? = null
        val multiplier = surgePrice?.surgeMultiplier?.toDouble() ?: MIN_SURGE_MULTIPLIER

        // 캐시 저장
        redisTemplate.opsForValue().set(
            cacheKey,
            multiplier.toString(),
            SURGE_CACHE_TTL_MINUTES,
            TimeUnit.MINUTES
        )

        return multiplier
    }

    @CacheEvict(value = ["surgePrice"], key = "#h3Index")
    override fun updateSurgeMultiplier(h3Index: String, multiplier: Double): SurgePrice {
        val validMultiplier = max(MIN_SURGE_MULTIPLIER, min(MAX_SURGE_MULTIPLIER, multiplier))

        // 기존 서지 가격 비활성화
        // TODO: 임시 수정 - deactivatePreviousPrices 메서드 없음

        // 새로운 서지 가격 생성
        val surgePrice = SurgePrice(
            h3Index = h3Index,
            surgeMultiplier = BigDecimal.valueOf(validMultiplier),
            demandCount = 0,
            supplyCount = 0
        )

        val saved = surgePriceRepository.save(surgePrice)

        // Redis 캐시 업데이트
        val cacheKey = "$SURGE_CACHE_KEY$h3Index"
        redisTemplate.opsForValue().set(
            cacheKey,
            validMultiplier.toString(),
            SURGE_CACHE_TTL_MINUTES,
            TimeUnit.MINUTES
        )

        logger.info { "Updated surge multiplier for $h3Index to $validMultiplier" }

        return saved
    }

    override fun calculateSurgeMultiplier(
        h3Index: String,
        demandCount: Int,
        supplyCount: Int
    ): Double {
        // 공급이 0이면 최대 서지 적용
        if (supplyCount == 0) {
            return if (demandCount > 0) MAX_SURGE_MULTIPLIER else MIN_SURGE_MULTIPLIER
        }

        // 수요/공급 비율 계산
        val demandSupplyRatio = demandCount.toDouble() / supplyCount.toDouble()

        // 최근 수요 증가율 확인
        val recentDemandGrowth = getRecentDemandGrowthRate(h3Index)

        // 기본 서지 계산
        var surgeMultiplier = when {
            demandSupplyRatio < 1.0 -> MIN_SURGE_MULTIPLIER
            demandSupplyRatio < DEMAND_SUPPLY_RATIO_THRESHOLD -> {
                MIN_SURGE_MULTIPLIER + (demandSupplyRatio - 1.0) * SURGE_INCREMENT * 2
            }
            demandSupplyRatio < 2.0 -> {
                1.2 + (demandSupplyRatio - DEMAND_SUPPLY_RATIO_THRESHOLD) * SURGE_INCREMENT * 3
            }
            demandSupplyRatio < 3.0 -> {
                1.5 + (demandSupplyRatio - 2.0) * SURGE_INCREMENT * 4
            }
            else -> {
                min(2.0 + (demandSupplyRatio - 3.0) * SURGE_INCREMENT * 5, MAX_SURGE_MULTIPLIER)
            }
        }

        // 급격한 수요 증가 시 추가 서지
        if (recentDemandGrowth > 2.0) {
            surgeMultiplier *= RAPID_DEMAND_MULTIPLIER
        }

        // 최소/최대값 제한
        surgeMultiplier = max(MIN_SURGE_MULTIPLIER, min(MAX_SURGE_MULTIPLIER, surgeMultiplier))

        // 소수점 한 자리로 반올림
        return (surgeMultiplier * 10).toInt() / 10.0
    }

    override fun batchUpdateSurgeMultipliers(h3Indexes: List<String>) {
        h3Indexes.forEach { h3Index ->
            try {
                // 해당 지역의 수요 조회 (최근 5분)
                // TODO: 임시 수정 - countRecentRequestsByH3Index 메서드 없음
                val demandCount = 0L

                // 해당 지역의 공급 조회 (가용 드라이버 수)
                val supplyCount = try {
                    locationServiceClient.getAvailableDriverCount(h3Index)
                } catch (e: Exception) {
                    logger.warn { "Failed to get driver count for $h3Index: ${e.message}" }
                    1 // 기본값
                }

                // 서지 배수 계산
                val newMultiplier = calculateSurgeMultiplier(h3Index, demandCount.toInt(), supplyCount)

                // 현재 서지와 비교하여 변경이 필요한 경우만 업데이트
                val currentMultiplier = getCurrentSurgeMultiplier(h3Index)
                if (kotlin.math.abs(currentMultiplier - newMultiplier) >= 0.1) {
                    updateSurgeMultiplier(h3Index, newMultiplier)

                    logger.info {
                        "Surge updated for $h3Index: $currentMultiplier -> $newMultiplier " +
                        "(demand: $demandCount, supply: $supplyCount)"
                    }
                }

            } catch (e: Exception) {
                logger.error(e) { "Error updating surge for $h3Index" }
            }
        }
    }

    override fun getSurgePricesInArea(h3Indexes: List<String>): Map<String, Double> {
        return h3Indexes.associateWith { h3Index ->
            getCurrentSurgeMultiplier(h3Index)
        }
    }

    private fun getRecentDemandGrowthRate(h3Index: String): Double {
        // 최근 15분과 이전 15분의 수요 비교
        val now = LocalDateTime.now()
        // TODO: 임시 수정 - 리포지토리 메서드 없음
        val recent = 0L
        val previous = 0L

        return if (previous > 0) {
            recent.toDouble() / previous.toDouble()
        } else {
            1.0
        }
    }
}
