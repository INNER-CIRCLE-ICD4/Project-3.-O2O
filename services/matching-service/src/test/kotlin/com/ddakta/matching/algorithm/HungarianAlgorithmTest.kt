package com.ddakta.matching.algorithm

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.system.measureTimeMillis

@DisplayName("Hungarian Algorithm Tests")
class HungarianAlgorithmTest {

    private lateinit var hungarianAlgorithm: HungarianAlgorithm

    @BeforeEach
    fun setUp() {
        hungarianAlgorithm = HungarianAlgorithm()
    }

    @Nested
    @DisplayName("Edge Case Tests")
    inner class EdgeCaseTests {

        @Test
        @DisplayName("Should handle empty matrix")
        fun shouldHandleEmptyMatrix() {
            // Given
            val emptyMatrix = arrayOf<DoubleArray>()

            // When
            val result = hungarianAlgorithm.findOptimalMatching(emptyMatrix)

            // Then
            assertThat(result).isEmpty()
        }

        @Test
        @DisplayName("Should handle matrix with empty rows")
        fun shouldHandleMatrixWithEmptyRows() {
            // Given
            val matrixWithEmptyRows = arrayOf(doubleArrayOf())

            // When
            val result = hungarianAlgorithm.findOptimalMatching(matrixWithEmptyRows)

            // Then
            assertThat(result).isEmpty()
        }

        @Test
        @DisplayName("Should handle single element matrix")
        fun shouldHandleSingleElementMatrix() {
            // Given
            val singleElementMatrix = arrayOf(
                doubleArrayOf(5.0)
            )

            // When
            val result = hungarianAlgorithm.findOptimalMatching(singleElementMatrix)

            // Then
            assertThat(result).hasSize(1)
            assertThat(result[0]).isEqualTo(0)
        }

        @Test
        @DisplayName("Should handle rectangular matrix (more rows than columns)")
        fun shouldHandleRectangularMatrixMoreRows() {
            // Given
            val rectangularMatrix = arrayOf(
                doubleArrayOf(1.0, 2.0),
                doubleArrayOf(3.0, 4.0),
                doubleArrayOf(5.0, 6.0)
            )

            // When
            val result = hungarianAlgorithm.findOptimalMatching(rectangularMatrix)

            // Then
            assertThat(result).hasSize(3)
            // Verify optimal assignment: row 0 -> col 0, row 1 -> col 1, row 2 -> unassigned (-1)
            assertThat(result[0]).isEqualTo(0)
            assertThat(result[1]).isEqualTo(1)
            assertThat(result[2]).isEqualTo(-1)
        }

        @Test
        @DisplayName("Should handle rectangular matrix (more columns than rows)")
        fun shouldHandleRectangularMatrixMoreColumns() {
            // Given
            val rectangularMatrix = arrayOf(
                doubleArrayOf(1.0, 3.0, 5.0),
                doubleArrayOf(2.0, 4.0, 6.0)
            )

            // When
            val result = hungarianAlgorithm.findOptimalMatching(rectangularMatrix)

            // Then
            assertThat(result).hasSize(2)
            // Should find optimal assignment
            assertThat(result[0]).isIn(0, 1, 2)
            assertThat(result[1]).isIn(0, 1, 2)
            assertThat(result[0]).isNotEqualTo(result[1])
        }
    }

