package com.ddakta.auth.dto

data class AuthResponse(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long
)
