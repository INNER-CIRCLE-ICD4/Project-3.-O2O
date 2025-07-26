package com.ddakta.auth.config

import com.ddakta.auth.service.JwtService
import io.jsonwebtoken.Claims
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

class JwtFilter(
    private val jwtService: JwtService
): OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val authHeader = request.getHeader("Authorization") ?: return filterChain.doFilter(request, response)
        val token = authHeader.removePrefix("Bearer ")
        val claims: Claims = try {
            jwtService.validateToken(token)
        } catch (e: Exception) {
            return filterChain.doFilter(request, response)
        }
        val username = claims.subject
        val role = claims.get("role", String::class.java)
        val auth = UsernamePasswordAuthenticationToken(
            username,
            null,
            listOf(SimpleGrantedAuthority(role))
        )
        SecurityContextHolder.getContext().authentication = auth
        filterChain.doFilter(request, response)
    }
}
