package com.ddakta.auth.controller

import com.ddakta.auth.domain.enum.AuthProvider
import com.ddakta.auth.service.AuthService
import com.ddakta.auth.support.BaseIntegrationTest
import com.ddakta.domain.user.UserRole
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

class OAuth2IntegrationTest : BaseIntegrationTest() {
    
    @Autowired
    private lateinit var authService: AuthService
    
    @Test
    fun `OAuth 로그인 엔드포인트가 올바른 URL을 반환해야 한다`() {
        performPost("/api/v1/auth/login")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.google").value("/oauth2/authorization/google"))
            .andExpect(jsonPath("$.apple").value("/oauth2/authorization/apple"))
    }
    
    @Test
    @WithMockUser
    fun `OAuth 성공 후 새로운 사용자가 자동으로 생성되어야 한다`() {
        // OAuth 성공 시뮬레이션 - 실제로는 OAuth2SuccessHandler가 호출됨
        // 여기서는 서비스 레이어를 직접 테스트
        val email = "newuser@gmail.com"
        val name = "New OAuth User"
        val providerId = "google-12345"
        val provider = AuthProvider.GOOGLE
        
        // 사용자가 없음을 확인
        val existingUser = userRepository.findByProviderAndProviderId(provider, providerId)
        assertTrue(existingUser.isEmpty)
        
        // OAuth 로그인 시뮬레이션 (실제로는 OAuth2SuccessHandler가 호출)
        val user = authService.findOrCreateUser(
            email = email,
            name = name,
            providerId = providerId,
            provider = provider,
            role = UserRole.PASSENGER,
            profileImageUrl = "https://example.com/profile.jpg"
        )
        
        // 사용자가 생성되었는지 확인
        assertNotNull(user.id)
        assertEquals(email, user.email)
        assertEquals(name, user.name)
        assertEquals(providerId, user.providerId)
        assertEquals(provider, user.provider)
        assertEquals(UserRole.PASSENGER, user.role)
        
        // DB에 저장되었는지 확인
        val savedUser = userRepository.findById(user.id).orElse(null)
        assertNotNull(savedUser)
        assertEquals(email, savedUser.email)
    }
    
    @Test
    @WithMockUser
    fun `OAuth 성공 후 기존 사용자는 새로 생성되지 않아야 한다`() {
        // 기존 사용자 생성
        val existingUser = createTestUser(
            email = "existing@gmail.com",
            name = "Existing User",
            providerId = "google-99999",
            provider = AuthProvider.GOOGLE
        )
        
        val userCountBefore = userRepository.count()
        
        // 동일한 provider와 providerId로 로그인 시도
        val user = authService.findOrCreateUser(
            email = existingUser.email,
            name = existingUser.name,
            providerId = existingUser.providerId,
            provider = existingUser.provider,
            role = existingUser.role,
            profileImageUrl = null
        )
        
        // 동일한 사용자인지 확인
        assertEquals(existingUser.id, user.id)
        
        // 새 사용자가 생성되지 않았는지 확인
        val userCountAfter = userRepository.count()
        assertEquals(userCountBefore, userCountAfter)
    }
    
    @Test
    @WithMockUser
    fun `OAuth 로그인 시 토큰 세션이 생성되어야 한다`() {
        val user = createTestUser(
            providerId = "google-token-test",
            provider = AuthProvider.GOOGLE
        )
        
        // 토큰 세션 생성
        val (accessToken, refreshToken) = authService.createTokenSession(user)
        
        // 토큰이 생성되었는지 확인
        assertNotNull(accessToken)
        assertNotNull(refreshToken)
        assertTrue(jwtTokenProvider.validateToken(accessToken))
        
        // 토큰 세션이 저장되었는지 확인
        val sessions = tokenSessionRepository.findByUserId(user.id)
        assertEquals(1, sessions.size)
        assertEquals(accessToken, sessions.first().accessToken)
        assertEquals(refreshToken, sessions.first().refreshToken)
    }
    
    @Test
    fun `Driver 역할로 OAuth 회원가입이 가능해야 한다`() {
        val email = "driver@gmail.com"
        val name = "Driver User"
        val providerId = "google-driver-123"
        val provider = AuthProvider.GOOGLE
        
        // Driver 역할로 회원가입
        val user = authService.findOrCreateUser(
            email = email,
            name = name,
            providerId = providerId,
            provider = provider,
            role = UserRole.DRIVER,
            profileImageUrl = null
        )
        
        // Driver 역할이 올바르게 설정되었는지 확인
        assertEquals(UserRole.DRIVER, user.role)
        assertFalse(user.isVerifiedDriver())
    }
    
    @Test
    fun `Apple OAuth로도 회원가입이 가능해야 한다`() {
        val email = "apple@icloud.com"
        val name = "Apple User"
        val providerId = "apple-123456"
        val provider = AuthProvider.APPLE
        
        // Apple OAuth로 회원가입
        val user = authService.findOrCreateUser(
            email = email,
            name = name,
            providerId = providerId,
            provider = provider,
            role = UserRole.PASSENGER,
            profileImageUrl = null
        )
        
        // Apple provider가 올바르게 설정되었는지 확인
        assertEquals(AuthProvider.APPLE, user.provider)
        assertEquals(providerId, user.providerId)
    }
}