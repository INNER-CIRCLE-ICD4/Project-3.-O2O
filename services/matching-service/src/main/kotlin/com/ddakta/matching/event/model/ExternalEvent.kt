package com.ddakta.matching.event.model

import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*

// Payment Service Events
data class PaymentProcessedEvent(
    val paymentId: UUID,
    val rideId: UUID,
    val passengerId: UUID,
    val amount: BigDecimal,
    val currency: String,
    val paymentMethod: String,
    val processedAt: LocalDateTime
)

data class PaymentFailedEvent(
    val paymentId: UUID,
    val rideId: UUID,
    val passengerId: UUID,
    val reason: String,
    val failedAt: LocalDateTime,
    val retryable: Boolean
)

data class PaymentRefundedEvent(
    val paymentId: UUID,
    val rideId: UUID,
    val passengerId: UUID,
    val amount: BigDecimal,
    val reason: String,
    val refundedAt: LocalDateTime
)

// Driver Service Events
data class DriverLocationUpdatedEvent(
    val driverId: UUID,
    val latitude: Double,
    val longitude: Double,
    val h3Index: String,
    val heading: Int?,
    val speed: Double?,
    val accuracy: Double?,
    val timestamp: LocalDateTime,
    val isOnline: Boolean
)

data class DriverStatusChangedEvent(
    val driverId: UUID,
    val previousStatus: String,
    val newStatus: String,
    val h3Index: String?,
    val changedAt: LocalDateTime
)

data class DriverAvailabilityChangedEvent(
    val driverId: UUID,
    val isAvailable: Boolean,
    val h3Index: String?,
    val reason: String?,
    val changedAt: LocalDateTime
)

// Notification Service Events
data class NotificationDeliveredEvent(
    val notificationId: UUID,
    val recipientId: UUID,
    val recipientType: String, // PASSENGER, DRIVER
    val referenceId: UUID?, // rideId or other reference
    val referenceType: String?, // RIDE, DRIVER_CALL, etc
    val deliveredAt: LocalDateTime
)

data class NotificationFailedEvent(
    val notificationId: UUID,
    val recipientId: UUID,
    val recipientType: String,
    val reason: String,
    val failedAt: LocalDateTime,
    val retryCount: Int
)

// User Service Events
data class UserProfileUpdatedEvent(
    val userId: UUID,
    val userType: String, // PASSENGER, DRIVER
    val updatedFields: Map<String, Any>,
    val updatedAt: LocalDateTime
)

data class UserPaymentMethodUpdatedEvent(
    val userId: UUID,
    val paymentMethodId: String,
    val action: String, // ADDED, UPDATED, REMOVED, SET_DEFAULT
    val updatedAt: LocalDateTime
)

data class DriverRatingUpdatedEvent(
    val driverId: UUID,
    val rideId: UUID,
    val rating: Int,
    val previousAverage: Double,
    val newAverage: Double,
    val totalRatings: Int,
    val updatedAt: LocalDateTime
)

// Location Service Events
data class TrafficConditionsUpdatedEvent(
    val h3Index: String,
    val congestionLevel: String, // LOW, MEDIUM, HIGH, SEVERE
    val averageSpeed: Double,
    val incidents: List<TrafficIncident>?,
    val updatedAt: LocalDateTime
)

data class TrafficIncident(
    val type: String, // ACCIDENT, CONSTRUCTION, EVENT
    val severity: String, // MINOR, MAJOR, CRITICAL
    val location: LocationInfo,
    val estimatedDelay: Int? // seconds
)

data class LocationInfo(
    val latitude: Double,
    val longitude: Double,
    val h3Index: String
)

// Surge Pricing Events (from Pricing Service)
data class SurgePricingUpdatedEvent(
    val h3Index: String,
    val previousMultiplier: Double,
    val newMultiplier: Double,
    val reason: String, // DEMAND_INCREASE, SUPPLY_DECREASE, SCHEDULED, MANUAL
    val effectiveFrom: LocalDateTime,
    val effectiveTo: LocalDateTime?
)