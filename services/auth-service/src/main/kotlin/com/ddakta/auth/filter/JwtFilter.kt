package com.ddakta.auth.filter

import com.ddakta.auth.entity.UserDto
import com.ddakta.auth.oauth.OAuth2UserDetails
import com.ddakta.auth.service.JwtService
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter


class JwtFilter(
    val jwtService: JwtService
): OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {

        val token = request.cookies
            ?.firstOrNull { it.name == "Authorization"}
            ?.value?.takeIf { it.startsWith("Bearer ") }
            ?: return filterChain.doFilter(request, response)


        val claims = jwtService.validateToken(token)
        val user = UserDto(
            username = jwtService.getUsername(claims),
            name = jwtService.getUsername(claims),
            role = jwtService.getRole(claims),
            email = jwtService.getEmail(claims)
        )


        val oAuth2User = OAuth2UserDetails(user)
        val authToken: Authentication = UsernamePasswordAuthenticationToken(oAuth2User, null, oAuth2User.authorities)
        SecurityContextHolder.getContext().authentication = authToken

        filterChain.doFilter(request, response)
    }
}
