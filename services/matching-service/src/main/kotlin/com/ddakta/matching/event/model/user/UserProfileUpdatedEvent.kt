package com.ddakta.matching.event.model.user

import java.time.LocalDateTime
import java.util.*

data class UserProfileUpdatedEvent(
    val userId: UUID,
    val userType: String, // PASSENGER, DRIVER
    val updatedFields: Map<String, Any>,
    val updatedAt: LocalDateTime
)