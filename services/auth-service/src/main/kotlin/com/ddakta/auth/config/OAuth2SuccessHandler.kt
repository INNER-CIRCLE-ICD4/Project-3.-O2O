package com.ddakta.auth.config

import com.ddakta.auth.domain.enum.AuthProvider
import com.ddakta.auth.domain.entity.User
import com.ddakta.domain.user.UserRole
import com.ddakta.auth.service.AuthService
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler
import org.springframework.stereotype.Component
import org.springframework.web.util.UriComponentsBuilder

@Component
class OAuth2SuccessHandler(
    private val authService: AuthService
) : SimpleUrlAuthenticationSuccessHandler() {

    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication
    ) {
        val oAuth2User = authentication.principal as OAuth2User
        val provider = getProvider(authentication)
        val providerId = getProviderId(oAuth2User, provider)
        val email = getEmail(oAuth2User)
        val name = getName(oAuth2User, provider)
        val profileImageUrl = getProfileImageUrl(oAuth2User, provider)

        val roleParam = request.getParameter("role")
        val role = if (roleParam?.uppercase() == "DRIVER") UserRole.DRIVER else UserRole.PASSENGER

        val user = authService.findOrCreateUser(
            email = email,
            name = name,
            providerId = providerId,
            provider = provider,
            role = role,
            profileImageUrl = profileImageUrl
        )

        val (accessToken, refreshToken) = authService.createTokenSession(user)

        val targetUrl = UriComponentsBuilder.fromUriString("/auth/success")
            .queryParam("access_token", accessToken)
            .queryParam("refresh_token", refreshToken)
            .queryParam("user_role", user.role.name)
            .build().toUriString()

        redirectStrategy.sendRedirect(request, response, targetUrl)
    }

    private fun getProvider(authentication: Authentication): AuthProvider {
        val clientRegistrationId = authentication.authorities
            .firstOrNull()?.authority?.removePrefix("ROLE_") ?: ""

        return when {
            clientRegistrationId.contains("google", ignoreCase = true) -> AuthProvider.GOOGLE
            clientRegistrationId.contains("apple", ignoreCase = true) -> AuthProvider.APPLE
            else -> {
                // Request Path에서 Provider 정보 추출 시도
                val request = (authentication.details as? org.springframework.security.web.authentication.WebAuthenticationDetails)
                    ?.let { HttpServletRequest::class.java.cast(it) }
                val path = request?.requestURI ?: ""
                when {
                    path.contains("google", ignoreCase = true) -> AuthProvider.GOOGLE
                    path.contains("apple", ignoreCase = true) -> AuthProvider.APPLE
                    else -> AuthProvider.GOOGLE
                }
            }
        }
    }

    private fun getProviderId(oAuth2User: OAuth2User, provider: AuthProvider): String {
        return when (provider) {
            AuthProvider.GOOGLE -> oAuth2User.getAttribute("sub") ?: throw IllegalArgumentException("Google sub not found")
            AuthProvider.APPLE -> oAuth2User.getAttribute("sub") ?: throw IllegalArgumentException("Apple sub not found")
        }
    }

    private fun getEmail(oAuth2User: OAuth2User): String {
        return oAuth2User.getAttribute("email") ?: throw IllegalArgumentException("Email not found")
    }

    private fun getName(oAuth2User: OAuth2User, provider: AuthProvider): String {
        return when (provider) {
            AuthProvider.GOOGLE -> oAuth2User.getAttribute("name") ?: "Unknown"
            AuthProvider.APPLE -> {
                val firstName = oAuth2User.getAttribute<String>("firstName") ?: ""
                val lastName = oAuth2User.getAttribute<String>("lastName") ?: ""
                "$firstName $lastName".trim().ifEmpty { "Unknown" }
            }
        }
    }

    private fun getProfileImageUrl(oAuth2User: OAuth2User, provider: AuthProvider): String? {
        return when (provider) {
            AuthProvider.GOOGLE -> oAuth2User.getAttribute("picture")
            AuthProvider.APPLE -> null
        }
    }
}
