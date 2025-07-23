package com.ddakta.auth.service

import com.ddakta.auth.entity.User
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.util.Date
import javax.crypto.SecretKey

@Component
class JwtService (
    @Value("\${jwt.secret}")
    val secret: String
){

    val secretKey: SecretKey = Keys.hmacShaKeyFor(secret.toByteArray(StandardCharsets.UTF_8))
    val expiration = 3600000

    fun createToken(username: String, role: String): String {
        val currentTimeMills = System.currentTimeMillis()
        return Jwts.builder()
            .setSubject(username)
            .claim("username", username)
            .claim("role", role)
            .setIssuedAt(Date(currentTimeMills))
            .setExpiration(Date(currentTimeMills+expiration))
            .signWith(secretKey)
            .compact()
    }

    fun validateToken(token:String): Claims {
        try {
            return parseJwtClaims(token)
        }catch (e: Exception) {
            throw IllegalArgumentException("Invalid Token")
        }
    }

    fun parseJwtClaims(token: String): Claims {
        return Jwts.parserBuilder()
            .setSigningKey(secretKey).build()
            .parseClaimsJws(token)
            .body
    }

    fun isExpired(claims: Claims): Boolean  = claims.expiration.before(Date())

    fun getUsername(claims: Claims): String = claims.get("username", String::class.java)
    fun getRole(claims: Claims): String = claims.get("role", String::class.java)
}
