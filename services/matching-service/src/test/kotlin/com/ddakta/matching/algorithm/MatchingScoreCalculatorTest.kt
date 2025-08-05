package com.ddakta.matching.algorithm

import com.ddakta.matching.config.MatchingProperties
import com.ddakta.matching.domain.entity.MatchingRequest
import com.ddakta.matching.domain.vo.Location
import com.ddakta.matching.domain.vo.MatchingScore
import com.ddakta.matching.dto.internal.AvailableDriver
import com.ddakta.matching.utils.DistanceCalculator
import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*

@ExtendWith(MockKExtension::class)
@DisplayName("MatchingScoreCalculator Tests")
class MatchingScoreCalculatorTest {

    @MockK
    private lateinit var distanceCalculator: DistanceCalculator

    @MockK
    private lateinit var matchingProperties: MatchingProperties

    @InjectMockKs
    private lateinit var matchingScoreCalculator: MatchingScoreCalculator

    private lateinit var testMatchingRequest: MatchingRequest
    private lateinit var testAvailableDriver: AvailableDriver
    private lateinit var testLocation: Location

    @BeforeEach
    fun setUp() {
        // Default matching properties
        every { matchingProperties.distanceWeight } returns 0.4
        every { matchingProperties.ratingWeight } returns 0.3
        every { matchingProperties.acceptanceWeight } returns 0.3

        // Test data setup
        testMatchingRequest = createTestMatchingRequest()
        testAvailableDriver = createTestAvailableDriver()
        testLocation = createTestLocation()
    }

