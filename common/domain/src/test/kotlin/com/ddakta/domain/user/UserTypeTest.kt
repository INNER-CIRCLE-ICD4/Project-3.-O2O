package com.ddakta.domain.user

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class UserTypeTest {

    @Test
    fun `UserType enum 에 PASSENGER, DRIVER, ADMIN 이 모두 정의되어 있다`() {
        val values = UserType.values().toList()
        assertThat(values)
            .containsExactlyInAnyOrder(
                UserType.PASSENGER,
                UserType.DRIVER,
                UserType.ADMIN
            )
    }

    @Test
    fun `새로운 UserType 값이 추가되면 테스트를 업데이트 해야 한다`() {
        val expectedNames = listOf("PASSENGER", "DRIVER", "ADMIN")
        val actualNames = UserType.values().map { it.name }
        assertThat(actualNames).containsExactlyInAnyOrderElementsOf(expectedNames)
    }
}
