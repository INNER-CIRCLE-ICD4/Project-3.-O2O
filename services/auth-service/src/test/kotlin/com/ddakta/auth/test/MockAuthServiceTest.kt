package com.ddakta.auth.test

import com.ddakta.auth.domain.entity.User
import com.ddakta.auth.domain.enum.AuthProvider
import com.ddakta.auth.service.AuthService
import com.ddakta.domain.user.UserRole
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import java.util.UUID

/**
 * MockAuthService 단위 테스트
 * 동적 사용자 생성 및 JWT 토큰 발급 기능 검증
 */
@ExtendWith(MockitoExtension::class)
class MockAuthServiceTest {

    @Mock
    private lateinit var authService: AuthService

    @InjectMocks
    private lateinit var mockAuthService: MockAuthService

    @Test
    fun `승객 역할로 테스트 사용자 생성 및 토큰 발급`() {
        // given
        val userId = "testuser123"
        val role = UserRole.PASSENGER
        val expectedUser = User(
            email = "test.passenger.testuser123@example.com",
            name = "Test Passenger testuser123",
            providerId = "test-PASSENGER-testuser123",
            provider = AuthProvider.GOOGLE,
            role = role,
            profileImageUrl = "https://ui-avatars.com/api/?name=Test+Passenger+testuser123"
        )
        
        whenever(authService.findOrCreateUser(any(), any(), any(), any(), any(), any()))
            .thenReturn(expectedUser)
        whenever(authService.createTokenSession(any()))
            .thenReturn(Pair("access-token", "refresh-token"))

        // when
        val result = mockAuthService.createOrGetTestUser(userId, role)

        // then
        assertEquals("access-token", result.accessToken)
        assertEquals("refresh-token", result.refreshToken)
        assertEquals(expectedUser.id.toString(), result.user.id)
        assertEquals(expectedUser.email, result.user.email)
        assertEquals(expectedUser.name, result.user.name)
        assertEquals(role, result.user.role)
    }

    @Test
    fun `드라이버 역할로 테스트 사용자 생성 및 토큰 발급`() {
        // given
        val userId = "driver456"
        val role = UserRole.DRIVER
        val expectedUser = User(
            email = "test.driver.driver456@example.com",
            name = "Test Driver driver456",
            providerId = "test-DRIVER-driver456",
            provider = AuthProvider.GOOGLE,
            role = role,
            profileImageUrl = "https://ui-avatars.com/api/?name=Test+Driver+driver456"
        )
        
        whenever(authService.findOrCreateUser(any(), any(), any(), any(), any(), any()))
            .thenReturn(expectedUser)
        whenever(authService.createTokenSession(any()))
            .thenReturn(Pair("driver-access-token", "driver-refresh-token"))

        // when
        val result = mockAuthService.createOrGetTestUser(userId, role)

        // then
        assertEquals("driver-access-token", result.accessToken)
        assertEquals("driver-refresh-token", result.refreshToken)
        assertEquals(expectedUser.id.toString(), result.user.id)
        assertEquals(expectedUser.email, result.user.email)
        assertEquals(expectedUser.name, result.user.name)
        assertEquals(role, result.user.role)
    }

    @Test
    fun `동일한 userId로 재호출 시 동일한 사용자 반환`() {
        // given
        val userId = "sameuser"
        val role = UserRole.PASSENGER
        val expectedUser = User(
            email = "test.passenger.sameuser@example.com",
            name = "Test Passenger sameuser",
            providerId = "test-PASSENGER-sameuser",
            provider = AuthProvider.GOOGLE,
            role = role,
            profileImageUrl = "https://ui-avatars.com/api/?name=Test+Passenger+sameuser"
        )
        
        whenever(authService.findOrCreateUser(any(), any(), any(), any(), any(), any()))
            .thenReturn(expectedUser)
        whenever(authService.createTokenSession(any()))
            .thenReturn(Pair("same-access-token", "same-refresh-token"))

        // when
        val result1 = mockAuthService.createOrGetTestUser(userId, role)
        val result2 = mockAuthService.createOrGetTestUser(userId, role)

        // then
        assertEquals(result1.user.id, result2.user.id)
        assertEquals(result1.user.email, result2.user.email)
        assertEquals(result1.user.name, result2.user.name)
    }
}