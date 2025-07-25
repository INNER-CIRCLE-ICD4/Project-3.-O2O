package com.ddakta.auth.service

import com.ddakta.auth.domain.enum.AuthProvider
import com.ddakta.auth.domain.entity.User
import com.ddakta.domain.user.UserRole
import com.ddakta.auth.domain.model.TokenSession
import com.ddakta.auth.domain.repository.TokenSessionRepository
import com.ddakta.auth.domain.repository.UserRepository
import com.ddakta.auth.dto.RefreshTokenResponse
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.*

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val tokenSessionRepository: TokenSessionRepository,
    private val jwtTokenProvider: JwtTokenProvider
) {

    @Transactional
    fun findOrCreateUser(
        email: String,
        name: String,
        providerId: String,
        provider: AuthProvider,
        role: UserRole,
        profileImageUrl: String?
    ): User {
        return userRepository.findByProviderAndProviderId(provider, providerId)
            .orElseGet {
                val newUser = User(
                    email = email,
                    name = name,
                    providerId = providerId,
                    provider = provider,
                    role = role,
                    profileImageUrl = profileImageUrl
                )
                userRepository.save(newUser)
            }
    }

    @Transactional
    fun createTokenSession(user: User): Pair<String, String> {
        // 기존 세션 삭제를 위해 벌크 연산으로 최적화
        val existingSessions = tokenSessionRepository.findByUserId(user.id)
        if (existingSessions.isNotEmpty()) {
            tokenSessionRepository.deleteAll(existingSessions)
        }

        val sessionId = UUID.randomUUID().toString()
        val accessToken = jwtTokenProvider.generateAccessToken(user)
        val refreshToken = jwtTokenProvider.generateRefreshToken()
        val expiresAt = LocalDateTime.now().plusDays(7)

        val tokenSession = TokenSession(
            sessionId = sessionId,
            userId = user.id,
            accessToken = accessToken,
            refreshToken = refreshToken,
            userEmail = user.email,
            expiresAt = expiresAt,
            ttl = 604800
        )

        tokenSessionRepository.save(tokenSession)

        return Pair(accessToken, refreshToken)
    }

    @Transactional
    fun refreshAccessToken(refreshToken: String): RefreshTokenResponse? {
        val tokenSession = tokenSessionRepository.findByRefreshToken(refreshToken)
            .orElse(null) ?: return null

        val user = userRepository.findById(tokenSession.userId)
            .orElse(null) ?: return null

        tokenSessionRepository.deleteById(tokenSession.sessionId)

        val sessionId = UUID.randomUUID().toString()
        val newAccessToken = jwtTokenProvider.generateAccessToken(user)
        val newRefreshToken = jwtTokenProvider.generateRefreshToken()
        val expiresAt = LocalDateTime.now().plusDays(7)

        val newTokenSession = TokenSession(
            sessionId = sessionId,
            userId = user.id,
            accessToken = newAccessToken,
            refreshToken = newRefreshToken,
            userEmail = user.email,
            expiresAt = expiresAt,
            ttl = 604800
        )

        tokenSessionRepository.save(newTokenSession)

        return RefreshTokenResponse(
            accessToken = newAccessToken,
            refreshToken = newRefreshToken,
            user = user
        )
    }

    @Transactional
    @CacheEvict(value = ["users"], key = "#userId")
    fun logout(userId: UUID) {
        val sessions = tokenSessionRepository.findByUserId(userId)
        if (sessions.isNotEmpty()) {
            tokenSessionRepository.deleteAll(sessions)
        }
    }

    @Transactional(readOnly = true)
    @Cacheable(value = ["users"], key = "#userId")
    fun getUserById(userId: UUID): User? {
        return userRepository.findById(userId).orElse(null)
    }
}
