package com.ddakta.mockauth.service

import com.ddakta.domain.user.UserRole

/**
 * 테스트용 동적 사용자 생성을 제공하는 Mock 인증 서비스
 * auth-service 모듈에서 구현해야 합니다.
 */
interface MockAuthService {
    fun createOrGetTestUser(userId: String, role: UserRole): TestAuthResult
    
    data class TestAuthResult(
        val accessToken: String,
        val refreshToken: String,
        val user: TestUserInfo
    )
    
    data class TestUserInfo(
        val id: String,
        val email: String,
        val name: String,
        val role: UserRole
    )
}