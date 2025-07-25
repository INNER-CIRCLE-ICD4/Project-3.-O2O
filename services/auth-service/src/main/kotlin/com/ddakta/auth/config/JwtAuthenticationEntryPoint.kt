package com.ddakta.auth.config

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.stereotype.Component

@Component
class JwtAuthenticationEntryPoint(
    private val objectMapper: ObjectMapper
) : AuthenticationEntryPoint {
    
    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException
    ) {
        response.contentType = "application/json"
        response.status = HttpServletResponse.SC_UNAUTHORIZED
        
        val errorResponse = mapOf(
            "error" to "Unauthorized",
            "message" to "Authentication required",
            "path" to request.servletPath
        )
        
        response.writer.write(objectMapper.writeValueAsString(errorResponse))
    }
}