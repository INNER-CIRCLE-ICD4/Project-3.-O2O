package com.ddakta.domain.user

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class UserTest {

    @Nested
    inner class CreateFactoryTests {

        @Test
        fun `PASSENGER 타입 생성 시 role 은 ROLE_USER`() {
            val user = User.create(
                username = "social_123",
                name     = "홍길동",
                email    = "hong@example.com",
                type     = UserType.PASSENGER
            )

            assertThat(user.username).isEqualTo("social_123")
            assertThat(user.name).isEqualTo("홍길동")
            assertThat(user.email).isEqualTo("hong@example.com")
            assertThat(user.type).isEqualTo(UserType.PASSENGER)
            assertThat(user.role).isEqualTo("ROLE_USER")
        }

        @Test
        fun `DRIVER 타입 생성 시 role 은 ROLE_DRIVER`() {
            val user = User.create(
                username = "social_999",
                name     = "김기사",
                email    = "kim@example.com",
                type     = UserType.DRIVER
            )

            assertThat(user.type).isEqualTo(UserType.DRIVER)
            assertThat(user.role).isEqualTo("ROLE_DRIVER")
        }

        @Test
        fun `ADMIN 타입 생성 시 role 은 ROLE_ADMIN`() {
            val user = User.create(
                username = "admin_001",
                name     = "운영자",
                email    = "admin@example.com",
                type     = UserType.ADMIN
            )

            assertThat(user.type).isEqualTo(UserType.ADMIN)
            assertThat(user.role).isEqualTo("ROLE_ADMIN")
        }
    }
}
