package com.ddakta.auth.repository

import com.ddakta.auth.domain.model.TokenSession
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface TokenSessionRepository : CrudRepository<TokenSession, String> {
    fun deleteAllByUserId(userId: Long)
}
