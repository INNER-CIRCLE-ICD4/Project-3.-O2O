package com.ddakta.matching.config

import com.ddakta.matching.cache.CustomKeyGenerator
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.CachingConfigurerSupport
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.interceptor.CacheErrorHandler
import org.springframework.cache.interceptor.CacheResolver
import org.springframework.cache.interceptor.KeyGenerator
import org.springframework.cache.interceptor.SimpleCacheErrorHandler
import org.springframework.cache.interceptor.SimpleCacheResolver
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableCaching
class CacheConfig : CachingConfigurerSupport() {

    @Bean
    override fun keyGenerator(): KeyGenerator {
        return CustomKeyGenerator()
    }

    override fun cacheResolver(): CacheResolver? {
        return SimpleCacheResolver(cacheManager()!!)
    }

    override fun errorHandler(): CacheErrorHandler {
        return SimpleCacheErrorHandler()
    }
}