package com.ddakta.auth.service

import com.ddakta.auth.domain.entity.User
import com.ddakta.auth.support.BaseUnitTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class JwtTokenProviderTest : BaseUnitTest() {
    
    private lateinit var jwtTokenProvider: JwtTokenProvider
    private lateinit var testUser: User
    
    @BeforeEach
    fun setUp() {
        jwtTokenProvider = JwtTokenProvider(createTestJwtProperties())
        testUser = createTestUserEntity()
    }
    
    @Test
    fun `유효한 액세스 토큰을 생성할 수 있어야 한다`() {
        val token = jwtTokenProvider.generateAccessToken(testUser)
        
        assertNotNull(token)
        assertTrue(token.isNotBlank())
        assertTrue(jwtTokenProvider.validateToken(token))
    }
    
    @Test
    fun `토큰에서 사용자 ID를 추출할 수 있어야 한다`() {
        val token = jwtTokenProvider.generateAccessToken(testUser)
        val userId = jwtTokenProvider.getUserIdFromToken(token)
        
        assertEquals(testUser.id, userId)
    }
    
    @Test
    fun `토큰에서 이메일을 추출할 수 있어야 한다`() {
        val token = jwtTokenProvider.generateAccessToken(testUser)
        val email = jwtTokenProvider.getEmailFromToken(token)
        
        assertEquals(testUser.email, email)
    }
    
    @Test
    fun `토큰에서 역할을 추출할 수 있어야 한다`() {
        val token = jwtTokenProvider.generateAccessToken(testUser)
        val role = jwtTokenProvider.getRoleFromToken(token)
        
        assertEquals(testUser.role.name, role)
    }
    
    @Test
    fun `고유한 리프레시 토큰을 생성할 수 있어야 한다`() {
        val refreshToken1 = jwtTokenProvider.generateRefreshToken()
        val refreshToken2 = jwtTokenProvider.generateRefreshToken()
        
        assertNotNull(refreshToken1)
        assertNotNull(refreshToken2)
        assertNotEquals(refreshToken1, refreshToken2)
    }
    
    @Test
    fun `유효하지 않은 토큰에 대해 false를 반환해야 한다`() {
        val invalidToken = "invalid.token.here"
        
        assertFalse(jwtTokenProvider.validateToken(invalidToken))
    }
    
    @Test
    fun `유효하지 않은 토큰에서 정보 추출시 예외가 발생해야 한다`() {
        val invalidToken = "invalid.token.here"
        
        assertThrows<Exception> {
            jwtTokenProvider.getUserIdFromToken(invalidToken)
        }
    }
}