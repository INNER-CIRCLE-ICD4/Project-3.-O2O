package com.ddakta.mockauth.controller

import com.ddakta.domain.user.UserRole
import com.ddakta.mockauth.service.MockAuthService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * 개발용 Mock 인증 엔드포인트
 * 지정된 역할로 테스트 사용자를 동적으로 생성합니다.
 */
@RestController
@RequestMapping("/api/test/auth")
@ConditionalOnProperty(name = ["test.auth.enabled"], havingValue = "true")
@Tag(name = "Mock 인증", description = "개발용 Mock 인증 (개발 환경 전용)")
class MockAuthController(
    private val mockAuthService: MockAuthService
) {
    
    @PostMapping("/passenger/{userId}")
    @Operation(
        summary = "테스트 승객으로 로그인",
        description = "테스트 승객 사용자를 생성하거나 조회하고 JWT 토큰을 반환합니다"
    )
    fun loginAsPassenger(@PathVariable userId: String): ResponseEntity<MockAuthService.TestAuthResult> {
        val result = mockAuthService.createOrGetTestUser(userId, UserRole.PASSENGER)
        return ResponseEntity.ok(result)
    }
    
    @PostMapping("/driver/{userId}")
    @Operation(
        summary = "테스트 드라이버로 로그인",
        description = "테스트 드라이버 사용자를 생성하거나 조회하고 JWT 토큰을 반환합니다"
    )
    fun loginAsDriver(@PathVariable userId: String): ResponseEntity<MockAuthService.TestAuthResult> {
        val result = mockAuthService.createOrGetTestUser(userId, UserRole.DRIVER)
        return ResponseEntity.ok(result)
    }
    
    @PostMapping("/admin/{userId}")
    @Operation(
        summary = "테스트 관리자로 로그인",
        description = "테스트 관리자 사용자를 생성하거나 조회하고 JWT 토큰을 반환합니다"
    )
    fun loginAsAdmin(@PathVariable userId: String): ResponseEntity<MockAuthService.TestAuthResult> {
        // 현재는 관리자가 특별한 권한을 가진 PASSENGER 역할을 사용합니다
        val result = mockAuthService.createOrGetTestUser("admin-$userId", UserRole.PASSENGER)
        return ResponseEntity.ok(result)
    }
}