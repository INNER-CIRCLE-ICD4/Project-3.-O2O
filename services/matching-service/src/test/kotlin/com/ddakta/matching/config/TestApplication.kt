package com.ddakta.matching.config

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType
import org.springframework.context.annotation.Profile
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling

/**
 * Test application configuration that excludes FeignClients to prevent conflicts
 */
@SpringBootApplication
@ComponentScan(
    basePackages = ["com.ddakta.matching"],
    excludeFilters = [
        ComponentScan.Filter(
            type = FilterType.REGEX,
            pattern = ["com.ddakta.matching.client.*Client"]
        )
    ]
)
@EntityScan("com.ddakta.matching.domain.entity")
@EnableJpaRepositories("com.ddakta.matching.domain.repository")
@EnableKafka
@EnableAsync
@EnableScheduling
@EnableCaching
@Profile("test", "integration")
class TestApplication