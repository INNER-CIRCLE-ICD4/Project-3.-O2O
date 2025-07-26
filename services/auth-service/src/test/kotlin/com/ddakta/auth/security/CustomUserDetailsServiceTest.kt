package com.ddakta.auth.security

import com.ddakta.domain.user.User
import com.ddakta.domain.user.UserService
import com.ddakta.domain.user.UserType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import org.springframework.security.core.userdetails.UsernameNotFoundException

class CustomUserDetailsServiceTest {

    private val userService: UserService = mock()
    private val userDetailsService = CustomUserDetailsService(userService)

    @Test
    fun `존재하는 유저명으로 조회하면 UserPrincipal을 반환한다`() {
        // given
        val email = "test@example.com"
        val user = User.create(email, "테스트", email, UserType.PASSENGER)
        whenever(userService.findByUsername(email)).thenReturn(user)

        // when
        val userDetails = userDetailsService.loadUserByUsername(email)

        // then
        assertTrue(userDetails is UserPrincipal)
        assertEquals(email, userDetails.username)
        assertEquals("ROLE_USER", userDetails.authorities.first().authority)
    }

    @Test
    fun `존재하지 않는 유저명으로 조회하면 예외 발생`() {
        // given
        val email = "nonexistent@example.com"
        whenever(userService.findByUsername(email)).thenReturn(null)

        // then
        assertThrows<UsernameNotFoundException> {
            userDetailsService.loadUserByUsername(email)
        }
    }
}
