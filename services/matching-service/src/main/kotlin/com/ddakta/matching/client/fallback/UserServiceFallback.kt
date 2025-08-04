package com.ddakta.matching.client.fallback

import com.ddakta.matching.client.UserServiceClient
import mu.KotlinLogging
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.TimeUnit

@Component
class UserServiceFallback(
    private val redisTemplate: RedisTemplate<String, String>? = null
) : UserServiceClient {
    
    private val logger = KotlinLogging.logger {}
    
    companion object {
        const val USER_CACHE_KEY = "user:cache:"
        const val DRIVER_RATING_KEY = "driver:rating:"
        const val CACHE_TTL_HOURS = 24L
    }
    
    override fun getPassengerInfo(passengerId: UUID): UserServiceClient.PassengerInfo {
        logger.warn { "User service unavailable, returning default passenger info for $passengerId" }
        
        // Try to get from cache
        val cachedData = getCachedUserData(passengerId, "passenger")
        
        return UserServiceClient.PassengerInfo(
            id = passengerId,
            name = cachedData?.get("name") ?: "Passenger",
            phone = cachedData?.get("phone") ?: "+82-10-0000-0000",
            email = cachedData?.get("email"),
            rating = cachedData?.get("rating")?.toDoubleOrNull() ?: 4.5,
            totalRides = cachedData?.get("totalRides")?.toIntOrNull() ?: 0,
            createdAt = LocalDateTime.now(),
            isActive = true,
            preferredLanguage = cachedData?.get("language") ?: "ko"
        )
    }
    
    override fun getDriverInfo(driverId: UUID): UserServiceClient.DriverInfo {
        logger.warn { "User service unavailable, returning default driver info for $driverId" }
        
        // Try to get from cache
        val cachedData = getCachedUserData(driverId, "driver")
        val cachedRating = getCachedDriverRating(driverId)
        
        return UserServiceClient.DriverInfo(
            id = driverId,
            name = cachedData?.get("name") ?: "Driver",
            phone = cachedData?.get("phone") ?: "+82-10-0000-0000",
            email = cachedData?.get("email"),
            rating = cachedRating ?: 4.5,
            acceptanceRate = 0.8, // Default acceptance rate
            completionRate = 0.95, // Default completion rate
            totalRides = cachedData?.get("totalRides")?.toIntOrNull() ?: 0,
            vehicleInfo = UserServiceClient.VehicleInfo(
                make = "Unknown",
                model = "Unknown",
                year = 2020,
                color = "Unknown",
                plateNumber = "00가0000",
                vehicleType = "STANDARD"
            ),
            licenseNumber = "00-00-000000-00",
            createdAt = LocalDateTime.now(),
            isActive = true,
            isAvailable = false
        )
    }
    
    override fun getDriverRating(driverId: UUID): UserServiceClient.DriverRating {
        logger.warn { "User service unavailable, returning cached driver rating for $driverId" }
        
        val rating = getCachedDriverRating(driverId) ?: 4.5
        
        return UserServiceClient.DriverRating(
            driverId = driverId,
            averageRating = rating,
            totalRatings = 0,
            ratingBreakdown = mapOf(5 to 0, 4 to 0, 3 to 0, 2 to 0, 1 to 0),
            lastUpdated = LocalDateTime.now()
        )
    }
    
    override fun getDefaultPaymentMethod(passengerId: UUID): UserServiceClient.PaymentMethodInfo? {
        logger.warn { "User service unavailable, returning cached payment method for $passengerId" }
        
        redisTemplate?.let {
            val key = "user:payment:default:$passengerId"
            val paymentMethodId = it.opsForValue().get(key)
            
            if (paymentMethodId != null) {
                return UserServiceClient.PaymentMethodInfo(
                    id = paymentMethodId,
                    userId = passengerId,
                    type = "CARD",
                    isDefault = true,
                    displayName = "****"
                )
            }
        }
        
        // Return cash as default fallback
        return UserServiceClient.PaymentMethodInfo(
            id = "CASH",
            userId = passengerId,
            type = "CASH",
            isDefault = true,
            displayName = "현금"
        )
    }
    
    override fun getUserProfile(userId: UUID, userType: String): UserServiceClient.UserProfile {
        logger.warn { "User service unavailable, returning default profile for $userId" }
        
        val cachedData = getCachedUserData(userId, userType)
        
        return UserServiceClient.UserProfile(
            id = userId,
            userType = userType,
            name = cachedData?.get("name") ?: userType.capitalize(),
            phone = cachedData?.get("phone") ?: "+82-10-0000-0000",
            email = cachedData?.get("email"),
            profileImageUrl = null,
            preferredLanguage = cachedData?.get("language") ?: "ko",
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
    }
    
    override fun getDriverInfoBatch(driverIds: List<UUID>): Map<UUID, UserServiceClient.DriverInfo> {
        logger.warn { "User service unavailable, returning default info for ${driverIds.size} drivers" }
        
        return driverIds.associateWith { driverId ->
            getDriverInfo(driverId)
        }
    }
    
    private fun getCachedUserData(userId: UUID, userType: String): Map<String, String>? {
        redisTemplate?.let {
            val key = "$USER_CACHE_KEY$userType:$userId"
            val data = it.opsForHash<String, String>().entries(key)
            if (data.isNotEmpty()) {
                return data
            }
        }
        return null
    }
    
    private fun getCachedDriverRating(driverId: UUID): Double? {
        redisTemplate?.let {
            val key = "$DRIVER_RATING_KEY$driverId"
            val ratingData = it.opsForValue().get(key)
            if (ratingData != null) {
                val parts = ratingData.split(":")
                if (parts.isNotEmpty()) {
                    return parts[0].toDoubleOrNull()
                }
            }
        }
        return null
    }
    
    private fun String.capitalize(): String {
        return this.lowercase().replaceFirstChar { it.uppercase() }
    }
}