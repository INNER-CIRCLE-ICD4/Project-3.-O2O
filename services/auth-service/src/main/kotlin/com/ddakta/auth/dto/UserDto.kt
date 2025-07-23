package com.ddakta.auth.dto

data class UserDto(
    val username: String,
    val name: String,
    val role: String,
    val email: String,
)
