package com.ddakta.matching.config

import com.ddakta.matching.cache.CustomKeyGenerator
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.CachingConfigurer
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.interceptor.CacheErrorHandler
import org.springframework.cache.interceptor.CacheResolver
import org.springframework.cache.interceptor.KeyGenerator
import org.springframework.cache.interceptor.SimpleCacheErrorHandler
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableCaching
class CacheConfig : CachingConfigurer {

    @Bean
    override fun keyGenerator(): KeyGenerator {
        return CustomKeyGenerator()
    }

    override fun cacheResolver(): CacheResolver? {
        return null // Use default cache resolver
    }

    override fun errorHandler(): CacheErrorHandler {
        return SimpleCacheErrorHandler()
    }
}