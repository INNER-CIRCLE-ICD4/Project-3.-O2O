package com.ddakta.auth.service

import com.ddakta.auth.config.JwtProperties
import com.ddakta.auth.domain.entity.User
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import io.jsonwebtoken.security.Keys
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.stereotype.Service
import java.util.*
import javax.crypto.SecretKey

@Service
@EnableConfigurationProperties(JwtProperties::class)
class JwtTokenProvider(
    private val jwtProperties: JwtProperties
) {
    private val key: SecretKey = Keys.hmacShaKeyFor(jwtProperties.secret.toByteArray())
    
    fun generateAccessToken(user: User): String {
        val now = Date()
        val expiryDate = Date(now.time + jwtProperties.accessTokenValidity)
        
        return Jwts.builder()
            .setSubject(user.id.toString())
            .claim("email", user.email)
            .claim("role", user.role.name)
            .claim("provider", user.provider.name)
            .setIssuedAt(now)
            .setExpiration(expiryDate)
            .signWith(key, SignatureAlgorithm.HS256)
            .compact()
    }
    
    fun generateRefreshToken(): String {
        return UUID.randomUUID().toString()
    }
    
    fun validateToken(token: String): Boolean {
        return try {
            val claims = getClaims(token)
            !claims.expiration.before(Date())
        } catch (e: Exception) {
            false
        }
    }
    
    fun getUserIdFromToken(token: String): UUID {
        val claims = getClaims(token)
        return UUID.fromString(claims.subject)
    }
    
    fun getEmailFromToken(token: String): String {
        val claims = getClaims(token)
        return claims["email"] as String
    }
    
    fun getRoleFromToken(token: String): String {
        val claims = getClaims(token)
        return claims["role"] as String
    }
    
    private fun getClaims(token: String): Claims {
        return Jwts.parserBuilder()
            .setSigningKey(key)
            .build()
            .parseClaimsJws(token)
            .body
    }
}