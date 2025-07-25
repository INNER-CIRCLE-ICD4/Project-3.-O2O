package com.ddakta.auth.domain.repository

import com.ddakta.auth.domain.model.TokenSession
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface TokenSessionRepository : CrudRepository<TokenSession, String> {
    fun findByAccessToken(accessToken: String): Optional<TokenSession>
    
    fun findByRefreshToken(refreshToken: String): Optional<TokenSession>
    
    fun findByUserId(userId: UUID): List<TokenSession>
}