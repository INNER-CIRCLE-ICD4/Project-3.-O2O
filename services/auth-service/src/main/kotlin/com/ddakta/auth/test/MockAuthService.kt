package com.ddakta.auth.test

import com.ddakta.auth.domain.enum.AuthProvider
import com.ddakta.auth.service.AuthService
import com.ddakta.domain.user.UserRole
import com.ddakta.mockauth.service.MockAuthService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service

/**
 * 개발/테스트용 Mock 인증 서비스
 * 테스트 사용자를 동적으로 생성하고 JWT 토큰을 발급합니다.
 */
@Service
@ConditionalOnProperty(name = ["test.auth.enabled"], havingValue = "true")
class MockAuthService(
    private val authService: AuthService
) : MockAuthService {
    
    override fun createOrGetTestUser(userId: String, role: UserRole): MockAuthService.TestAuthResult {
        // userId와 역할 기반으로 테스트 사용자 데이터 생성
        val email = "test.${role.name.lowercase()}.$userId@example.com"
        val name = "Test ${role.name.lowercase().replaceFirstChar { it.uppercase() }} $userId"
        val providerId = "test-$role-$userId"
        
        // 기존 인증 서비스를 사용하여 사용자 생성 또는 조회
        val user = authService.findOrCreateUser(
            email = email,
            name = name,
            providerId = providerId,
            provider = AuthProvider.GOOGLE, // Google OAuth로 Mock
            role = role,
            profileImageUrl = "https://ui-avatars.com/api/?name=${name.replace(" ", "+")}"
        )
        
        // JWT 토큰 생성
        val (accessToken, refreshToken) = authService.createTokenSession(user)
        
        return MockAuthService.TestAuthResult(
            accessToken = accessToken,
            refreshToken = refreshToken,
            user = MockAuthService.TestUserInfo(
                id = user.id.toString(),
                email = user.email,
                name = user.name,
                role = user.role
            )
        )
    }
}