package com.ddakta.auth.domain.model

import org.springframework.data.annotation.Id
import org.springframework.data.redis.core.RedisHash

@RedisHash("oauth_requests")
data class OAuthRequest(
    @Id val state: String,
    val codeVerifier: String,
    val createdAt: Long = System.currentTimeMillis()
)
