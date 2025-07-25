package com.ddakta.auth.support

import com.ddakta.auth.domain.entity.User
import com.ddakta.auth.domain.enum.AuthProvider
import com.ddakta.auth.domain.model.TokenSession
import com.ddakta.auth.domain.repository.TokenSessionRepository
import com.ddakta.auth.domain.repository.UserRepository
import com.ddakta.auth.service.JwtTokenProvider
import com.ddakta.domain.user.UserRole
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import java.time.LocalDateTime
import java.util.*

abstract class TestSupport {
    
    @Autowired
    protected lateinit var mockMvc: MockMvc
    
    @Autowired
    protected lateinit var objectMapper: ObjectMapper
    
    @Autowired
    protected lateinit var userRepository: UserRepository
    
    @Autowired
    protected lateinit var tokenSessionRepository: TokenSessionRepository
    
    @Autowired
    protected lateinit var jwtTokenProvider: JwtTokenProvider
    
    /**
     * 테스트 유저 생성
     */
    protected fun createTestUser(
        email: String = "test@example.com",
        name: String = "Test User",
        providerId: String = "google-123",
        provider: AuthProvider = AuthProvider.GOOGLE,
        role: UserRole = UserRole.PASSENGER,
        profileImageUrl: String? = null
    ): User {
        return userRepository.save(
            User(
                email = email,
                name = name,
                providerId = providerId,
                provider = provider,
                role = role,
                profileImageUrl = profileImageUrl
            )
        )
    }
    
    /**
     * 토큰 세션 생성
     */
    protected fun createTokenSession(
        user: User,
        accessToken: String? = null,
        refreshToken: String? = null
    ): TokenSession {
        return tokenSessionRepository.save(
            TokenSession(
                sessionId = UUID.randomUUID().toString(),
                userId = user.id,
                accessToken = accessToken ?: jwtTokenProvider.generateAccessToken(user),
                refreshToken = refreshToken ?: UUID.randomUUID().toString(),
                userEmail = user.email,
                expiresAt = LocalDateTime.now().plusDays(7),
                ttl = 604800
            )
        )
    }
    
    /**
     * GET 요청 수행
     */
    protected fun performGet(url: String, token: String? = null): ResultActions {
        val request = MockMvcRequestBuilders.get(url)
        token?.let { request.header("Authorization", "Bearer $it") }
        return mockMvc.perform(request)
    }
    
    /**
     * POST 요청 수행 (JSON body 포함)
     */
    protected fun performPost(url: String, body: Any? = null, token: String? = null): ResultActions {
        val request = MockMvcRequestBuilders.post(url)
        token?.let { request.header("Authorization", "Bearer $it") }
        body?.let {
            request
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(it))
        }
        return mockMvc.perform(request)
    }
    
    /**
     * 모든 토큰 세션 삭제
     */
    protected fun clearAllTokenSessions() {
        tokenSessionRepository.deleteAll()
    }
    
    /**
     * 모든 유저 삭제
     */
    protected fun clearAllUsers() {
        userRepository.deleteAll()
    }
}