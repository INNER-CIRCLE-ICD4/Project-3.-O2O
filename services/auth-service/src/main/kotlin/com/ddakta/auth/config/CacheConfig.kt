package com.ddakta.auth.config

import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.cache.RedisCacheConfiguration
import org.springframework.data.redis.cache.RedisCacheManager
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.RedisSerializationContext
import org.springframework.data.redis.serializer.StringRedisSerializer
import java.time.Duration

@Configuration
@EnableCaching
class CacheConfig {
    
    @Bean
    fun cacheManager(redisConnectionFactory: RedisConnectionFactory): CacheManager {
        val redisCacheConfiguration = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(10)) // 10분 TTL
            .disableCachingNullValues()
            .serializeKeysWith(
                RedisSerializationContext.SerializationPair.fromSerializer(StringRedisSerializer())
            )
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(GenericJackson2JsonRedisSerializer())
            )
        
        return RedisCacheManager.builder(redisConnectionFactory)
            .cacheDefaults(redisCacheConfiguration)
            .withCacheConfiguration(
                "users",
                RedisCacheConfiguration.defaultCacheConfig()
                    .entryTtl(Duration.ofMinutes(30)) // 사용자 정보는 30분 캐싱
                    .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(StringRedisSerializer())
                    )
                    .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(GenericJackson2JsonRedisSerializer())
                    )
                    .prefixCacheNameWith("auth:cache:")
            )
            .transactionAware() // 트랜잭션과 캐시 연동
            .build()
    }
}