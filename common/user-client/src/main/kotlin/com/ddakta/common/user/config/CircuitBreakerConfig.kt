package com.ddakta.common.user.config

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

@Configuration
class CircuitBreakerConfig {
    
    @Bean
    fun circuitBreakerRegistry(): CircuitBreakerRegistry {
        val circuitBreakerConfig = CircuitBreakerConfig.custom()
            .failureRateThreshold(50f) // 실패율 50% 이상시 Circuit Open
            .waitDurationInOpenState(Duration.ofSeconds(30)) // Open 상태 유지 시간
            .slidingWindowSize(10) // 슬라이딩 윈도우 크기
            .minimumNumberOfCalls(5) // 최소 호출 횟수
            .permittedNumberOfCallsInHalfOpenState(3) // Half-Open 상태에서 허용 호출
            .automaticTransitionFromOpenToHalfOpenEnabled(true) // 자동 전환 활성화
            .build()
        
        return CircuitBreakerRegistry.of(circuitBreakerConfig)
    }
    
    @Bean
    fun userServiceCircuitBreaker(registry: CircuitBreakerRegistry): CircuitBreaker {
        return registry.circuitBreaker("user-service")
    }
}