package com.ddakta.common.user.cache

import com.ddakta.common.user.client.UserServiceClient
import com.ddakta.common.user.dto.UserInfo
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import java.util.UUID
import java.util.concurrent.TimeUnit

@Service
class UserCacheService(
    private val userServiceClient: UserServiceClient,
    private val redisTemplate: RedisTemplate<String, UserInfo>,
    private val localCacheManager: LocalCacheManager,
    circuitBreakerRegistry: CircuitBreakerRegistry
) {
    companion object {
        private const val CACHE_KEY_PREFIX = "user:"
        private const val REDIS_TTL_MINUTES = 5L
        private const val LOCAL_TTL_MINUTES = 1L
    }
    
    private val logger = LoggerFactory.getLogger(javaClass)
    private val circuitBreaker: CircuitBreaker = circuitBreakerRegistry.circuitBreaker("user-service")
    
    fun getUserInfo(userId: UUID): UserInfo? {
        // 1. 로컬 캐시 확인
        localCacheManager.get(userId)?.let { 
            logger.debug("User $userId found in local cache")
            return it 
        }
        
        // 2. Redis 캐시 확인
        val redisKey = "$CACHE_KEY_PREFIX$userId"
        try {
            redisTemplate.opsForValue().get(redisKey)?.let { userInfo ->
                logger.debug("User $userId found in Redis cache")
                // 로컬 캐시에 저장
                localCacheManager.put(userId, userInfo)
                return userInfo
            }
        } catch (e: Exception) {
            logger.error("Error accessing Redis for user $userId", e)
        }
        
        // 3. Auth Service 호출 (Circuit Breaker 적용)
        return circuitBreaker.executeSupplier {
            logger.debug("Fetching user $userId from auth service")
            userServiceClient.getUserInfo(userId)?.also { userInfo ->
                // 캐시에 저장
                try {
                    redisTemplate.opsForValue().set(
                        redisKey, 
                        userInfo, 
                        REDIS_TTL_MINUTES, 
                        TimeUnit.MINUTES
                    )
                } catch (e: Exception) {
                    logger.error("Error saving to Redis cache", e)
                }
                localCacheManager.put(userId, userInfo)
            }
        }
    }
    
    fun getUsersBatch(userIds: List<UUID>): Map<UUID, UserInfo> {
        val result = mutableMapOf<UUID, UserInfo>()
        val notInCache = mutableListOf<UUID>()
        
        // 1. 로컬 캐시에서 조회
        userIds.forEach { userId ->
            localCacheManager.get(userId)?.let {
                result[userId] = it
            } ?: notInCache.add(userId)
        }
        
        if (notInCache.isEmpty()) return result
        
        // 2. Redis에서 배치 조회
        val redisKeys = notInCache.map { "$CACHE_KEY_PREFIX$it" }
        try {
            val redisResults = redisTemplate.opsForValue().multiGet(redisKeys)
            val stillNotFound = mutableListOf<UUID>()
            
            notInCache.forEachIndexed { index, userId ->
                redisResults?.get(index)?.let {
                    result[userId] = it
                    localCacheManager.put(userId, it)
                } ?: stillNotFound.add(userId)
            }
            
            if (stillNotFound.isEmpty()) return result
            notInCache.clear()
            notInCache.addAll(stillNotFound)
        } catch (e: Exception) {
            logger.error("Error during Redis batch get", e)
        }
        
        // 3. Auth Service에서 배치 조회
        if (notInCache.isNotEmpty()) {
            circuitBreaker.executeSupplier {
                userServiceClient.getUsersBatch(notInCache)
            }?.forEach { (userId, userInfo) ->
                result[userId] = userInfo
                updateUserInfo(userInfo)
            }
        }
        
        return result
    }
    
    fun invalidateUser(userId: UUID) {
        logger.debug("Invalidating cache for user $userId")
        localCacheManager.evict(userId)
        try {
            redisTemplate.delete("$CACHE_KEY_PREFIX$userId")
        } catch (e: Exception) {
            logger.error("Error invalidating Redis cache for user $userId", e)
        }
    }
    
    fun updateUserInfo(userInfo: UserInfo) {
        logger.debug("Updating cache for user ${userInfo.id}")
        val redisKey = "$CACHE_KEY_PREFIX${userInfo.id}"
        try {
            redisTemplate.opsForValue().set(
                redisKey, 
                userInfo, 
                REDIS_TTL_MINUTES, 
                TimeUnit.MINUTES
            )
        } catch (e: Exception) {
            logger.error("Error updating Redis cache", e)
        }
        localCacheManager.put(userInfo.id, userInfo)
    }
    
    fun getFromLocalCache(userId: UUID): UserInfo? = localCacheManager.get(userId)
    
    fun putToLocalCache(userId: UUID, userInfo: UserInfo) = localCacheManager.put(userId, userInfo)
    
    fun getCacheStats() = CacheStats(
        localCacheHitRate = localCacheManager.getStats().hitRate(),
        localCacheSize = localCacheManager.getSize(),
        localCacheStats = localCacheManager.getStats()
    )
    
    data class CacheStats(
        val localCacheHitRate: Double,
        val localCacheSize: Long,
        val localCacheStats: com.github.benmanes.caffeine.cache.stats.CacheStats
    )
}