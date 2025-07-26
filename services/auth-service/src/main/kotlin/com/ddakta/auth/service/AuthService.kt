// services/auth-service/src/main/kotlin/com/ddakta/auth/service/AuthService.kt
package com.ddakta.auth.service

import com.ddakta.auth.dto.AuthResponse

interface AuthService {
    fun refreshToken(refreshToken: String): AuthResponse
    fun logout(userId: Long)
}