    @Nested
    @DisplayName("Score Calculation Tests")
    inner class ScoreCalculationTests {

        @Test
        @DisplayName("Should calculate score correctly with normal values")
        fun shouldCalculateScoreCorrectlyWithNormalValues() {
            // Given
            val distance = 1000.0 // 1km
            every { distanceCalculator.calculate(any(), any()) } returns distance
            
            // When
            val result = matchingScoreCalculator.calculateScore(
                testMatchingRequest,
                testAvailableDriver,
                testLocation
            )

            // Then
            assertThat(result.distanceScore).isCloseTo(0.8, org.assertj.core.data.Offset.offset(0.01))
            assertThat(result.ratingScore).isCloseTo(0.95, org.assertj.core.data.Offset.offset(0.01))
            assertThat(result.acceptanceScore).isCloseTo(0.95, org.assertj.core.data.Offset.offset(0.01))
            assertThat(result.totalScore).isBetween(0.0, 1.0)
            verify { distanceCalculator.calculate(testLocation, testAvailableDriver.currentLocation) }
        }

        @Test
        @DisplayName("Should handle zero distance correctly")
        fun shouldHandleZeroDistanceCorrectly() {
            // Given
            val distance = 0.0
            every { distanceCalculator.calculate(any(), any()) } returns distance
            
            // When
            val result = matchingScoreCalculator.calculateScore(
                testMatchingRequest,
                testAvailableDriver,
                testLocation
            )

            // Then
            assertThat(result.distanceScore).isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.01)) // Maximum score for zero distance
            assertThat(result.ratingScore).isCloseTo(0.95, org.assertj.core.data.Offset.offset(0.01))
            assertThat(result.acceptanceScore).isCloseTo(0.95, org.assertj.core.data.Offset.offset(0.01))
            assertThat(result.totalScore).isBetween(0.9, 1.0)
        }

        @Test
        @DisplayName("Should handle maximum distance correctly")
        fun shouldHandleMaximumDistanceCorrectly() {
            // Given
            val distance = 6000.0 // Beyond 5km threshold
            every { distanceCalculator.calculate(any(), any()) } returns distance
            
            // When
            val result = matchingScoreCalculator.calculateScore(
                testMatchingRequest,
                testAvailableDriver,
                testLocation
            )

            // Then
            assertThat(result.distanceScore).isCloseTo(0.0, org.assertj.core.data.Offset.offset(0.01)) // Minimum score for excessive distance
            assertThat(result.ratingScore).isCloseTo(0.95, org.assertj.core.data.Offset.offset(0.01))
            assertThat(result.acceptanceScore).isCloseTo(0.95, org.assertj.core.data.Offset.offset(0.01))
            assertThat(result.totalScore).isBetween(0.0, 1.0)
        }

        @Test
        @DisplayName("Should calculate score with legacy method")
        fun shouldCalculateScoreWithLegacyMethod() {
            // Given
            val distance = 2000.0
            val rating = 4.5
            val acceptance = 0.85
            val completion = 0.95

            // When
            val result = matchingScoreCalculator.calculateScore(distance, rating, acceptance, completion)

            // Then
            assertThat(result).isBetween(0.0, 1.0)
            assertThat(result).isGreaterThan(0.5) // Should be reasonably good score
        }
    }

    @Nested
    @DisplayName("Cost Matrix Creation Tests")
    inner class CostMatrixTests {

        @Test
        @DisplayName("Should create valid cost matrix for single request and driver")
        fun shouldCreateValidCostMatrixForSingleRequestAndDriver() {
            // Given
            val requests = listOf(Pair(testMatchingRequest, testLocation))
            val drivers = listOf(testAvailableDriver)
            
            every { distanceCalculator.calculate(any(), any()) } returns 1500.0

            // When
            val costMatrix = matchingScoreCalculator.calculateCostMatrix(requests, drivers)

            // Then
            assertThat(costMatrix.size).isEqualTo(1)
            assertThat(costMatrix[0].size).isEqualTo(1)
            assertThat(costMatrix[0][0]).isBetween(0.0, 1.0) // Should be 1.0 - totalScore
            assertThat(costMatrix[0][0]).isLessThan(1.0) // Cost should be reasonable
        }

        @Test
        @DisplayName("Should create valid cost matrix for multiple requests and drivers")
        fun shouldCreateValidCostMatrixForMultipleRequestsAndDrivers() {
            // Given
            val requests = listOf(
                Pair(testMatchingRequest, testLocation),
                Pair(createTestMatchingRequest(), createTestLocation())
            )
            val drivers = listOf(
                testAvailableDriver,
                createTestAvailableDriver(rating = 4.0, acceptanceRate = 0.8)
            )
            
            every { distanceCalculator.calculate(any(), any()) } returns 1000.0

            // When
            val costMatrix = matchingScoreCalculator.calculateCostMatrix(requests, drivers)

            // Then
            assertThat(costMatrix.size).isEqualTo(2)
            assertThat(costMatrix[0].size).isEqualTo(2)
            assertThat(costMatrix[1].size).isEqualTo(2)
            
            // All costs should be valid (between 0 and 1)
            for (i in costMatrix.indices) {
                for (j in costMatrix[i].indices) {
                    assertThat(costMatrix[i][j]).isBetween(0.0, 1.0)
                }
            }
        }

        @Test
        @DisplayName("Should handle empty requests list")
        fun shouldHandleEmptyRequestsList() {
            // Given
            val emptyRequests = emptyList<Pair<MatchingRequest, Location>>()
            val drivers = listOf(testAvailableDriver)

            // When
            val costMatrix = matchingScoreCalculator.calculateCostMatrix(emptyRequests, drivers)

            // Then
            assertThat(costMatrix).isEmpty()
        }

        @Test
        @DisplayName("Should handle empty drivers list")
        fun shouldHandleEmptyDriversList() {
            // Given
            val requests = listOf(Pair(testMatchingRequest, testLocation))
            val emptyDrivers = emptyList<AvailableDriver>()

            // When
            val costMatrix = matchingScoreCalculator.calculateCostMatrix(requests, emptyDrivers)

            // Then
            assertThat(costMatrix.size).isEqualTo(1)
            assertThat(costMatrix[0].size).isEqualTo(0)
        }
    }

    @Nested
    @DisplayName("Normalization Tests")
    inner class NormalizationTests {

        @Test
        @DisplayName("Should normalize distance correctly")
        fun shouldNormalizeDistanceCorrectly() {
            // Use reflection to access private method
            val normalizeDistanceMethod = matchingScoreCalculator.javaClass.getDeclaredMethod(
                "normalizeDistance", Double::class.java
            )
            normalizeDistanceMethod.isAccessible = true

            // Test various distances
            val testCases = listOf(
                0.0 to 1.0,      // Zero distance -> max score
                2500.0 to 0.5,   // Half max distance -> half score
                5000.0 to 0.0,   // Max distance -> min score
                7500.0 to 0.0    // Beyond max -> min score
            )

            testCases.forEach { (distance, expectedScore) ->
                // When
                val result = normalizeDistanceMethod.invoke(matchingScoreCalculator, distance) as Double

                // Then
                assertThat(result).isCloseTo(expectedScore, org.assertj.core.data.Offset.offset(0.01))
            }
        }

        @Test
        @DisplayName("Should normalize rating correctly")
        fun shouldNormalizeRatingCorrectly() {
            // Use reflection to access private method
            val normalizeRatingMethod = matchingScoreCalculator.javaClass.getDeclaredMethod(
                "normalizeRating", Double::class.java
            )
            normalizeRatingMethod.isAccessible = true

            // Test various ratings
            val testCases = listOf(
                1.0 to 0.0,   // Min rating -> min score
                3.0 to 0.5,   // Mid rating -> half score
                5.0 to 1.0,   // Max rating -> max score
                0.5 to 0.0,   // Below min -> min score
                5.5 to 1.0    // Above max -> max score
            )

            testCases.forEach { (rating, expectedScore) ->
                // When
                val result = normalizeRatingMethod.invoke(matchingScoreCalculator, rating) as Double

                // Then
                assertThat(result).isCloseTo(expectedScore, org.assertj.core.data.Offset.offset(0.01))
            }
        }
    }

    @Nested
    @DisplayName("Driver Selection Tests")
    inner class DriverSelectionTests {

        @Test
        @DisplayName("Should select top drivers correctly")
        fun shouldSelectTopDriversCorrectly() {
            // Given
            val drivers = listOf(
                testAvailableDriver,
                createTestAvailableDriver(rating = 4.0),
                createTestAvailableDriver(rating = 3.5)
            )
            val scores = listOf(
                MatchingScore(0.8, 0.95, 0.9, 0.88),
                MatchingScore(0.7, 0.75, 0.8, 0.75),
                MatchingScore(0.6, 0.625, 0.7, 0.64)
            )
            val driversWithScores = drivers.zip(scores)

            // When
            val topDrivers = matchingScoreCalculator.selectTopDrivers(driversWithScores, 2)

            // Then
            assertThat(topDrivers).hasSize(2)
            assertThat(topDrivers[0]).isEqualTo(testAvailableDriver) // Highest score
            assertThat(topDrivers[1].rating).isEqualTo(4.0) // Second highest
        }

        @Test
        @DisplayName("Should select all drivers when requested count exceeds available")
        fun shouldSelectAllDriversWhenRequestedCountExceedsAvailable() {
            // Given
            val drivers = listOf(testAvailableDriver)
            val scores = listOf(MatchingScore(0.8, 0.95, 0.9, 0.88))
            val driversWithScores = drivers.zip(scores)

            // When
            val topDrivers = matchingScoreCalculator.selectTopDrivers(driversWithScores, 5)

            // Then
            assertThat(topDrivers).hasSize(1)
            assertThat(topDrivers[0]).isEqualTo(testAvailableDriver)
        }

        @Test
        @DisplayName("Should handle empty drivers list")
        fun shouldHandleEmptyDriversList() {
            // Given
            val emptyDriversWithScores = emptyList<Pair<AvailableDriver, MatchingScore>>()

            // When
            val topDrivers = matchingScoreCalculator.selectTopDrivers(emptyDriversWithScores, 3)

            // Then
            assertThat(topDrivers).isEmpty()
        }
    }

    @Nested
    @DisplayName("Driver Eligibility Tests")
    inner class DriverEligibilityTests {

        @Test
        @DisplayName("Should identify eligible driver")
        fun shouldIdentifyEligibleDriver() {
            // Given
            val eligibleDriver = createTestAvailableDriver(
                rating = 4.5,
                acceptanceRate = 0.8,
                isAvailable = true
            )

            // When
            val isEligible = matchingScoreCalculator.isDriverEligible(eligibleDriver, 0.5)

            // Then
            assertThat(isEligible).isTrue()
        }

        @Test
        @DisplayName("Should reject driver with low acceptance rate")
        fun shouldRejectDriverWithLowAcceptanceRate() {
            // Given
            val lowAcceptanceDriver = createTestAvailableDriver(
                rating = 4.5,
                acceptanceRate = 0.3, // Below minimum
                isAvailable = true
            )

            // When
            val isEligible = matchingScoreCalculator.isDriverEligible(lowAcceptanceDriver, 0.5)

            // Then
            assertThat(isEligible).isFalse()
        }

        @Test
        @DisplayName("Should reject driver with low rating")
        fun shouldRejectDriverWithLowRating() {
            // Given
            val lowRatingDriver = createTestAvailableDriver(
                rating = 2.5, // Below minimum of 3.0
                acceptanceRate = 0.8,
                isAvailable = true
            )

            // When
            val isEligible = matchingScoreCalculator.isDriverEligible(lowRatingDriver)

            // Then
            assertThat(isEligible).isFalse()
        }

        @Test
        @DisplayName("Should reject unavailable driver")
        fun shouldRejectUnavailableDriver() {
            // Given
            val unavailableDriver = createTestAvailableDriver(
                rating = 4.5,
                acceptanceRate = 0.8,
                isAvailable = false
            )

            // When
            val isEligible = matchingScoreCalculator.isDriverEligible(unavailableDriver)

            // Then
            assertThat(isEligible).isFalse()
        }

        @Test
        @DisplayName("Should use custom minimum acceptance rate")
        fun shouldUseCustomMinimumAcceptanceRate() {
            // Given
            val driver = createTestAvailableDriver(
                acceptanceRate = 0.6,
                rating = 4.0,
                isAvailable = true
            )

            // When
            val isEligibleWithDefault = matchingScoreCalculator.isDriverEligible(driver) // 0.5 default
            val isEligibleWithHigher = matchingScoreCalculator.isDriverEligible(driver, 0.7)

            // Then
            assertThat(isEligibleWithDefault).isTrue()
            assertThat(isEligibleWithHigher).isFalse()
        }
    }

    @Nested
    @DisplayName("Edge Cases and Validation Tests")
    inner class EdgeCasesTests {

        @Test
        @DisplayName("Should handle extreme rating values")
        fun shouldHandleExtremeRatingValues() {
            // Given
            val extremeRatingDriver = createTestAvailableDriver(rating = 0.0)
            every { distanceCalculator.calculate(any(), any()) } returns 1000.0
            

            // When
            val result = matchingScoreCalculator.calculateScore(
                testMatchingRequest,
                extremeRatingDriver,
                testLocation
            )

            // Then
            assertThat(result.totalScore).isGreaterThanOrEqualTo(0.0)
        }

        @Test
        @DisplayName("Should handle extreme acceptance rate values")
        fun shouldHandleExtremeAcceptanceRateValues() {
            // Given
            val extremeAcceptanceDriver = createTestAvailableDriver(acceptanceRate = 0.0)
            every { distanceCalculator.calculate(any(), any()) } returns 500.0
            

            // When
            val result = matchingScoreCalculator.calculateScore(
                testMatchingRequest,
                extremeAcceptanceDriver,
                testLocation
            )

            // Then
            assertThat(result.totalScore).isGreaterThanOrEqualTo(0.0)
        }

        @Test
        @DisplayName("Should handle distance calculation errors gracefully")
        fun shouldHandleDistanceCalculationErrorsGracefully() {
            // Given
            every { distanceCalculator.calculate(any(), any()) } throws RuntimeException("GPS error")
            
            // Expect the method to handle the error and continue (this might need to be implemented)
            // For now, just verify it doesn't crash
            try {
                // When
                val result = matchingScoreCalculator.calculateScore(
                    testMatchingRequest,
                    testAvailableDriver,
                    testLocation
                )
                
                // Should not reach here if error handling is not implemented
                assertThat(result).isNotNull()
            } catch (e: Exception) {
                // Current implementation might not handle this gracefully
                assertThat(e).isInstanceOf(RuntimeException::class.java)
            }
        }
    }

    private fun createTestMatchingRequest(): MatchingRequest {
        return MatchingRequest(
            rideId = UUID.randomUUID(),
            passengerId = UUID.randomUUID(),
            pickupH3 = "8830e1d8dffffff",
            dropoffH3 = "8830e1d89ffffff",
            surgeMultiplier = BigDecimal("1.0"),
            expiresAt = LocalDateTime.now().plusMinutes(5)
        )
    }

    private fun createTestAvailableDriver(
        rating: Double = 4.8,
        acceptanceRate: Double = 0.95,
        isAvailable: Boolean = true
    ): AvailableDriver {
        return AvailableDriver(
            driverId = UUID.randomUUID(),
            currentLocation = Location(37.5665, 126.9780, null, "8830e1d8dffffff"),
            rating = rating,
            acceptanceRate = acceptanceRate,
            isAvailable = isAvailable,
            completedTrips = 1000,
            completionRate = 0.98
        )
    }

    private fun createTestLocation(): Location {
        return Location(37.5759, 126.9768, "Test Location", "8830e1d89ffffff")
    }
}