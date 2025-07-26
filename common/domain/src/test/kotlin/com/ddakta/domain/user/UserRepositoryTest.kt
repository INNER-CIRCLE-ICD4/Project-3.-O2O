package com.ddakta.domain.user

import com.ddakta.domain.DomainTestConfig
import org.junit.jupiter.api.Test
import org.assertj.core.api.Assertions.assertThat
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest(classes = [DomainTestConfig::class])
@AutoConfigureTestDatabase
class UserRepositoryTest @Autowired constructor(
    val userRepository: UserRepository
) {
    @Test
    fun `username 으로 저장 후 조회가 가능해야 한다`() {
        // fixture 이용
        val savedUser = userRepository.save(UserFixture.PASSENGER)

        val found = userRepository.findByUsername(UserFixture.PASSENGER.username)
        assertThat(found).isNotNull
        assertThat(found?.email).isEqualTo(UserFixture.PASSENGER.email)
    }
}
