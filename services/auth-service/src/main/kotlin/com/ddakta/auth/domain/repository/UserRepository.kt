package com.ddakta.auth.domain.repository

import com.ddakta.auth.domain.enum.AuthProvider
import com.ddakta.auth.domain.entity.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface UserRepository : JpaRepository<User, UUID> {
    fun findByProviderAndProviderId(provider: AuthProvider, providerId: String): Optional<User>
}
