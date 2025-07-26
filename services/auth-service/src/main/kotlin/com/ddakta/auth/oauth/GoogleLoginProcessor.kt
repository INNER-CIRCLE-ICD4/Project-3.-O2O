package com.ddakta.auth.oauth

import com.ddakta.auth.service.JwtService
import com.ddakta.domain.user.User
import com.ddakta.domain.user.UserService
import com.ddakta.domain.user.UserType
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.stereotype.Component
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Component
class GoogleLoginProcessor(
    private val userService: UserService,
    private val jwtService: JwtService
) : SocialLoginProcessor {

    override fun supports(registrationId: String): Boolean = registrationId == "google"

    override fun process(
        oauth2User: OAuth2User,
        request: HttpServletRequest,
        response: HttpServletResponse
    ) {
        val attributes = oauth2User.attributes
        val email = attributes["email"] as String? ?: throw IllegalArgumentException("이메일 정보 없음")

        // 기존 회원이 없으면 임시 회원 등록
        val user = userService.findByUsername(email)
            ?: userService.register(User.create(email, "Unknown", email, UserType.PASSENGER))

        // JWT 발급
        val token = jwtService.createToken(user.username, user.type.name)

        // 쿠키에 토큰 담기 (보안을 위해 HttpOnly 설정 고려 가능)
        val encodedToken = URLEncoder.encode(token, StandardCharsets.UTF_8)
        response.sendRedirect("http://localhost:3000/oauth?token=$encodedToken")
    }
}