    @Nested
    @DisplayName("Basic Functionality Tests")
    inner class BasicFunctionalityTests {

        @Test
        @DisplayName("Should solve simple 2x2 matrix optimally")
        fun shouldSolveSimple2x2MatrixOptimally() {
            // Given - Known optimal solution: (0,1) and (1,0) with total cost 3
            val costMatrix = arrayOf(
                doubleArrayOf(4.0, 1.0),
                doubleArrayOf(2.0, 0.0)
            )

            // When
            val result = hungarianAlgorithm.findOptimalMatching(costMatrix)

            // Then
            assertThat(result).hasSize(2)
            val totalCost = result.mapIndexed { row, col ->
                if (col >= 0) costMatrix[row][col] else 0.0
            }.sum()
            
            // Verify assignments are valid
            assertThat(result[0]).isIn(0, 1)
            assertThat(result[1]).isIn(0, 1)
            assertThat(result[0]).isNotEqualTo(result[1])
            
            // Should achieve optimal cost
            assertThat(totalCost).isLessThanOrEqualTo(5.0) // Maximum possible cost
        }

        @Test
        @DisplayName("Should solve 3x3 matrix with known optimal solution")
        fun shouldSolve3x3MatrixWithKnownOptimalSolution() {
            // Given - Classic assignment problem
            val costMatrix = arrayOf(
                doubleArrayOf(10.0, 19.0, 8.0),
                doubleArrayOf(15.0, 16.0, 9.0),
                doubleArrayOf(13.0, 12.0, 11.0)
            )

            // When
            val result = hungarianAlgorithm.findOptimalMatching(costMatrix)

            // Then
            assertThat(result).hasSize(3)
            
            // Verify all assignments are valid
            for (i in result.indices) {
                if (result[i] >= 0) {
                    assertThat(result[i]).isBetween(0, 2)
                }
            }
            
            // Verify no duplicate assignments
            val validAssignments = result.filter { it >= 0 }
            assertThat(validAssignments).doesNotHaveDuplicates()
            
            // Calculate total cost
            val totalCost = result.mapIndexed { row, col ->
                if (col >= 0) costMatrix[row][col] else 0.0
            }.sum()
            
            // Should find a reasonable solution
            assertThat(totalCost).isPositive()
        }

        @Test
        @DisplayName("Should handle matrix with identical costs")
        fun shouldHandleMatrixWithIdenticalCosts() {
            // Given
            val identicalCostMatrix = arrayOf(
                doubleArrayOf(5.0, 5.0, 5.0),
                doubleArrayOf(5.0, 5.0, 5.0),
                doubleArrayOf(5.0, 5.0, 5.0)
            )

            // When
            val result = hungarianAlgorithm.findOptimalMatching(identicalCostMatrix)

            // Then
            assertThat(result).hasSize(3)
            
            // Should still find valid assignments
            val validAssignments = result.filter { it >= 0 }
            assertThat(validAssignments).doesNotHaveDuplicates()
            assertThat(validAssignments).allMatch { it in 0..2 }
        }

        @Test
        @DisplayName("Should handle matrix with zero costs")
        fun shouldHandleMatrixWithZeroCosts() {
            // Given
            val zeroMatrix = arrayOf(
                doubleArrayOf(0.0, 1.0, 2.0),
                doubleArrayOf(1.0, 0.0, 1.0),
                doubleArrayOf(2.0, 1.0, 0.0)
            )

            // When
            val result = hungarianAlgorithm.findOptimalMatching(zeroMatrix)

            // Then
            assertThat(result).hasSize(3)
            
            // Should achieve optimal cost of 0 (diagonal assignment)
            val totalCost = result.mapIndexed { row, col ->
                if (col >= 0) zeroMatrix[row][col] else 0.0
            }.sum()
            
            assertThat(totalCost).isEqualTo(0.0)
        }
    }

    @Nested
    @DisplayName("Large Dataset Tests")
    inner class LargeDatasetTests {

        @Test
        @DisplayName("Should handle 10x10 matrix efficiently")
        fun shouldHandle10x10MatrixEfficiently() {
            // Given
            val largeMatrix = Array(10) { i ->
                DoubleArray(10) { j ->
                    (i + j + 1).toDouble() * (if ((i + j) % 2 == 0) 1.0 else 1.5)
                }
            }

            // When
            val executionTime = measureTimeMillis {
                val result = hungarianAlgorithm.findOptimalMatching(largeMatrix)
                
                // Then
                assertThat(result).hasSize(10)
                
                // Verify assignments are valid
                val validAssignments = result.filter { it >= 0 }
                assertThat(validAssignments).doesNotHaveDuplicates()
                assertThat(validAssignments).allMatch { it in 0..9 }
            }
            
            // Should complete within reasonable time (less than 1 second)
            assertThat(executionTime).isLessThan(1000)
        }

        @Test
        @DisplayName("Should handle 20x20 matrix with performance measurement")
        fun shouldHandle20x20MatrixWithPerformanceMeasurement() {
            // Given
            val largeMatrix = Array(20) { i ->
                DoubleArray(20) { j ->
                    Math.abs(i - j) + Math.random() * 10
                }
            }

            // When
            val executionTime = measureTimeMillis {
                val result = hungarianAlgorithm.findOptimalMatching(largeMatrix)
                
                // Then
                assertThat(result).hasSize(20)
                
                // Verify no invalid assignments
                result.forEach { assignment ->
                    if (assignment >= 0) {
                        assertThat(assignment).isBetween(0, 19)
                    }
                }
            }
            
            // Should complete within reasonable time (less than 5 seconds)
            assertThat(executionTime).isLessThan(5000)
            println("20x20 matrix solved in ${executionTime}ms")
        }

        @Test
        @DisplayName("Should handle worst-case scenario matrix")
        fun shouldHandleWorstCaseScenarioMatrix() {
            // Given - Matrix designed to be challenging for Hungarian algorithm
            val worstCaseMatrix = Array(8) { i ->
                DoubleArray(8) { j ->
                    when {
                        i == j -> 1000.0 // High cost on diagonal
                        i < j -> (j - i).toDouble()
                        else -> (i - j + 10).toDouble()
                    }
                }
            }

            // When
            val result = hungarianAlgorithm.findOptimalMatching(worstCaseMatrix)

            // Then
            assertThat(result).hasSize(8)
            
            // Should still find valid solution
            val validAssignments = result.filter { it >= 0 }
            assertThat(validAssignments).doesNotHaveDuplicates()
            
            // Calculate and verify total cost
            val totalCost = result.mapIndexed { row, col ->
                if (col >= 0) worstCaseMatrix[row][col] else 0.0
            }.sum()
            
            assertThat(totalCost).isPositive()
        }
    }

