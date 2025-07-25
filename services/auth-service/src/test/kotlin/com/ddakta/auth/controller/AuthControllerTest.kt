package com.ddakta.auth.controller

import com.ddakta.auth.dto.RefreshTokenRequest
import com.ddakta.auth.domain.entity.User
import com.ddakta.auth.support.BaseIntegrationTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.util.*

class AuthControllerTest : BaseIntegrationTest() {
    
    private lateinit var testUser: User
    private lateinit var testAccessToken: String
    
    @BeforeEach
    fun setUp() {
        testUser = createTestUser()
        testAccessToken = jwtTokenProvider.generateAccessToken(testUser)
        createTokenSession(testUser, testAccessToken)
    }
    
    @Test
    fun `OAuth 로그인 엔드포인트를 반환해야 한다`() {
        performPost("/api/v1/auth/login")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.message").exists())
            .andExpect(jsonPath("$.google").value("/oauth2/authorization/google"))
            .andExpect(jsonPath("$.apple").value("/oauth2/authorization/apple"))
    }
    
    @Test
    fun `인증된 사용자의 정보를 조회할 수 있어야 한다`() {
        performGet("/api/v1/auth/me", testAccessToken)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(testUser.id.toString()))
            .andExpect(jsonPath("$.email").value(testUser.email))
            .andExpect(jsonPath("$.name").value(testUser.name))
            .andExpect(jsonPath("$.role").value(testUser.role.name))
    }
    
    @Test
    fun `토큰 없이 사용자 정보 조회시 401을 반환해야 한다`() {
        performGet("/api/v1/auth/me")
            .andExpect(status().isUnauthorized)
    }
    
    @Test
    fun `리프레시 토큰으로 액세스 토큰을 갱신할 수 있어야 한다`() {
        // 리프레시 토큰과 함께 토큰 세션 생성
        val refreshTokenValue = UUID.randomUUID().toString()
        createTokenSession(testUser, "old-access-token", refreshTokenValue)
        
        val request = RefreshTokenRequest(refreshToken = refreshTokenValue)
        
        performPost("/api/v1/auth/refresh", request)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accessToken").exists())
            .andExpect(jsonPath("$.refreshToken").exists())
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.expiresIn").value(900))
            .andExpect(jsonPath("$.userInfo.id").value(testUser.id.toString()))
            .andExpect(jsonPath("$.userInfo.email").value(testUser.email))
    }
    
    @Test
    fun `유효하지 않은 리프레시 토큰으로 갱신시 401을 반환해야 한다`() {
        val request = RefreshTokenRequest(refreshToken = "invalid-token")
        
        performPost("/api/v1/auth/refresh", request)
            .andExpect(status().isUnauthorized)
    }
    
    @Test
    fun `로그아웃을 성공적으로 수행해야 한다`() {
        performPost("/api/v1/auth/logout", token = testAccessToken)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.message").value("Successfully logged out"))
    }
    
    @Test
    fun `토큰 없이 로그아웃 시도시 401을 반환해야 한다`() {
        performPost("/api/v1/auth/logout")
            .andExpect(status().isUnauthorized)
    }
}