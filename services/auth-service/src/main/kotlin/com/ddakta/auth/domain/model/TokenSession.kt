package com.ddakta.auth.domain.model

import org.springframework.data.annotation.Id
import org.springframework.data.redis.core.RedisHash
import org.springframework.data.redis.core.TimeToLive
import org.springframework.data.redis.core.index.Indexed
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.TimeUnit

@RedisHash("TokenSession")
data class TokenSession(
    @Id
    val sessionId: String,
    
    @Indexed
    val userId: UUID,
    
    @Indexed
    val accessToken: String,
    
    @Indexed
    val refreshToken: String,
    
    val userEmail: String,
    
    val createdAt: LocalDateTime = LocalDateTime.now(),
    
    val expiresAt: LocalDateTime,
    
    @TimeToLive(unit = TimeUnit.SECONDS)
    val ttl: Long
)