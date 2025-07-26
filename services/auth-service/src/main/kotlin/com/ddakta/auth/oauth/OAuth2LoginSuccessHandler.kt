package com.ddakta.auth.oauth

import com.ddakta.auth.service.JwtService
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import org.springframework.security.web.authentication.AuthenticationSuccessHandler
import org.springframework.stereotype.Component

@Component
class OAuth2LoginSuccessHandler(
    private val processors: List<SocialLoginProcessor>
) : AuthenticationSuccessHandler {

    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication
    ) {
        // 1. OAuth2AuthenticationToken 캐스팅
        val oauthToken = authentication as OAuth2AuthenticationToken
        val registrationId = oauthToken.authorizedClientRegistrationId

        // 2. 처리할 수 있는 Processor 찾기
        val processor = processors.find { it.supports(registrationId) }
            ?: throw IllegalArgumentException("지원하지 않는 OAuth2 로그인 방식입니다: $registrationId")

        // 3. 후처리 실행 (JWT 발급, 쿠키 전달 등)
        processor.process(oauthToken.principal, request, response)
    }
}
