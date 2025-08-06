// services/location-service/src/main/kotlin/com/ddakta/location/service/JwtUtil.kt
package com.ddakta.location.service

import com.ddakta.utils.security.AuthenticationPrincipal
import com.ddakta.domain.user.UserRole
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.*

@Component
class JwtUtil(
    @Value("\${security.jwt.secret}") val secret: String
) {
    private lateinit var key: java.security.Key

    @PostConstruct
    fun init() {
        // secret이 Base64 인코딩된 경우
        key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(secret))
    }

    fun validateToken(token: String): Boolean = try {
        Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token)
        true
    } catch (_: Exception) {
        false
    }

    fun parsePrincipal(token: String): AuthenticationPrincipal {
        val claims: Claims = Jwts.parserBuilder()
            .setSigningKey(key)
            .build()
            .parseClaimsJws(token)
            .body

        val userId = UUID.fromString(claims.subject)
        val email  = claims["email"] as? String ?: ""
        val roleStr = claims["role"] as? String
            ?: throw IllegalArgumentException("JWT에 role 클레임이 없습니다")
        val role = UserRole.valueOf(roleStr)

        return AuthenticationPrincipal(
            userId = userId,
            email  = email,
            role   = role
        )
    }
}
