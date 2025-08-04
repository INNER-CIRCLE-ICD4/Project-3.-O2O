package com.ddakta.matching.config

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
import java.lang.reflect.Method

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

class CustomKeyGenerator : KeyGenerator {
    override fun generate(target: Any, method: Method, vararg params: Any?): Any {
        return buildString {
            append(target.javaClass.simpleName)
            append(".")
            append(method.name)
            append(":")
            params.forEach { param ->
                append(param?.hashCode() ?: "null")
                append("-")
            }
        }
    }
}