package com.ddakta.auth.dto

import com.ddakta.auth.domain.entity.User
import com.ddakta.domain.user.UserRole
import java.util.*

data class UserInfo(
    val id: UUID,
    val email: String,
    val name: String,
    val role: UserRole,
    val profileImageUrl: String?,
    val isVerifiedDriver: Boolean = false
) {
    companion object {
        fun from(user: User): UserInfo {
            return UserInfo(
                id = user.id,
                email = user.email,
                name = user.name,
                role = user.role,
                profileImageUrl = user.profileImageUrl,
                isVerifiedDriver = user.isVerifiedDriver()
            )
        }
    }
}