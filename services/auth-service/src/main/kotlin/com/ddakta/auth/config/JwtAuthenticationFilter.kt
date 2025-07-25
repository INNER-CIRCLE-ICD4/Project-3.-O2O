package com.ddakta.auth.config

import com.ddakta.auth.service.AuthService
import com.ddakta.auth.service.JwtTokenProvider
import com.ddakta.auth.security.AuthenticationPrincipal
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthenticationFilter(
    private val jwtTokenProvider: JwtTokenProvider,
    private val authService: AuthService
) : OncePerRequestFilter() {
    
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        try {
            val token = getJwtFromRequest(request)
            
            if (!token.isNullOrBlank() && jwtTokenProvider.validateToken(token)) {
                val userId = jwtTokenProvider.getUserIdFromToken(token)
                val email = jwtTokenProvider.getEmailFromToken(token)
                val role = jwtTokenProvider.getRoleFromToken(token)
                
                val principal = AuthenticationPrincipal(
                    userId = userId,
                    email = email,
                    role = role
                )
                
                val authorities = listOf(SimpleGrantedAuthority("ROLE_$role"))
                
                val authentication = UsernamePasswordAuthenticationToken(
                    principal,
                    null,
                    authorities
                )
                
                authentication.details = WebAuthenticationDetailsSource().buildDetails(request)
                SecurityContextHolder.getContext().authentication = authentication
            }
        } catch (ex: Exception) {
            logger.error("Could not set user authentication in security context", ex)
        }
        
        filterChain.doFilter(request, response)
    }
    
    private fun getJwtFromRequest(request: HttpServletRequest): String? {
        val bearerToken = request.getHeader("Authorization")
        return if (!bearerToken.isNullOrBlank() && bearerToken.startsWith("Bearer ")) {
            bearerToken.substring(7)
        } else {
            null
        }
    }
}