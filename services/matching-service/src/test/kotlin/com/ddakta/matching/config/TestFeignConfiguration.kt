package com.ddakta.matching.config

import com.ddakta.matching.client.LocationServiceClient
import com.ddakta.matching.client.UserServiceClient
import com.ddakta.matching.dto.internal.AvailableDriver
import com.ddakta.matching.dto.internal.LocationInfo
import com.ddakta.matching.domain.vo.Location
import org.mockito.Mockito
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.cloud.openfeign.EnableFeignClients
import org.springframework.cloud.openfeign.FeignAutoConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*

/**
 * Test configuration that provides mocked FeignClients for integration tests.
 * This prevents duplicate bean definition errors and allows tests to run without external services.
 */
@TestConfiguration
@Profile("test", "integration")
class TestFeignConfiguration {

    @Bean
    @Primary
    fun mockLocationServiceClient(): LocationServiceClient {
        val mockClient = Mockito.mock(LocationServiceClient::class.java)
        
        // Default behavior for common methods
        Mockito.`when`(mockClient.findNearbyDrivers(
            Mockito.anyString(),
            Mockito.anyDouble(),
            Mockito.anyInt()
        )).thenReturn(createMockAvailableDrivers())
        
        Mockito.`when`(mockClient.getAvailableDrivers(
            Mockito.anyList()
        )).thenReturn(createMockAvailableDrivers())
        
        Mockito.`when`(mockClient.getAvailableDriverCount(
            Mockito.anyString()
        )).thenReturn(3)
        
        Mockito.`when`(mockClient.getDriverLocation(
            Mockito.any(UUID::class.java)
        )).thenReturn(createMockLocationInfo())
        
        Mockito.`when`(mockClient.getNeighboringH3Indexes(
            Mockito.anyString(),
            Mockito.anyInt()
        )).thenReturn(listOf("8830e1d8dffffff", "8830e1d89ffffff", "8830e1d8bffffff"))
        
        Mockito.`when`(mockClient.calculateDistance(
            Mockito.anyDouble(),
            Mockito.anyDouble(),
            Mockito.anyDouble(),
            Mockito.anyDouble()
        )).thenReturn(LocationServiceClient.DistanceInfo(
            distanceMeters = 1500,
            estimatedDurationSeconds = 300,
            trafficCondition = "NORMAL"
        ))
        
        Mockito.`when`(mockClient.getTripSummary(
            Mockito.any(UUID::class.java)
        )).thenReturn(LocationServiceClient.TripSummary(
            rideId = UUID.randomUUID(),
            distanceMeters = 5000,
            durationSeconds = 900,
            route = emptyList()
        ))
        
        return mockClient
    }

    @Bean
    @Primary
    fun mockUserServiceClient(): UserServiceClient {
        val mockClient = Mockito.mock(UserServiceClient::class.java)
        
        // Default behavior for common methods
        Mockito.`when`(mockClient.getPassengerInfo(
            Mockito.any(UUID::class.java)
        )).thenReturn(createMockPassengerInfo())
        
        Mockito.`when`(mockClient.getDriverInfo(
            Mockito.any(UUID::class.java)
        )).thenReturn(createMockDriverInfo())
        
        Mockito.`when`(mockClient.getDriverRating(
            Mockito.any(UUID::class.java)
        )).thenReturn(createMockDriverRating())
        
        Mockito.`when`(mockClient.getDefaultPaymentMethod(
            Mockito.any(UUID::class.java)
        )).thenReturn(createMockPaymentMethod())
        
        Mockito.`when`(mockClient.getUserProfile(
            Mockito.any(UUID::class.java),
            Mockito.anyString()
        )).thenReturn(createMockUserProfile())
        
        Mockito.`when`(mockClient.getDriverInfoBatch(
            Mockito.anyList()
        )).thenReturn(emptyMap())
        
        return mockClient
    }

