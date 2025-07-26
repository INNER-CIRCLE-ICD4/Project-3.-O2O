package com.ddakta.domain.user

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserService(
    private val userRepository: UserRepository
) {
    fun findByUsername(username: String): User? =
        userRepository.findByUsername(username)

    @Transactional
    fun register(user: User): User =
        userRepository.save(user)
}
