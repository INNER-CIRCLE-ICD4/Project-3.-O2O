package com.ddakta.matching.config

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig as ResilienceCircuitBreakerConfig
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.timelimiter.TimeLimiterConfig
import mu.KotlinLogging
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder
import org.springframework.cloud.client.circuitbreaker.Customizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

@Configuration
class CircuitBreakerConfig {
    
    private val logger = KotlinLogging.logger {}
    
    @Bean
    fun defaultCustomizer(): Customizer<Resilience4JCircuitBreakerFactory> {
        return Customizer { factory ->
            factory.configureDefault { id ->
                Resilience4JConfigBuilder(id)
                    .timeLimiterConfig(
                        TimeLimiterConfig.custom()
                            .timeoutDuration(Duration.ofSeconds(3))
                            .build()
                    )
                    .circuitBreakerConfig(
                        ResilienceCircuitBreakerConfig.custom()
                            .slidingWindowSize(10)
                            .minimumNumberOfCalls(5)
                            .failureRateThreshold(50.0f)
                            .waitDurationInOpenState(Duration.ofSeconds(30))
                            .slowCallRateThreshold(50.0f)
                            .slowCallDurationThreshold(Duration.ofSeconds(2))
                            .permittedNumberOfCallsInHalfOpenState(3)
                            .automaticTransitionFromOpenToHalfOpenEnabled(true)
                            .build()
                    )
                    .build()
            }
        }
    }
    
    @Bean
    fun locationServiceCustomizer(): Customizer<Resilience4JCircuitBreakerFactory> {
        return Customizer { factory ->
            factory.configure(
                { builder ->
                    builder
                        .timeLimiterConfig(
                            TimeLimiterConfig.custom()
                                .timeoutDuration(Duration.ofSeconds(5))
                                .build()
                        )
                        .circuitBreakerConfig(
                            ResilienceCircuitBreakerConfig.custom()
                                .slidingWindowSize(20)
                                .minimumNumberOfCalls(10)
                                .failureRateThreshold(60.0f)
                                .waitDurationInOpenState(Duration.ofSeconds(60))
                                .slowCallRateThreshold(70.0f)
                                .slowCallDurationThreshold(Duration.ofSeconds(3))
                                .permittedNumberOfCallsInHalfOpenState(5)
                                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                                .recordExceptions(
                                    feign.FeignException::class.java,
                                    feign.RetryableException::class.java
                                )
                                .build()
                        )
                },
                "location-service"
            )
        }
    }
    
    @Bean
    fun userServiceCustomizer(): Customizer<Resilience4JCircuitBreakerFactory> {
        return Customizer { factory ->
            factory.configure(
                { builder ->
                    builder
                        .timeLimiterConfig(
                            TimeLimiterConfig.custom()
                                .timeoutDuration(Duration.ofSeconds(3))
                                .build()
                        )
                        .circuitBreakerConfig(
                            ResilienceCircuitBreakerConfig.custom()
                                .slidingWindowSize(15)
                                .minimumNumberOfCalls(8)
                                .failureRateThreshold(50.0f)
                                .waitDurationInOpenState(Duration.ofSeconds(45))
                                .slowCallRateThreshold(60.0f)
                                .slowCallDurationThreshold(Duration.ofSeconds(2))
                                .permittedNumberOfCallsInHalfOpenState(4)
                                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                                .recordExceptions(
                                    feign.FeignException::class.java,
                                    feign.RetryableException::class.java
                                )
                                .ignoreExceptions(
                                    IllegalArgumentException::class.java
                                )
                                .build()
                        )
                },
                "user-service"
            )
        }
    }
    
    @Bean
    fun circuitBreakerRegistry(): CircuitBreakerRegistry {
        val config = ResilienceCircuitBreakerConfig.custom()
            .slidingWindowSize(10)
            .minimumNumberOfCalls(5)
            .failureRateThreshold(50.0f)
            .waitDurationInOpenState(Duration.ofSeconds(30))
            .build()
        
        val registry = CircuitBreakerRegistry.of(config)
        
        // Register event listeners for monitoring
        registry.circuitBreaker("location-service").apply {
            eventPublisher.onStateTransition { event ->
                logger.warn { "Location service circuit breaker state transition: ${event.stateTransition}" }
            }
            eventPublisher.onFailureRateExceeded { event ->
                logger.error { "Location service circuit breaker failure rate exceeded: ${event.failureRate}%" }
            }
        }
        
        registry.circuitBreaker("user-service").apply {
            eventPublisher.onStateTransition { event ->
                logger.warn { "User service circuit breaker state transition: ${event.stateTransition}" }
            }
            eventPublisher.onFailureRateExceeded { event ->
                logger.error { "User service circuit breaker failure rate exceeded: ${event.failureRate}%" }
            }
        }
        
        return registry
    }
}