    private fun createMockAvailableDrivers(): List<AvailableDriver> {
        return listOf(
            AvailableDriver(
                driverId = UUID.randomUUID(),
                currentLocation = Location(
                    latitude = 37.5665,
                    longitude = 126.9780,
                    address = "Seoul Station",
                    h3Index = "8830e1d8dffffff"
                ),
                rating = 4.5,
                acceptanceRate = 0.85,
                isAvailable = true,
                vehicleType = "STANDARD",
                completedTrips = 100,
                estimatedArrivalMinutes = 3,
                estimatedFare = BigDecimal("15000"),
                distanceToPickupMeters = 500.0,
                completionRate = 0.95
            ),
            AvailableDriver(
                driverId = UUID.randomUUID(),
                currentLocation = Location(
                    latitude = 37.5670,
                    longitude = 126.9785,
                    address = "Near Seoul Station",
                    h3Index = "8830e1d8dffffff"
                ),
                rating = 4.8,
                acceptanceRate = 0.90,
                isAvailable = true,
                vehicleType = "PREMIUM",
                completedTrips = 200,
                estimatedArrivalMinutes = 4,
                estimatedFare = BigDecimal("20000"),
                distanceToPickupMeters = 800.0,
                completionRate = 0.98
            )
        )
    }

    private fun createMockLocationInfo(): LocationInfo {
        return LocationInfo(
            latitude = 37.5665,
            longitude = 126.9780,
            h3Index = "8830e1d8dffffff",
            heading = 0.0,
            speed = 0.0,
            accuracy = 10.0,
            timestamp = LocalDateTime.now()
        )
    }

    private fun createMockPassengerInfo(): UserServiceClient.PassengerInfo {
        return UserServiceClient.PassengerInfo(
            id = UUID.randomUUID(),
            name = "Test Passenger",
            phone = "+821012345678",
            email = "passenger@test.com",
            rating = 4.7,
            totalRides = 50,
            createdAt = LocalDateTime.now().minusMonths(6),
            isActive = true,
            preferredLanguage = "ko"
        )
    }

    private fun createMockDriverInfo(): UserServiceClient.DriverInfo {
        return UserServiceClient.DriverInfo(
            id = UUID.randomUUID(),
            name = "Test Driver",
            phone = "+821098765432",
            email = "driver@test.com",
            rating = 4.8,
            acceptanceRate = 0.85,
            completionRate = 0.95,
            totalRides = 150,
            vehicleInfo = UserServiceClient.VehicleInfo(
                make = "Hyundai",
                model = "Sonata",
                year = 2022,
                color = "White",
                plateNumber = "12가3456",
                vehicleType = "STANDARD"
            ),
            licenseNumber = "12-34-567890-12",
            createdAt = LocalDateTime.now().minusYears(1),
            isActive = true,
            isAvailable = true
        )
    }

    private fun createMockDriverRating(): UserServiceClient.DriverRating {
        return UserServiceClient.DriverRating(
            driverId = UUID.randomUUID(),
            averageRating = 4.8,
            totalRatings = 150,
            ratingBreakdown = mapOf(
                5 to 120,
                4 to 25,
                3 to 3,
                2 to 1,
                1 to 1
            ),
            lastUpdated = LocalDateTime.now()
        )
    }

    private fun createMockPaymentMethod(): UserServiceClient.PaymentMethodInfo {
        return UserServiceClient.PaymentMethodInfo(
            id = "pm_test_123",
            userId = UUID.randomUUID(),
            type = "CARD",
            isDefault = true,
            displayName = "****1234",
            metadata = mapOf("brand" to "Visa", "last4" to "1234")
        )
    }

    private fun createMockUserProfile(): UserServiceClient.UserProfile {
        return UserServiceClient.UserProfile(
            id = UUID.randomUUID(),
            userType = "PASSENGER",
            name = "Test User",
            phone = "+821012345678",
            email = "test@example.com",
            profileImageUrl = null,
            preferredLanguage = "ko",
            createdAt = LocalDateTime.now().minusMonths(6),
            updatedAt = LocalDateTime.now()
        )
    }
}