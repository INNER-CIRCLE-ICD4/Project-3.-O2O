package com.ddakta.auth.service

import com.ddakta.auth.config.JwtProperties
import com.ddakta.auth.domain.model.TokenSession
import com.ddakta.auth.dto.AuthResponse
import com.ddakta.auth.repository.TokenSessionRepository
import com.ddakta.auth.repository.UserRepository
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class AuthServiceImpl(
    private val jwtTokenProvider: JwtTokenProvider,
    private val tokenSessionRepository: TokenSessionRepository,
    private val userRepository: UserRepository,
    private val jwtProps: JwtProperties
) : AuthService {

    override fun refreshToken(refreshToken: String): AuthResponse {
        val session = tokenSessionRepository.findById(refreshToken)
            .orElseThrow { IllegalArgumentException("Invalid refresh token") }

        val user = userRepository.findById(session.userId)
            .orElseThrow { IllegalStateException("User not found") }

        // 새 토큰 발급: roles.toList() 로 List<String> 전달
        val newAccess  = jwtTokenProvider.createAccessToken(user.id!!, user.roles.toList())
        val newRefresh = jwtTokenProvider.createRefreshToken(user.id!!)

        // 세션 갱신
        tokenSessionRepository.deleteById(refreshToken)
        tokenSessionRepository.save(
            TokenSession(
                refreshToken = newRefresh,
                userId       = user.id,
                createdAt    = Instant.now()
            )
        )

        return AuthResponse(
            accessToken  = newAccess,
            refreshToken = newRefresh,
            expiresIn    = jwtProps.accessTokenExpirySeconds
        )
    }

    override fun logout(userId: Long) {
        tokenSessionRepository.deleteAllByUserId(userId)
    }
}
