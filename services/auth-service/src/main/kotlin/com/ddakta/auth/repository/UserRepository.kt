package com.ddakta.auth.repository

import com.ddakta.auth.entity.User
import com.ddakta.auth.domain.enums.AuthProvider
import org.springframework.data.jpa.repository.JpaRepository

interface UserRepository : JpaRepository<User, Long> {
    fun findByProviderAndProviderId(provider: AuthProvider, providerId: String): User?
}
