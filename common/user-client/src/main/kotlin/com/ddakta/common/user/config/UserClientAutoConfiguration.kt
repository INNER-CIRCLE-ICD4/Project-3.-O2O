package com.ddakta.common.user.config

import com.ddakta.common.user.annotation.UserInfoArgumentResolver
import com.ddakta.common.user.cache.LocalCacheManager
import com.ddakta.common.user.cache.UserCacheService
import com.ddakta.common.user.client.UserServiceClient
import com.ddakta.common.user.client.UserServiceFallback
import com.ddakta.common.user.dto.UserInfo
import com.ddakta.common.user.event.UserEventListener
import com.fasterxml.jackson.databind.ObjectMapper
import feign.Feign
import feign.jackson.JacksonDecoder
import feign.jackson.JacksonEncoder
import feign.okhttp.OkHttpClient
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.cloud.openfeign.EnableFeignClients
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
@EnableFeignClients(basePackages = ["com.ddakta.common.user.client"])
@EnableKafka
@ConditionalOnProperty(
    prefix = "ddakta.user-client",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true
)
@Import(
    UserCacheConfig::class,
    UserEventConfig::class,
    CircuitBreakerConfig::class
)
class UserClientAutoConfiguration : WebMvcConfigurer {
    
    @Bean
    @ConditionalOnMissingBean
    fun userServiceClient(
        @Value("\${ddakta.auth-service.url:http://auth-service:8080}") authServiceUrl: String,
        objectMapper: ObjectMapper
    ): UserServiceClient {
        return Feign.builder()
            .client(OkHttpClient())
            .encoder(JacksonEncoder(objectMapper))
            .decoder(JacksonDecoder(objectMapper))
            .target(UserServiceClient::class.java, authServiceUrl)
    }
    
    @Bean
    @ConditionalOnMissingBean
    fun userServiceFallback(): UserServiceFallback {
        return UserServiceFallback()
    }
    
    @Bean
    @ConditionalOnMissingBean
    fun localCacheManager(): LocalCacheManager {
        return LocalCacheManager()
    }
    
    @Bean
    @ConditionalOnMissingBean
    fun userCacheService(
        userServiceClient: UserServiceClient,
        redisTemplate: RedisTemplate<String, UserInfo>,
        localCacheManager: LocalCacheManager,
        circuitBreakerRegistry: CircuitBreakerRegistry
    ): UserCacheService {
        return UserCacheService(
            userServiceClient, 
            redisTemplate, 
            localCacheManager,
            circuitBreakerRegistry
        )
    }
    
    @Bean
    @ConditionalOnMissingBean
    fun userInfoArgumentResolver(
        userCacheService: UserCacheService,
        @Value("\${ddakta.jwt.secret}") jwtSecret: String
    ): UserInfoArgumentResolver {
        return UserInfoArgumentResolver(userCacheService, jwtSecret)
    }
    
    @Bean
    @ConditionalOnMissingBean
    fun userEventListener(
        userCacheService: UserCacheService,
        objectMapper: ObjectMapper
    ): UserEventListener {
        return UserEventListener(userCacheService, objectMapper)
    }
    
    override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
        val userInfoResolver = applicationContext?.getBean(UserInfoArgumentResolver::class.java)
        userInfoResolver?.let { resolvers.add(it) }
    }
    
    companion object {
        private lateinit var applicationContext: org.springframework.context.ApplicationContext
    }
    
    @Bean
    fun applicationContextProvider(): org.springframework.context.ApplicationContextAware {
        return org.springframework.context.ApplicationContextAware { context ->
            applicationContext = context
        }
    }
}