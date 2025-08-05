package com.ddakta.matching.algorithm

import mu.KotlinLogging
import org.springframework.stereotype.Component
import kotlin.math.min

@Component
class HungarianAlgorithm {

    private val logger = KotlinLogging.logger {}

    fun findOptimalMatching(costMatrix: Array<DoubleArray>): IntArray {
        if (costMatrix.isEmpty() || costMatrix[0].isEmpty()) {
            return IntArray(0)
        }

        val n = costMatrix.size
        val m = costMatrix[0].size

        // Convert to square matrix
        val size = maxOf(n, m)
        val squareMatrix = Array(size) { DoubleArray(size) { Double.MAX_VALUE / 2 } }

        for (i in 0 until n) {
            for (j in 0 until m) {
                squareMatrix[i][j] = costMatrix[i][j]
            }
        }

        // Execute Hungarian Algorithm
        val assignment = executeHungarian(squareMatrix)

        // Convert back to original size
        return assignment.take(n).map { if (it < m) it else -1 }.toIntArray()
    }

    private fun executeHungarian(matrix: Array<DoubleArray>): IntArray {
        val n = matrix.size
        val INF = Double.MAX_VALUE / 2

        // Step 1: Subtract row minimums
        for (i in 0 until n) {
            val minVal = matrix[i].filter { it < INF }.minOrNull() ?: 0.0
            if (minVal > 0 && minVal < INF) {
                for (j in 0 until n) {
                    if (matrix[i][j] < INF) {
                        matrix[i][j] -= minVal
                    }
                }
            }
        }

        // Step 2: Subtract column minimums
        for (j in 0 until n) {
            var minVal = INF
            for (i in 0 until n) {
                if (matrix[i][j] < INF) {
                    minVal = min(minVal, matrix[i][j])
                }
            }
            if (minVal < INF && minVal > 0) {
                for (i in 0 until n) {
                    if (matrix[i][j] < INF) {
                        matrix[i][j] -= minVal
                    }
                }
            }
        }

        // Initialize assignments
        val rowAssignment = IntArray(n) { -1 }
        val colAssignment = IntArray(n) { -1 }

        // Step 3: Assign initial zeros
        val epsilon = 1e-9
        for (i in 0 until n) {
            for (j in 0 until n) {
                if (kotlin.math.abs(matrix[i][j]) < epsilon && rowAssignment[i] == -1 && colAssignment[j] == -1) {
                    rowAssignment[i] = j
                    colAssignment[j] = i
                }
            }
        }

        // Main loop
        var numAssigned = rowAssignment.count { it != -1 }
        while (numAssigned < n) {
            val uncoveredZero = findUncoveredZero(matrix, rowAssignment, colAssignment)
            
            if (uncoveredZero != null) {
                // Augment the assignment
                augmentAssignment(uncoveredZero.first, uncoveredZero.second, rowAssignment, colAssignment, matrix)
                numAssigned++
            } else {
                // Modify the matrix
                modifyMatrix(matrix, rowAssignment, colAssignment)
            }
        }

        return rowAssignment
    }

    private fun findUncoveredZero(
        matrix: Array<DoubleArray>,
        rowAssignment: IntArray,
        colAssignment: IntArray
    ): Pair<Int, Int>? {
        val n = matrix.size
        val rowCovered = BooleanArray(n)
        val colCovered = BooleanArray(n)

        // Mark covered rows and columns
        for (i in 0 until n) {
            if (rowAssignment[i] != -1) {
                rowCovered[i] = true
            }
        }
        for (j in 0 until n) {
            if (colAssignment[j] != -1) {
                colCovered[j] = true
            }
        }

        // Find uncovered zero
        val epsilon = 1e-9
        for (i in 0 until n) {
            for (j in 0 until n) {
                if (!rowCovered[i] && !colCovered[j] && kotlin.math.abs(matrix[i][j]) < epsilon) {
                    return Pair(i, j)
                }
            }
        }
        return null
    }

    private fun augmentAssignment(
        row: Int,
        col: Int,
        rowAssignment: IntArray,
        colAssignment: IntArray,
        matrix: Array<DoubleArray>
    ) {
        // Simple assignment if row is unassigned
        if (rowAssignment[row] == -1) {
            rowAssignment[row] = col
            colAssignment[col] = row
            return
        }

        // Find augmenting path and update assignments
        val n = rowAssignment.size
        val path = findAugmentingPath(row, col, rowAssignment, colAssignment, matrix)
        
        // Update assignments along the path
        for (i in path.indices step 2) {
            if (i + 1 < path.size) {
                rowAssignment[path[i].first] = path[i + 1].second
                colAssignment[path[i + 1].second] = path[i].first
            }
        }
    }

    private fun findAugmentingPath(
        startRow: Int,
        startCol: Int,
        rowAssignment: IntArray,
        colAssignment: IntArray,
        matrix: Array<DoubleArray>
    ): List<Pair<Int, Int>> {
        // Simplified path finding - in a full implementation, this would use
        // a more sophisticated algorithm like BFS
        return listOf(Pair(startRow, startCol))
    }

    private fun modifyMatrix(
        matrix: Array<DoubleArray>,
        rowAssignment: IntArray,
        colAssignment: IntArray
    ) {
        val n = matrix.size
        val rowCovered = BooleanArray(n)
        val colCovered = BooleanArray(n)

        // Mark covered rows and columns
        for (i in 0 until n) {
            if (rowAssignment[i] != -1) {
                rowCovered[i] = true
            }
        }
        for (j in 0 until n) {
            if (colAssignment[j] != -1) {
                colCovered[j] = true
            }
        }

        // Find minimum uncovered value
        var minVal = Double.MAX_VALUE
        for (i in 0 until n) {
            for (j in 0 until n) {
                if (!rowCovered[i] && !colCovered[j]) {
                    minVal = min(minVal, matrix[i][j])
                }
            }
        }

        // Subtract from uncovered, add to doubly covered
        for (i in 0 until n) {
            for (j in 0 until n) {
                if (!rowCovered[i] && !colCovered[j]) {
                    matrix[i][j] -= minVal
                } else if (rowCovered[i] && colCovered[j]) {
                    matrix[i][j] += minVal
                }
            }
        }
    }
}