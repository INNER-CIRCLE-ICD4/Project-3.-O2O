package com.ddakta.matching.event.consumer

import com.ddakta.matching.event.model.driver.DriverRatingUpdatedEvent
import com.ddakta.matching.event.model.user.UserPaymentMethodUpdatedEvent
import com.ddakta.matching.event.model.user.UserProfileUpdatedEvent
import mu.KotlinLogging
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class UserEventConsumer(
    private val redisTemplate: RedisTemplate<String, String>
) {
    
    private val logger = KotlinLogging.logger {}
    
    companion object {
        const val USER_PROFILE_KEY = "user:profile:"
        const val DRIVER_RATING_KEY = "driver:rating:"
        const val CACHE_TTL_HOURS = 24L
    }
    
    @KafkaListener(
        topics = ["user-profile-updated"],
        groupId = "matching-service-user",
        containerFactory = "kafkaListenerContainerFactory"
    )
    fun handleUserProfileUpdated(
        event: UserProfileUpdatedEvent,
        acknowledgment: Acknowledgment
    ) {
        logger.info { 
            "User profile updated for ${event.userType} ${event.userId}: ${event.updatedFields.keys}"
        }
        
        try {
            // Invalidate cached user data
            val cacheKey = "$USER_PROFILE_KEY${event.userId}"
            redisTemplate.delete(cacheKey)
            
            // Handle specific field updates
            if (event.updatedFields.containsKey("phone")) {
                logger.info { "Phone number updated for user ${event.userId}" }
                // Could trigger verification process
            }
            
            if (event.userType == "DRIVER" && event.updatedFields.containsKey("vehicleInfo")) {
                logger.info { "Vehicle info updated for driver ${event.userId}" }
                // Could trigger vehicle verification
            }
            
            acknowledgment.acknowledge()
            
        } catch (e: Exception) {
            logger.error(e) { "Error processing user profile update for ${event.userId}" }
            throw e
        }
    }
    
    @KafkaListener(
        topics = ["user-payment-method-updated"],
        groupId = "matching-service-user",
        containerFactory = "kafkaListenerContainerFactory"
    )
    fun handlePaymentMethodUpdated(
        event: UserPaymentMethodUpdatedEvent,
        acknowledgment: Acknowledgment
    ) {
        logger.info { 
            "Payment method ${event.action} for user ${event.userId}: ${event.paymentMethodId}"
        }
        
        try {
            // Update cached payment method information
            when (event.action) {
                "SET_DEFAULT" -> {
                    val key = "user:payment:default:${event.userId}"
                    redisTemplate.opsForValue().set(
                        key,
                        event.paymentMethodId,
                        CACHE_TTL_HOURS,
                        TimeUnit.HOURS
                    )
                }
                "REMOVED" -> {
                    // Clear any cached payment method data
                    val key = "user:payment:${event.userId}:${event.paymentMethodId}"
                    redisTemplate.delete(key)
                }
            }
            
            acknowledgment.acknowledge()
            
        } catch (e: Exception) {
            logger.error(e) { "Error processing payment method update for ${event.userId}" }
            throw e
        }
    }
    
    @KafkaListener(
        topics = ["driver-rating-updated"],
        groupId = "matching-service-user",
        containerFactory = "kafkaListenerContainerFactory"
    )
    fun handleDriverRatingUpdated(
        event: DriverRatingUpdatedEvent,
        acknowledgment: Acknowledgment
    ) {
        logger.info { 
            "Driver ${event.driverId} rating updated: ${event.previousAverage} -> ${event.newAverage}"
        }
        
        try {
            // Cache updated driver rating
            val key = "$DRIVER_RATING_KEY${event.driverId}"
            val ratingData = "${event.newAverage}:${event.totalRatings}"
            
            redisTemplate.opsForValue().set(
                key,
                ratingData,
                CACHE_TTL_HOURS,
                TimeUnit.HOURS
            )
            
            // Log significant rating changes
            val ratingDiff = event.newAverage - event.previousAverage
            if (kotlin.math.abs(ratingDiff) >= 0.5) {
                logger.warn { 
                    "Significant rating change for driver ${event.driverId}: " +
                    "${if (ratingDiff > 0) "+" else ""}$ratingDiff"
                }
            }
            
            acknowledgment.acknowledge()
            
        } catch (e: Exception) {
            logger.error(e) { "Error processing driver rating update for ${event.driverId}" }
            throw e
        }
    }
}