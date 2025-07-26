package com.ddakta.auth.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class JwtServiceTest {

    // 테스트용 시크릿 (실제 운영에선 절대 이렇게 쓰지 마세요!)
    private val service = JwtService("0123456789abcdef0123456789abcdef")

    @Test
    fun `토큰 생성 후 파싱하면 username 과 role 이 동일해야 한다`() {
        val token = service.createToken("user1", "ROLE_USER")
        val claims = service.validateToken(token)

        assertThat(claims.subject).isEqualTo("user1")
        assertThat(claims["role", String::class.java]).isEqualTo("ROLE_USER")
    }
}
