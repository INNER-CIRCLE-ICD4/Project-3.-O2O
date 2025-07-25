package com.ddakta.auth.security

import java.util.*

data class AuthenticationPrincipal(
    val userId: UUID,
    val email: String,
    val role: String
)