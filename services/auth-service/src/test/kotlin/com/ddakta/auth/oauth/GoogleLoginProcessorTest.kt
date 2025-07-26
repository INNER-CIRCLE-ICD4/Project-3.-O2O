package com.ddakta.auth.oauth

import com.ddakta.auth.service.JwtService
import com.ddakta.domain.user.User
import com.ddakta.domain.user.UserService
import com.ddakta.domain.user.UserType
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.Mockito.never
import org.springframework.security.oauth2.core.user.DefaultOAuth2User
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.test.context.junit.jupiter.SpringExtension
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

@ExtendWith(SpringExtension::class)
class GoogleLoginProcessorTest {

    private lateinit var userService: UserService
    private lateinit var jwtService: JwtService
    private lateinit var processor: GoogleLoginProcessor

    private val mockRequest: HttpServletRequest = mock()
    private val mockResponse: HttpServletResponse = mock()

    @BeforeEach
    fun setUp() {
        userService = mock()
        jwtService = mock()
        processor = GoogleLoginProcessor(userService, jwtService)
    }

    @Test
    fun `Google registrationId를 지원하는지 확인`() {
        assertThat(processor.supports("google")).isTrue()
        assertThat(processor.supports("kakao")).isFalse()
    }

    @Test
    fun `기존 유저가 없으면 임시 등록 후 JWT 발급`() {
        // given
        val email = "test@example.com"
        val mockOAuth2User: OAuth2User = DefaultOAuth2User(emptyList(), mapOf("email" to email), "email")

        val user = User.create(email, "테스트", email, UserType.PASSENGER)

        whenever(userService.findByUsername(email)).thenReturn(null)
        whenever(userService.register(argThat {
            this.username == email && this.email == email && this.type == UserType.PASSENGER
        })).thenReturn(user)

        whenever(jwtService.createToken(any(), any())).thenReturn("mock-token")

        // when
        processor.process(mockOAuth2User, mockRequest, mockResponse)

        // then
        verify(userService).register(argThat {
            this.username == email && this.email == email && this.type == UserType.PASSENGER
        })

        verify(jwtService).createToken(user.username, user.type.name)

        verify(mockResponse).sendRedirect(argThat {
            val decoded = URLDecoder.decode(this, StandardCharsets.UTF_8)
            decoded.contains("token=mock-token")
        })
    }

    @Test
    fun `기존 유저가 있으면 등록하지 않고 JWT 발급`() {
        val email = "existing@example.com"
        val mockOAuth2User: OAuth2User = DefaultOAuth2User(emptyList(), mapOf("email" to email), "email")
        val user = User.create(email, "기존유저", email, UserType.PASSENGER)

        whenever(userService.findByUsername(email)).thenReturn(user)
        whenever(jwtService.createToken(any(), any())).thenReturn("mock-token")

        processor.process(mockOAuth2User, mockRequest, mockResponse)

        verify(userService).findByUsername(email)
        verify(userService, never()).register(any()) // ❗ 변경된 부분
        verify(jwtService).createToken(user.username, user.type.name)
        verify(mockResponse).sendRedirect(any())
    }

}
