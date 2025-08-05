package com.ddakta.matching.repository

import com.ddakta.matching.domain.entity.Ride
import com.ddakta.matching.domain.enum.RideStatus
import com.ddakta.matching.domain.repository.RideRepository
import com.ddakta.matching.domain.vo.Fare
import com.ddakta.matching.domain.vo.Location
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.springframework.context.annotation.ComponentScan
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*

@DataJpaTest(
    excludeAutoConfiguration = [
        org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration::class,
        org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration::class,
        org.springframework.cloud.openfeign.FeignAutoConfiguration::class
    ]
)
@ComponentScan(basePackages = ["com.ddakta.matching.domain.repository"])
@ActiveProfiles("test")
@TestPropertySource(
    properties = [
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "spring.sql.init.mode=never"
    ]
)
@DisplayName("Simple Repository Tests")
class SimpleRepositoryTest {

    @Autowired
    private lateinit var entityManager: TestEntityManager

    @Autowired
    private lateinit var rideRepository: RideRepository

    @Test
    @DisplayName("Should save and find ride by ID")
    fun shouldSaveAndFindRideById() {
        // Given
        val ride = createTestRide()
        
        // When
        val savedRide = rideRepository.save(ride)
        entityManager.flush()
        
        // Then
        assertThat(savedRide.id).isNotNull()
        
        val foundRide = rideRepository.findById(savedRide.id!!)
        assertThat(foundRide).isPresent()
        assertThat(foundRide.get().passengerId).isEqualTo(ride.passengerId)
        assertThat(foundRide.get().status).isEqualTo(RideStatus.REQUESTED)
    }

    private fun createTestRide(): Ride {
        val ride = Ride(
            passengerId = UUID.randomUUID(),
            pickupLocation = Location(37.5665, 126.9780, "Test Pickup", "8830e1d8dffffff"),
            dropoffLocation = Location(37.5759, 126.9768, "Test Dropoff", "8830e1d89ffffff"),
            fare = Fare(
                baseFare = BigDecimal("3000"),
                surgeMultiplier = BigDecimal("1.0"),
                totalFare = BigDecimal("6000")
            )
        )
        return ride
    }
}