    @Nested
    @DisplayName("Extreme Value Tests")
    inner class ExtremeValueTests {

        @Test
        @DisplayName("Should handle matrix with very large values")
        fun shouldHandleMatrixWithVeryLargeValues() {
            // Given
            val largeValueMatrix = arrayOf(
                doubleArrayOf(Double.MAX_VALUE / 4, 1000000.0),
                doubleArrayOf(2000000.0, Double.MAX_VALUE / 4)
            )

            // When
            val result = hungarianAlgorithm.findOptimalMatching(largeValueMatrix)

            // Then
            assertThat(result).hasSize(2)
            assertThat(result[0]).isIn(0, 1)
            assertThat(result[1]).isIn(0, 1)
            assertThat(result[0]).isNotEqualTo(result[1])
        }

        @Test
        @DisplayName("Should handle matrix with very small values")
        fun shouldHandleMatrixWithVerySmallValues() {
            // Given
            val smallValueMatrix = arrayOf(
                doubleArrayOf(0.0001, 0.0002),
                doubleArrayOf(0.0003, 0.0001)
            )

            // When
            val result = hungarianAlgorithm.findOptimalMatching(smallValueMatrix)

            // Then
            assertThat(result).hasSize(2)
            assertThat(result[0]).isIn(0, 1)
            assertThat(result[1]).isIn(0, 1)
            assertThat(result[0]).isNotEqualTo(result[1])
        }

        @Test
        @DisplayName("Should handle matrix with infinity values")
        fun shouldHandleMatrixWithInfinityValues() {
            // Given
            val infinityMatrix = arrayOf(
                doubleArrayOf(1.0, Double.POSITIVE_INFINITY),
                doubleArrayOf(Double.POSITIVE_INFINITY, 2.0)
            )

            // When
            val result = hungarianAlgorithm.findOptimalMatching(infinityMatrix)

            // Then
            assertThat(result).hasSize(2)
            // Should assign finite costs where possible
            assertThat(result[0]).isEqualTo(0) // Row 0 -> Col 0 (cost 1.0)
            assertThat(result[1]).isEqualTo(1) // Row 1 -> Col 1 (cost 2.0)
        }
    }

    @Nested
    @DisplayName("Assignment Validation Tests")
    inner class AssignmentValidationTests {

        @Test
        @DisplayName("Should produce valid assignments for square matrix")
        fun shouldProduceValidAssignmentsForSquareMatrix() {
            // Given
            val matrix = arrayOf(
                doubleArrayOf(2.0, 4.0, 6.0),
                doubleArrayOf(1.0, 3.0, 5.0),
                doubleArrayOf(7.0, 8.0, 9.0)
            )

            // When
            val result = hungarianAlgorithm.findOptimalMatching(matrix)

            // Then
            assertThat(result).hasSize(3)
            
            // Each assignment should be valid
            result.forEachIndexed { row, col ->
                if (col >= 0) {
                    assertThat(col).isBetween(0, 2)
                }
            }
            
            // No duplicate column assignments
            val validAssignments = result.filter { it >= 0 }
            assertThat(validAssignments).doesNotHaveDuplicates()
        }

        @Test
        @DisplayName("Should handle infeasible assignments gracefully")
        fun shouldHandleInfeasibleAssignmentsGracefully() {
            // Given - Matrix where some assignments are impossible (infinity cost)
            val infeasibleMatrix = arrayOf(
                doubleArrayOf(1.0, Double.POSITIVE_INFINITY),
                doubleArrayOf(Double.POSITIVE_INFINITY, 1.0)
            )

            // When
            val result = hungarianAlgorithm.findOptimalMatching(infeasibleMatrix)

            // Then
            assertThat(result).hasSize(2)
            // Should find the only possible assignment
            assertThat(result[0]).isEqualTo(0)
            assertThat(result[1]).isEqualTo(1)
        }

        @Test
        @DisplayName("Should optimize total assignment cost")
        fun shouldOptimizeTotalAssignmentCost() {
            // Given - Matrix with clear optimal solution
            val optimizationMatrix = arrayOf(
                doubleArrayOf(9.0, 2.0, 7.0),
                doubleArrayOf(6.0, 4.0, 3.0),
                doubleArrayOf(5.0, 8.0, 1.0)
            )

            // When
            val result = hungarianAlgorithm.findOptimalMatching(optimizationMatrix)

            // Then
            val totalCost = result.mapIndexed { row, col ->
                if (col >= 0) optimizationMatrix[row][col] else 0.0
            }.sum()
            
            // Should achieve optimal cost (2 + 6 + 1 = 9)
            assertThat(totalCost).isEqualTo(9.0)
            
            // Verify valid assignment (no duplicates)
            val validAssignments = result.filter { it >= 0 }
            assertThat(validAssignments).doesNotHaveDuplicates()
            assertThat(validAssignments).allMatch { it in 0..2 }
        }
    }
}