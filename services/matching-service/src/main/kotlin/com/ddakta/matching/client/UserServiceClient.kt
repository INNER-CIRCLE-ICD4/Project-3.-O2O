package com.ddakta.matching.client

import com.ddakta.matching.client.fallback.UserServiceFallback
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*

@FeignClient(
    name = "user-service",
    url = "\${ddakta.user-service.url:http://user-service:8081}",
    fallback = UserServiceFallback::class
)
interface UserServiceClient {
    
    @GetMapping("/internal/passengers/{passengerId}")
    fun getPassengerInfo(@PathVariable passengerId: UUID): PassengerInfo
    
    @GetMapping("/internal/drivers/{driverId}")
    fun getDriverInfo(@PathVariable driverId: UUID): DriverInfo
    
    @GetMapping("/internal/drivers/{driverId}/rating")
    fun getDriverRating(@PathVariable driverId: UUID): DriverRating
    
    @GetMapping("/internal/passengers/{passengerId}/payment-method")
    fun getDefaultPaymentMethod(@PathVariable passengerId: UUID): PaymentMethodInfo?
    
    @GetMapping("/internal/users/{userId}/profile")
    fun getUserProfile(
        @PathVariable userId: UUID,
        @RequestParam userType: String
    ): UserProfile
    
    @GetMapping("/internal/drivers/batch")
    fun getDriverInfoBatch(
        @RequestParam driverIds: List<UUID>
    ): Map<UUID, DriverInfo>
    
    data class PassengerInfo(
        val id: UUID,
        val name: String,
        val phone: String,
        val email: String?,
        val rating: Double,
        val totalRides: Int,
        val createdAt: LocalDateTime,
        val isActive: Boolean = true,
        val preferredLanguage: String = "ko"
    )
    
    data class DriverInfo(
        val id: UUID,
        val name: String,
        val phone: String,
        val email: String?,
        val rating: Double,
        val acceptanceRate: Double,
        val completionRate: Double,
        val totalRides: Int,
        val vehicleInfo: VehicleInfo,
        val licenseNumber: String,
        val createdAt: LocalDateTime,
        val isActive: Boolean = true,
        val isAvailable: Boolean = false
    )
    
    data class VehicleInfo(
        val make: String,
        val model: String,
        val year: Int,
        val color: String,
        val plateNumber: String,
        val vehicleType: String
    )
    
    data class DriverRating(
        val driverId: UUID,
        val averageRating: Double,
        val totalRatings: Int,
        val ratingBreakdown: Map<Int, Int>, // rating value to count
        val lastUpdated: LocalDateTime
    )
    
    data class PaymentMethodInfo(
        val id: String,
        val userId: UUID,
        val type: String, // CARD, CASH, WALLET
        val isDefault: Boolean,
        val displayName: String,
        val metadata: Map<String, String> = emptyMap()
    )
    
    data class UserProfile(
        val id: UUID,
        val userType: String,
        val name: String,
        val phone: String,
        val email: String?,
        val profileImageUrl: String?,
        val preferredLanguage: String,
        val createdAt: LocalDateTime,
        val updatedAt: LocalDateTime
    )
}