package com.ddakta.domain.user

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class UserTest {

    @Nested
    inner class CreateFactoryTests {

        @Test
        fun `PASSENGER 타입 생성 시 role 은 ROLE_USER`() {
            val user = UserFixture.PASSENGER

            assertThat(user.username).isEqualTo(UserFixture.PASSENGER.username)
            assertThat(user.name).isEqualTo(UserFixture.PASSENGER.name)
            assertThat(user.email).isEqualTo(UserFixture.PASSENGER.email)
            assertThat(user.type).isEqualTo(UserType.PASSENGER)
            assertThat(user.role).isEqualTo("ROLE_USER")
        }

        @Test
        fun `DRIVER 타입 생성 시 role 은 ROLE_DRIVER`() {
            val user = UserFixture.DRIVER

            assertThat(user.type).isEqualTo(UserType.DRIVER)
            assertThat(user.role).isEqualTo("ROLE_DRIVER")
        }

        @Test
        fun `ADMIN 타입 생성 시 role 은 ROLE_ADMIN`() {
            val user = UserFixture.ADMIN

            assertThat(user.type).isEqualTo(UserType.ADMIN)
            assertThat(user.role).isEqualTo("ROLE_ADMIN")
        }
    }
}
