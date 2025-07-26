package com.ddakta.auth.repository

import com.ddakta.auth.domain.model.OAuthRequest
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface OAuthRequestRepository : CrudRepository<OAuthRequest, String>
