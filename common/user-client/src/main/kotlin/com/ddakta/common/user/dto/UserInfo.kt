package com.ddakta.common.user.dto

import com.ddakta.domain.user.UserStatus
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import java.time.Instant
import java.util.UUID

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "userType")
@JsonSubTypes(
    JsonSubTypes.Type(value = RiderInfo::class, name = "RIDER"),
    JsonSubTypes.Type(value = PassengerInfo::class, name = "PASSENGER")
)
sealed class UserInfo {
    abstract val id: UUID
    abstract val name: String
    abstract val phoneNumber: String
    abstract val email: String
    abstract val profileImageUrl: String?
    abstract val status: UserStatus
    abstract val lastActiveAt: Instant
    
    fun getDisplayName(): String = when (this) {
        is RiderInfo -> "$name 라이더님"
        is PassengerInfo -> "$name 승객님"
    }
}