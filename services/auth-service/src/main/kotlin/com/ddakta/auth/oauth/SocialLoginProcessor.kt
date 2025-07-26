package com.ddakta.auth.oauth

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.oauth2.core.user.OAuth2User

/**
 * OAuth2 로그인 성공 후 후처리 전략을 정의하는 인터페이스
 */
interface SocialLoginProcessor {
    /**
     * 해당 processor가 처리할 provider의 registrationId를 판단
     * 예: "google", "naver", "kakao" 등
     */
    fun supports(registrationId: String): Boolean

    /**
     * 실제 프로세싱 로직
     * - 회원 조회/등록
     * - JWT 발급
     * - 쿠키 전달
     * - 리다이렉트
     */
    fun process(
        oauth2User: OAuth2User,
        request: HttpServletRequest,
        response: HttpServletResponse
    )
}
