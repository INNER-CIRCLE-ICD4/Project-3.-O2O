package com.ddakta.domain.user

import com.ddakta.domain.DomainTestConfig
import org.junit.jupiter.api.Test
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
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
        val user = User.create("me_123", "홍길동", "hong@x.com", UserType.PASSENGER)
        userRepository.save(user)

        val found = userRepository.findByUsername("me_123")
        assertThat(found).isNotNull()
        assertThat(found?.email).isEqualTo("hong@x.com")
    }
}
