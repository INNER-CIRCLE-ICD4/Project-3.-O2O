package com.ddakta.auth.entity

enum class UserRole(val type: String) {
    PENDING_REGISTRATION("ROLE_PENDING"),
    PASSENGER("ROLE_PASSENGER"),
    DRIVER("ROLE_DRIVER")
}
