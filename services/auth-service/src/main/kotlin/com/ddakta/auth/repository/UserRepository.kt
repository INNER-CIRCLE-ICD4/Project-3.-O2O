package com.ddakta.auth.repository

import com.ddakta.auth.entity.User
import org.springframework.data.jpa.repository.JpaRepository

interface UserRepository: JpaRepository<User, Long> {
    fun findByNaverId(username: String): User?
    fun findByGoogleId(username: String): User?
}
