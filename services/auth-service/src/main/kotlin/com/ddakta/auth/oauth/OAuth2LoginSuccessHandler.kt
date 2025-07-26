package com.ddakta.auth.oauth

import com.ddakta.auth.entity.UserRole
import com.ddakta.auth.service.JwtService
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.Authentication
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler
import org.springframework.stereotype.Component

@Component
class OAuth2LoginSuccessHandler(
    val jwtService: JwtService,
    @Value("\${client.web.successUrl}")
    val successUrl: String
): SimpleUrlAuthenticationSuccessHandler() {
    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication
    ) {

        val userDetails: OAuth2UserDetails = authentication.principal as OAuth2UserDetails
        val role  = userDetails.authorities!!.firstOrNull()?.authority ?: UserRole.PASSENGER.type

        val access = jwtService.createAccessToken(userDetails.getUsername(), role)
        val refresh = jwtService.createRefreshToken(userDetails.getUsername(), role)

        //TODO :refresh 저장


        //access 전달
        response.addCookie(createCookie("Authorization", "Bearer $access"))
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
