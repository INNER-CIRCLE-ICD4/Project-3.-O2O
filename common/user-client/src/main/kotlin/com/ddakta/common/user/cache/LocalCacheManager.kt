package com.ddakta.common.user.cache

import com.ddakta.common.user.dto.UserInfo
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.stats.CacheStats
import org.springframework.stereotype.Component
import java.util.UUID
import java.util.concurrent.TimeUnit

@Component
class LocalCacheManager {
    private val cache: Cache<UUID, UserInfo> = Caffeine.newBuilder()
        .maximumSize(10_000)
        .expireAfterWrite(1, TimeUnit.MINUTES)
        .recordStats()
        .build()
    
    fun get(userId: UUID): UserInfo? = cache.getIfPresent(userId)
    
    fun put(userId: UUID, userInfo: UserInfo) = cache.put(userId, userInfo)
    
    fun evict(userId: UUID) = cache.invalidate(userId)
    
    fun evictAll() = cache.invalidateAll()
    
    fun getStats(): CacheStats = cache.stats()
    
    fun getSize(): Long = cache.estimatedSize()
}