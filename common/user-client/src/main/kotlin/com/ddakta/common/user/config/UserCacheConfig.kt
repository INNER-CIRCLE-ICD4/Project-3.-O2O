package com.ddakta.common.user.config

import com.ddakta.common.user.dto.UserInfo
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.StringRedisSerializer

@Configuration
class UserCacheConfig {
    
    @Bean
    fun userInfoRedisTemplate(
        redisConnectionFactory: RedisConnectionFactory
    ): RedisTemplate<String, UserInfo> {
        val template = RedisTemplate<String, UserInfo>()
        template.connectionFactory = redisConnectionFactory
        
        // Jackson ObjectMapper 설정
        val objectMapper = ObjectMapper().apply {
            registerModule(JavaTimeModule())
            registerModule(KotlinModule.Builder().build())
            
            // 다형성 타입 활성화
            activateDefaultTyping(
                BasicPolymorphicTypeValidator.builder()
                    .allowIfSubType(UserInfo::class.java)
                    .build(),
                ObjectMapper.DefaultTyping.NON_FINAL
            )
        }
        
        val serializer = GenericJackson2JsonRedisSerializer(objectMapper)
        
        template.keySerializer = StringRedisSerializer()
        template.valueSerializer = serializer
        template.hashKeySerializer = StringRedisSerializer()
        template.hashValueSerializer = serializer
        
        template.afterPropertiesSet()
        return template
    }
}