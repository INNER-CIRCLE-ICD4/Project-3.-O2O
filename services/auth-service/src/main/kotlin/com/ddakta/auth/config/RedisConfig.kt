package com.ddakta.auth.config

import org.springframework.boot.autoconfigure.data.redis.RedisProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories
import org.springframework.data.redis.serializer.StringRedisSerializer

@Configuration
@EnableRedisRepositories
@EnableConfigurationProperties(RedisProperties::class)
class RedisConfig {
    
    @Bean
    fun redisConnectionFactory(redisProperties: RedisProperties): RedisConnectionFactory {
        return LettuceConnectionFactory(redisProperties.host, redisProperties.port)
    }
    
    @Bean
    fun redisTemplate(connectionFactory: RedisConnectionFactory): RedisTemplate<String, Any> {
        return RedisTemplate<String, Any>().apply {
            this.connectionFactory = connectionFactory
            keySerializer = StringRedisSerializer()
            valueSerializer = StringRedisSerializer()
            hashKeySerializer = StringRedisSerializer()
            hashValueSerializer = StringRedisSerializer()
        }
    }
}