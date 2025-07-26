package com.ddakta.common.user.dto

import java.util.UUID

data class FavoriteLocation(
    val id: UUID,
    val name: String,
    val address: Address,
    val location: Location
)