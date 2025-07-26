package com.ddakta.auth.domain.model

import org.springframework.data.annotation.Id
import org.springframework.data.redis.core.RedisHash
import java.time.Instant

/**
 * Refresh Token → User 매핑 (Redis)
 */
@RedisHash(value = "token_sessions", timeToLive = 604_800)
data class TokenSession(
    @Id
    val refreshToken: String,
    val userId: Long,
    val createdAt: Instant = Instant.now()
)
