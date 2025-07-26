package com.ddakta.auth.dto

data class LoginUserRequest(
    val userId: String,
    val name: String,
    val email: String,
    val oAuthType: String,
) {
}
