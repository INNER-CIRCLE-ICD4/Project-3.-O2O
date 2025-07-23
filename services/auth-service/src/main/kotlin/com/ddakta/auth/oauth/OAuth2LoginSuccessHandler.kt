package com.ddakta.auth.oauth

import com.ddakta.auth.service.JwtService
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.Authentication
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler
import org.springframework.stereotype.Component

@Component
class OAuth2LoginSuccessHandler(
    val jwtService: JwtService
): SimpleUrlAuthenticationSuccessHandler() {
    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication
    ) {

        // TODO: 성공 시 url로 반환하도록...
        //  근데 앱의 경우는..? FireBase 연동? Deep Link?
        val successUrl = "http://localhost:8081/"

        val userDetails: OAuth2UserDetails = authentication.principal as OAuth2UserDetails
        val role = userDetails.authorities?.firstOrNull()?.authority ?: "ROLE_USER"

        val token = jwtService.createToken(userDetails.getUsername(), role)

        response.addCookie(createCookie("Authorization", token))
        response.sendRedirect(successUrl)
    }

    fun createCookie(key: String, value: String): Cookie {
            return Cookie(key, value).apply {
                path = "/"
                maxAge = 60*60*60
                isHttpOnly = true
            }
        }
}
