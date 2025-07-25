package com.ddakta.auth.support

import com.ddakta.auth.config.JwtProperties
import com.ddakta.auth.domain.entity.User
import com.ddakta.auth.domain.enum.AuthProvider
import com.ddakta.domain.user.UserRole
import java.util.*

abstract class BaseUnitTest {
    
    /**
     * 테스트용 JWT 설정 생성
     */
    protected fun createTestJwtProperties(): JwtProperties {
        return JwtProperties(
            secret = "test-secret-key-that-is-at-least-32-characters-long",
            accessTokenValidity = 900000, // 15 minutes
            refreshTokenValidity = 604800000 // 7 days
        )
    }
    
    /**
     * 테스트용 유저 엔티티 생성 (DB 저장하지 않음)
     */
    protected fun createTestUserEntity(
        email: String = "test@example.com",
        name: String = "Test User",
        providerId: String = "google-123",
        provider: AuthProvider = AuthProvider.GOOGLE,
        role: UserRole = UserRole.PASSENGER
    ): User {
        return User(
            email = email,
            name = name,
            providerId = providerId,
            provider = provider,
            role = role
        )
    }
}