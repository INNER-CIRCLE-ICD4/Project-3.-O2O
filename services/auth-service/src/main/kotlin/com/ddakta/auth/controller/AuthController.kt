package com.ddakta.auth.controller

import com.ddakta.auth.common.annotation.CurrentUser
import com.ddakta.auth.domain.entity.User
import com.ddakta.auth.dto.AuthResponse
import com.ddakta.auth.dto.RefreshTokenRequest
import com.ddakta.auth.dto.UserInfo
import com.ddakta.auth.dto.OAuthEndpointsResponse
import com.ddakta.auth.service.AuthService
import com.ddakta.auth.security.AuthenticationPrincipal
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "인증 관련 API")
class AuthController(
    private val authService: AuthService
) {
    
    @PostMapping("/login")
    @Operation(summary = "OAuth 로그인", description = "OAuth 로그인 엔드포인트 정보 반환")
    fun login(): ResponseEntity<OAuthEndpointsResponse> {
        return ResponseEntity.ok(OAuthEndpointsResponse())
    }
    
    @PostMapping("/refresh")
    @Operation(summary = "토큰 갱신")
    fun refreshToken(
        @Valid @RequestBody request: RefreshTokenRequest
    ): ResponseEntity<AuthResponse> {
        return authService.refreshAccessToken(request.refreshToken)
            ?.let { ResponseEntity.ok(AuthResponse.from(it)) }
            ?: ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
    }
    
    @PostMapping("/logout")
    @Operation(summary = "로그아웃")
    @SecurityRequirement(name = "bearerAuth")
    fun logout(
        @CurrentUser principal: AuthenticationPrincipal
    ): ResponseEntity<Map<String, String>> {
        authService.logout(principal.userId)
        return ResponseEntity.ok(mapOf("message" to "Successfully logged out"))
    }
    
    @GetMapping("/me")
    @Operation(summary = "현재 사용자 정보")
    @SecurityRequirement(name = "bearerAuth")
    fun getCurrentUser(
        @CurrentUser user: User?
    ): ResponseEntity<UserInfo> {
        return user?.let { 
            ResponseEntity.ok(it.toUserInfo()) 
        } ?: ResponseEntity.notFound().build()
    }
}