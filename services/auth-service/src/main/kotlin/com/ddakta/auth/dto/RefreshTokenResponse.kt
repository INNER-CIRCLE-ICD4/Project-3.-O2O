package com.ddakta.auth.dto

import com.ddakta.auth.domain.entity.User

data class RefreshTokenResponse(
    val accessToken: String,
    val refreshToken: String,
    val user: User
)