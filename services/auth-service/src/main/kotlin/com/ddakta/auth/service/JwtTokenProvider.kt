package com.ddakta.auth.service

import com.ddakta.auth.config.JwtProperties
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import io.jsonwebtoken.security.Keys
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.stereotype.Component
import java.security.Key
import java.util.Date

@Component
class JwtTokenProvider(
    private val props: JwtProperties
) {
    private val key: Key = Keys.hmacShaKeyFor(props.secret.toByteArray())

    // Access Token 발급
    fun createAccessToken(userId: Long, roles: List<String>): String {
        val now = Date()
        val expiry = Date(now.time + props.accessTokenExpirySeconds * 1000)
        return Jwts.builder()
            .setSubject(userId.toString())
            .claim("roles", roles)
            .setIssuedAt(now)
            .setExpiration(expiry)
            .signWith(key, SignatureAlgorithm.HS256)
            .compact()
    }

    // Refresh Token 발급
    fun createRefreshToken(userId: Long): String {
        val now = Date()
        val expiry = Date(now.time + props.refreshTokenExpirySeconds * 1000)
        return Jwts.builder()
            .setSubject(userId.toString())
            .setIssuedAt(now)
            .setExpiration(expiry)
            .signWith(key, SignatureAlgorithm.HS256)
            .compact()
    }

    // 유효성 검사
    fun validateToken(token: String): Boolean =
        try {
            Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
            true
        } catch (_: Exception) {
            false
        }

    // 토큰으로부터 Authentication 생성
    fun getAuthentication(token: String): Authentication {
        val claims = Jwts.parserBuilder()
            .setSigningKey(key)
            .build()
            .parseClaimsJws(token)
            .body

        val userId = claims.subject.toLong()
        @Suppress("UNCHECKED_CAST")
        val roles = claims["roles"] as List<String>
        val authorities = roles.map { SimpleGrantedAuthority(it) }

        return UsernamePasswordAuthenticationToken(userId, token, authorities)
    }
}
