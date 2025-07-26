package com.ddakta.auth.entity

data class UserDto(

    val email: String,
    val name: String,
    val role: String,
    val username: String,
) {
    companion object {
        fun fromUser(user: User): UserDto {
            return UserDto(
                email = user.email,
                name = user.name,
                role = user.role.type,
                username = user.email
            )
        }
    }
}

