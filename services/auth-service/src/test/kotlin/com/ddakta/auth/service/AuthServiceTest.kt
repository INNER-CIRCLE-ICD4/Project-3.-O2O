package com.ddakta.auth.service

import com.ddakta.auth.domain.enum.AuthProvider
import com.ddakta.auth.domain.entity.User
import com.ddakta.auth.support.BaseIntegrationTest
import com.ddakta.domain.user.UserRole
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.util.*

class AuthServiceTest : BaseIntegrationTest() {
    
    @Autowired
    private lateinit var authService: AuthService
    
    private lateinit var testUser: User
    
    @BeforeEach
    fun setUp() {
        testUser = createTestUser()
    }
    
    @Test
    fun `기존 사용자를 provider와 providerId로 찾을 수 있어야 한다`() {
        val foundUser = authService.findOrCreateUser(
            email = testUser.email,
            name = testUser.name,
            providerId = testUser.providerId,
            provider = testUser.provider,
            role = testUser.role,
            profileImageUrl = null
        )
        
        assertEquals(testUser.id, foundUser.id)
        assertEquals(testUser.email, foundUser.email)
    }
    
    @Test
    fun `존재하지 않는 사용자는 새로 생성해야 한다`() {
        val newUser = authService.findOrCreateUser(
            email = "new@example.com",
            name = "New User",
            providerId = "google-456",
            provider = AuthProvider.GOOGLE,
            role = UserRole.DRIVER,
            profileImageUrl = "https://example.com/image.jpg"
        )
        
        assertNotNull(newUser.id)
        assertEquals("new@example.com", newUser.email)
        assertEquals("New User", newUser.name)
        assertEquals(UserRole.DRIVER, newUser.role)
        assertEquals("https://example.com/image.jpg", newUser.profileImageUrl)
    }
    
    @Test
    fun `토큰 세션 생성시 기존 세션은 삭제되어야 한다`() {
        // 첫 번째 토큰 세션 생성
        val (firstAccessToken, firstRefreshToken) = authService.createTokenSession(testUser)
        assertNotNull(firstAccessToken)
        assertNotNull(firstRefreshToken)
        
        val sessions = tokenSessionRepository.findByUserId(testUser.id)
        assertEquals(1, sessions.size)
        
        // 두 번째 토큰 세션 생성 - 첫 번째 세션은 삭제되어야 함
        val (secondAccessToken, secondRefreshToken) = authService.createTokenSession(testUser)
        assertNotNull(secondAccessToken)
        assertNotNull(secondRefreshToken)
        assertNotEquals(firstRefreshToken, secondRefreshToken)
        
        val newSessions = tokenSessionRepository.findByUserId(testUser.id)
        assertEquals(1, newSessions.size)
        assertEquals(secondRefreshToken, newSessions.first().refreshToken)
    }
    
    @Test
    fun `리프레시 토큰으로 액세스 토큰을 갱신할 수 있어야 한다`() {
        // 토큰 세션 생성
        val (_, refreshToken) = authService.createTokenSession(testUser)
        
        // 액세스 토큰 갱신
        val response = authService.refreshAccessToken(refreshToken)
        
        assertNotNull(response)
        assertTrue(jwtTokenProvider.validateToken(response!!.accessToken))
        assertNotEquals(refreshToken, response.refreshToken)
        assertEquals(testUser.id, response.user.id)
    }
    
    @Test
    fun `유효하지 않은 리프레시 토큰으로는 갱신할 수 없어야 한다`() {
        val response = authService.refreshAccessToken("invalid-token")
        assertNull(response)
    }
    
    @Test
    fun `로그아웃시 모든 토큰 세션이 삭제되어야 한다`() {
        // 토큰 세션 생성
        authService.createTokenSession(testUser)
        
        // 세션 존재 확인
        val sessionsBefore = tokenSessionRepository.findByUserId(testUser.id)
        assertEquals(1, sessionsBefore.size)
        
        // 로그아웃
        authService.logout(testUser.id)
        
        // 모든 토큰 세션이 삭제되었는지 확인
        val sessionsAfter = tokenSessionRepository.findByUserId(testUser.id)
        assertTrue(sessionsAfter.isEmpty())
    }
    
    @Test
    fun `ID로 사용자를 조회할 수 있어야 한다`() {
        val foundUser = authService.getUserById(testUser.id)
        
        assertNotNull(foundUser)
        assertEquals(testUser.id, foundUser?.id)
        assertEquals(testUser.email, foundUser?.email)
    }
    
    @Test
    fun `존재하지 않는 ID로 사용자 조회시 null을 반환해야 한다`() {
        val foundUser = authService.getUserById(UUID.randomUUID())
        assertNull(foundUser)
    }
}