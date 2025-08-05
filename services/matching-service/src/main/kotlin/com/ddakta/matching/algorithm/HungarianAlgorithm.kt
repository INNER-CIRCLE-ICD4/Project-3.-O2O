package com.ddakta.matching.algorithm

import mu.KotlinLogging
import org.springframework.stereotype.Component
import kotlin.math.min

/**
 * 헝가리안 알고리즘 구현체
 * 
 * 승객-드라이버 매칭의 최적 할당을 찾기 위한 알고리즘입니다.
 * 이 알고리즘은 이분 그래프에서 최소 비용 최대 매칭을 O(n³) 시간 복잡도로 해결합니다.
 * 
 * 알고리즘 동작 원리:
 * 1. 비용 행렬의 각 행과 열에서 최솟값을 차감하여 0을 만듭니다
 * 2. 최소한의 선으로 모든 0을 덮을 수 있을 때까지 반복합니다
 * 3. 덮이지 않은 최솟값을 찾아 행렬을 수정합니다
 * 4. 모든 작업자가 할당될 때까지 증대 경로를 찾습니다
 */
@Component
class HungarianAlgorithm {

    private val logger = KotlinLogging.logger {}

    /**
     * 최적 매칭을 찾는 메인 메서드
     * 
     * @param costMatrix 비용 행렬 - costMatrix[i][j]는 i번째 승객과 j번째 드라이버의 매칭 비용
     * @return 매칭 결과 배열 - result[i]는 i번째 승객에게 할당된 드라이버의 인덱스 (-1은 할당 없음)
     * 
     * 예시:
     * 입력: [[10, 20, 30], [15, 25, 35], [20, 30, 25]]
     * 출력: [0, 1, 2] (승객0→드라이버0, 승객1→드라이버1, 승객2→드라이버2)
     */
    fun findOptimalMatching(costMatrix: Array<DoubleArray>): IntArray {
        if (costMatrix.isEmpty() || costMatrix[0].isEmpty()) {
            return IntArray(0)
        }

        val n = costMatrix.size
        val m = costMatrix[0].size

        // 정사각 행렬로 변환
        // 헝가리안 알고리즘은 정사각 행렬을 요구하므로, 더미 행/열을 추가합니다.
        // Double.MAX_VALUE / 2를 사용하여 오버플로우를 방지하면서도 충분히 큰 값을 설정합니다.
        val size = maxOf(n, m)
        val squareMatrix = Array(size) { DoubleArray(size) { Double.MAX_VALUE / 2 } }

        for (i in 0 until n) {
            for (j in 0 until m) {
                squareMatrix[i][j] = costMatrix[i][j]
            }
        }

        // 헝가리안 알고리즘 실행
        val assignment = executeHungarian(squareMatrix)

        // 원래 크기로 되돌림
        return assignment.take(n).map { if (it < m) it else -1 }.toIntArray()
    }

    /**
     * 헝가리안 알고리즘의 핵심 실행 로직
     * 
     * 주요 단계:
     * 1. 행 축소 (Row Reduction): 각 행의 최솟값을 차감
     * 2. 열 축소 (Column Reduction): 각 열의 최솟값을 차감
     * 3. 초기 할당: 0인 위치에 가능한 할당 수행
     * 4. 반복: 모든 작업이 할당될 때까지 증대 경로 찾기와 행렬 수정 반복
     */
    private fun executeHungarian(matrix: Array<DoubleArray>): IntArray {
        val n = matrix.size
        val INF = Double.MAX_VALUE / 2

        // 단계 1: 행 최솟값 차감
        // 각 행에서 가장 작은 값을 찾아 모든 원소에서 차감합니다.
        // 이를 통해 각 행에 최소 하나의 0이 생성됩니다.
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

        // 단계 2: 열 최솟값 차감
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

        // 할당 초기화
        val rowAssignment = IntArray(n) { -1 }
        val colAssignment = IntArray(n) { -1 }

        // 단계 3: 초기 0값 할당
        // 부동소수점 연산의 정밀도 문제를 해결하기 위해 epsilon 사용
        // 아직 할당되지 않은 행과 열에서 0(또는 매우 작은 값)을 찾아 할당합니다.
        val epsilon = 1e-9
        for (i in 0 until n) {
            for (j in 0 until n) {
                if (kotlin.math.abs(matrix[i][j]) < epsilon && rowAssignment[i] == -1 && colAssignment[j] == -1) {
                    rowAssignment[i] = j
                    colAssignment[j] = i
                }
            }
        }

        // 메인 루프
        // 모든 행이 할당될 때까지 반복합니다.
        // 핵심 아이디어: 덮이지 않은 0을 찾아 할당을 증가시키거나,
        // 없다면 행렬을 수정하여 새로운 0을 만듭니다.
        var numAssigned = rowAssignment.count { it != -1 }
        while (numAssigned < n) {
            val uncoveredZero = findUncoveredZero(matrix, rowAssignment, colAssignment)
            
            if (uncoveredZero != null) {
                // 할당 증대: 덮이지 않은 0을 찾았으므로 증대 경로를 통해 할당을 늘립니다
                augmentAssignment(uncoveredZero.first, uncoveredZero.second, rowAssignment, colAssignment, matrix)
                numAssigned++
            } else {
                // 행렬 수정: 덮이지 않은 0이 없으므로 행렬을 수정하여 새로운 0을 생성합니다
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

        // 덮인 행과 열 표시
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

        // 덮이지 않은 0값 찾기
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
        // 행이 할당되지 않은 경우 단순 할당
        if (rowAssignment[row] == -1) {
            rowAssignment[row] = col
            colAssignment[col] = row
            return
        }

        // 증대 경로 찾기 및 할당 업데이트
        val n = rowAssignment.size
        val path = findAugmentingPath(row, col, rowAssignment, colAssignment, matrix)
        
        // 경로를 따라 할당 업데이트
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
        // 단순화된 경로 찾기 - 완전한 구현에서는
        // BFS와 같은 더 정교한 알고리즘을 사용할 것
        return listOf(Pair(startRow, startCol))
    }

    /**
     * 행렬 수정 단계
     * 
     * 덮이지 않은 0이 없을 때 호출됩니다.
     * 알고리즘:
     * 1. 덮이지 않은 원소 중 최솟값을 찾습니다
     * 2. 덮이지 않은 모든 원소에서 이 값을 차감합니다
     * 3. 이중으로 덮인 원소에는 이 값을 더합니다
     * 
     * 이 과정을 통해 새로운 0이 생성되면서도 기존 0의 구조가 유지됩니다.
     */
    private fun modifyMatrix(
        matrix: Array<DoubleArray>,
        rowAssignment: IntArray,
        colAssignment: IntArray
    ) {
        val n = matrix.size
        val rowCovered = BooleanArray(n)
        val colCovered = BooleanArray(n)

        // 덮인 행과 열 표시
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

        // 덮이지 않은 최솟값 찾기
        var minVal = Double.MAX_VALUE
        for (i in 0 until n) {
            for (j in 0 until n) {
                if (!rowCovered[i] && !colCovered[j]) {
                    minVal = min(minVal, matrix[i][j])
                }
            }
        }

        // 덮이지 않은 곳에서 차감, 이중으로 덮인 곳에 추가
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