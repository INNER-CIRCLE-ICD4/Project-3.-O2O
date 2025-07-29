package com.ddakta.mockauth.config

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration

/**
 * 개발 환경용 Mock 인증 설정
 * test.auth.enabled=true로 활성화
 */
@Configuration
@ConditionalOnProperty(name = ["test.auth.enabled"], havingValue = "true")
@ComponentScan(basePackages = ["com.ddakta.mockauth"])
class MockAuthConfiguration