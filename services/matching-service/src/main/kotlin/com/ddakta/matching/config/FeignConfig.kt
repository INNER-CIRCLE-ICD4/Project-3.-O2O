package com.ddakta.matching.config

import com.ddakta.matching.exception.ResourceNotFoundException
import com.ddakta.matching.exception.ServiceUnavailableException
import feign.Logger
import feign.Request
import feign.Retryer
import feign.codec.ErrorDecoder
import org.springframework.cloud.openfeign.EnableFeignClients
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.concurrent.TimeUnit

@Configuration
@EnableFeignClients(basePackages = ["com.ddakta.matching.client"])
class FeignConfig {
    
    @Bean
    fun feignLoggerLevel(): Logger.Level {
        return Logger.Level.BASIC
    }

    @Bean
    fun requestOptions(): Request.Options {
        return Request.Options(
            5000, TimeUnit.MILLISECONDS,
            10000, TimeUnit.MILLISECONDS,
            true
        )
    }

    @Bean
    fun retryer(): Retryer {
        return Retryer.Default(100, 1000, 3)
    }

    @Bean
    fun errorDecoder(): ErrorDecoder {
        return ErrorDecoder { methodKey, response ->
            when (response.status()) {
                503 -> ServiceUnavailableException("Service $methodKey is unavailable")
                404 -> ResourceNotFoundException("Resource not found in $methodKey")
                else -> Exception("Error calling $methodKey: ${response.reason()}")
            }
        }
    }
}