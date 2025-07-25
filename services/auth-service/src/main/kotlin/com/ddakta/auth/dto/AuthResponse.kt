package com.ddakta.auth.dto

data class AuthResponse(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String = "Bearer",
    val expiresIn: Long = 900,
    val userInfo: UserInfo
) {
    companion object {
        fun from(refreshResponse: RefreshTokenResponse): AuthResponse {
            return AuthResponse(
                accessToken = refreshResponse.accessToken,
                refreshToken = refreshResponse.refreshToken,
                userInfo = refreshResponse.user.toUserInfo()
            )
        }
    }
}
