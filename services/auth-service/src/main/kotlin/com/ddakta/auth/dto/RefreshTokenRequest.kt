package com.ddakta.auth.dto

import jakarta.validation.constraints.NotBlank

data class RefreshTokenRequest(
    @field:NotBlank(message = "Refresh token is required")
    val refreshToken: String
)