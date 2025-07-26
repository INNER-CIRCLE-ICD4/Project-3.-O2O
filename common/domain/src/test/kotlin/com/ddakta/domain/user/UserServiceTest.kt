package com.ddakta.domain.user

import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import org.mockito.kotlin.verify
import org.assertj.core.api.Assertions.assertThat
import org.mockito.Mockito

class UserServiceTest {

    private val repo: UserRepository = Mockito.mock(UserRepository::class.java)
    private val service = UserService(repo)

    @Test
    fun `register 호출 시 repository save 를 사용해야 한다`() {
        val user = User.create("u1","이름","u1@x.com", UserType.PASSENGER)
        whenever(repo.save(user)).thenReturn(user)

        val saved = service.register(user)
        assertThat(saved).isEqualTo(user)
        verify(repo).save(user)
    }

    @Test
    fun `findByUsername 으로 조회 결과를 그대로 반환해야 한다`() {
        val user = User.create("u2","이름2","u2@x.com", UserType.DRIVER)
        whenever(repo.findByUsername("u2")).thenReturn(user)

        val found = service.findByUsername("u2")
        assertThat(found).isEqualTo(user)
    }
}
