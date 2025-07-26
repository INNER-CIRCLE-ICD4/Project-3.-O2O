package com.ddakta.utils.security

import com.ddakta.domain.user.UserRole
import java.util.*

/**
 * JWT 토큰에서 추출된 인증 정보
 * 모든 서비스에서 공통으로 사용
 */
data class AuthenticationPrincipal(
    val userId: UUID,
    val email: String,
    val role: UserRole
) {
    fun isDriver(): Boolean = role == UserRole.DRIVER
    fun isPassenger(): Boolean = role == UserRole.PASSENGER
}