package com.ddakta.domain.user

import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.kotlin.verify
import org.assertj.core.api.Assertions.assertThat

class UserServiceTest {

    private val repo: UserRepository = mock()
    private val service = UserService(repo)

    @Test
    fun `register 호출 시 repository save 를 사용해야 한다`() {
        whenever(repo.save(UserFixture.PASSENGER)).thenReturn(UserFixture.PASSENGER)

        val saved = service.register(UserFixture.PASSENGER)
        assertThat(saved).isEqualTo(UserFixture.PASSENGER)
        verify(repo).save(UserFixture.PASSENGER)
    }

    @Test
    fun `findByUsername 으로 조회 결과를 그대로 반환해야 한다`() {
        whenever(repo.findByUsername(UserFixture.DRIVER.username)).thenReturn(UserFixture.DRIVER)

        val found = service.findByUsername(UserFixture.DRIVER.username)
        assertThat(found).isEqualTo(UserFixture.DRIVER)
    }
}
