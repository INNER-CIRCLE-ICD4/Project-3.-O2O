package com.ddakta.domain

import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

@SpringBootConfiguration
@EnableAutoConfiguration
@EntityScan("com.ddakta.domain.user")
@EnableJpaRepositories("com.ddakta.domain.user")
class DomainTestConfig
