package com.ddakta.auth.controller

import com.ddakta.auth.dto.AuthResponse
import com.ddakta.auth.dto.RefreshTokenRequest
import com.ddakta.auth.service.AuthService
import com.ddakta.auth.service.KakaoOAuthService
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val kakaoOAuthService: KakaoOAuthService,
    private val authService: AuthService
) {

    // 1) 클라이언트에 제공할 카카오 인증 URL
    @GetMapping("/kakao/url")
    fun getKakaoUrl(): Map<String, String> =
        mapOf("url" to kakaoOAuthService.getAuthorizationUrl())

    // 2) 카카오 로그인 콜백
    @GetMapping("/kakao/callback")
    fun kakaoCallback(
        @RequestParam code: String,
        @RequestParam state: String
    ): AuthResponse =
        kakaoOAuthService.login(code, state)

    // 3) Refresh 토큰으로 재발급
    @PostMapping("/refresh")
    fun refresh(@RequestBody req: RefreshTokenRequest): AuthResponse =
        authService.refreshToken(req.refreshToken)

    // 4) 로그아웃 (현재 토큰의 userId 로 세션 삭제)
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun logout(@AuthenticationPrincipal userId: Long) {
        authService.logout(userId)
    }
}
