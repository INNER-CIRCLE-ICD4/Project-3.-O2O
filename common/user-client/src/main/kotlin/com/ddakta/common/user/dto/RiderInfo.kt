package com.ddakta.common.user.dto

import com.ddakta.domain.user.UserStatus
import java.time.Instant
import java.util.UUID

data class RiderInfo(
    override val id: UUID,
    override val name: String,
    override val phoneNumber: String,
    override val email: String,
    override val profileImageUrl: String?,
    override val status: UserStatus,
    override val lastActiveAt: Instant,
    val rating: Double,
    val completedRides: Int,
    val vehicle: VehicleInfo,
    val currentLocation: Location?,
    val isOnline: Boolean,
    val acceptingRides: Boolean
) : UserInfo() {
    
    fun isAvailable(): Boolean = isOnline && acceptingRides && status == UserStatus.ACTIVE
}