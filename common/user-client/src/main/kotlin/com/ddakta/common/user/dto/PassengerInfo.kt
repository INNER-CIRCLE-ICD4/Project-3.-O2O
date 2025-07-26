package com.ddakta.common.user.dto

import com.ddakta.domain.user.UserStatus
import java.time.Instant
import java.util.UUID

data class PassengerInfo(
    override val id: UUID,
    override val name: String,
    override val phoneNumber: String,
    override val email: String,
    override val profileImageUrl: String?,
    override val status: UserStatus,
    override val lastActiveAt: Instant,
    val preferredPaymentMethodId: UUID?,
    val homeAddress: Address?,
    val workAddress: Address?,
    val favoriteLocations: List<FavoriteLocation> = emptyList()
) : UserInfo() {
    
    fun canRequestRide(): Boolean = status == UserStatus.ACTIVE
}