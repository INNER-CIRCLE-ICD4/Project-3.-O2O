package com.ddakta.common.user.event

import com.ddakta.common.user.dto.Location
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import java.time.Instant
import java.util.UUID

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "eventType")
@JsonSubTypes(
    JsonSubTypes.Type(value = UserCreatedEvent::class, name = "USER_CREATED"),
    JsonSubTypes.Type(value = UserUpdatedEvent::class, name = "USER_UPDATED"),
    JsonSubTypes.Type(value = UserDeletedEvent::class, name = "USER_DELETED"),
    JsonSubTypes.Type(value = RiderStatusChangedEvent::class, name = "RIDER_STATUS_CHANGED"),
    JsonSubTypes.Type(value = RiderLocationUpdatedEvent::class, name = "RIDER_LOCATION_UPDATED")
)
sealed class UserEvent {
    abstract val eventId: UUID
    abstract val timestamp: Instant
    abstract val aggregateId: UUID
}

data class UserCreatedEvent(
    override val eventId: UUID = UUID.randomUUID(),
    override val timestamp: Instant = Instant.now(),
    override val aggregateId: UUID,
    val userType: String // "RIDER" or "PASSENGER"
) : UserEvent()

data class UserUpdatedEvent(
    override val eventId: UUID = UUID.randomUUID(),
    override val timestamp: Instant = Instant.now(),
    override val aggregateId: UUID,
    val updatedFields: Set<String>
) : UserEvent()

data class UserDeletedEvent(
    override val eventId: UUID = UUID.randomUUID(),
    override val timestamp: Instant = Instant.now(),
    override val aggregateId: UUID
) : UserEvent()

data class RiderStatusChangedEvent(
    override val eventId: UUID = UUID.randomUUID(),
    override val timestamp: Instant = Instant.now(),
    override val aggregateId: UUID,
    val isOnline: Boolean,
    val acceptingRides: Boolean
) : UserEvent()

data class RiderLocationUpdatedEvent(
    override val eventId: UUID = UUID.randomUUID(),
    override val timestamp: Instant = Instant.now(),
    override val aggregateId: UUID,
    val location: Location
) : UserEvent()