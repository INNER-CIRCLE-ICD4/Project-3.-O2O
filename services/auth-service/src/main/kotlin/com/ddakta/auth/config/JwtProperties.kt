package com.ddakta.auth.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.ConstructorBinding

@ConfigurationProperties(prefix = "jwt")
data class JwtProperties @ConstructorBinding constructor(
    val secret: String,
    val accessTokenValidity: Long,
    val refreshTokenValidity: Long
)