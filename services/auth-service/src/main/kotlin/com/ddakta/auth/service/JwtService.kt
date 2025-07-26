package com.ddakta.auth.service

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.Date
import javax.crypto.SecretKey

@Service
class JwtService(
    @Value("\${jwt.secret}") secret: String
) {
    private val secretKey: SecretKey = Keys.hmacShaKeyFor(secret.toByteArray())
    private val validityMs: Long = 60 * 60 * 1000 // 1시간

    fun createToken(username: String, role: String): String {
        val now = Date()
        return Jwts.builder()
            .setSubject(username)
            .claim("role", role)
            .setIssuedAt(now)
            .setExpiration(Date(now.time + validityMs))
            .signWith(secretKey)
            .compact()
    }

    fun validateToken(token: String): Claims {
        return Jwts.parserBuilder()
            .setSigningKey(secretKey)
            .build()
            .parseClaimsJws(token)
            .body
    }
}

