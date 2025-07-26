package com.ddakta.auth.domain.entity

import com.ddakta.auth.domain.enum.AuthProvider
import com.ddakta.domain.base.BaseEntity
import com.ddakta.domain.user.UserRole
import com.ddakta.domain.user.UserStatus
import jakarta.persistence.*

@Entity
@Table(name = "users")
class User(
    @Column(nullable = false, unique = true)
    val email: String,
    
    @Column(nullable = false)
    val name: String,
    
    @Column(name = "provider_id", nullable = false)
    val providerId: String,
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val provider: AuthProvider,
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val role: UserRole,
    
    @Column(name = "profile_image_url")
    val profileImageUrl: String? = null,
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val status: UserStatus = UserStatus.ACTIVE,
    
    @Column(name = "driver_license_number")
    val driverLicenseNumber: String? = null,
    
    @Column(name = "driver_verified")
    val driverVerified: Boolean = false
) : BaseEntity() {
    fun isDriver(): Boolean = role == UserRole.DRIVER
    
    fun isVerifiedDriver(): Boolean = isDriver() && driverVerified
    
    fun isActive(): Boolean = status == UserStatus.ACTIVE
    
    fun toUserInfo(): com.ddakta.auth.dto.UserInfo {
        return com.ddakta.auth.dto.UserInfo.from(this)
    }
}