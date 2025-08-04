package com.ddakta.matching.domain.vo

data class MatchingScore(
    val distanceScore: Double,
    val ratingScore: Double,
    val acceptanceScore: Double,
    val totalScore: Double
) {
    init {
        require(distanceScore in 0.0..1.0) { "Distance score must be between 0 and 1" }
        require(ratingScore in 0.0..1.0) { "Rating score must be between 0 and 1" }
        require(acceptanceScore in 0.0..1.0) { "Acceptance score must be between 0 and 1" }
        require(totalScore in 0.0..1.0) { "Total score must be between 0 and 1" }
    }
    
    companion object {
        fun calculate(
            distanceScore: Double,
            ratingScore: Double,
            acceptanceScore: Double,
            distanceWeight: Double = 0.7,
            ratingWeight: Double = 0.2,
            acceptanceWeight: Double = 0.1
        ): MatchingScore {
            val total = (distanceScore * distanceWeight) +
                       (ratingScore * ratingWeight) +
                       (acceptanceScore * acceptanceWeight)
            
            return MatchingScore(
                distanceScore = distanceScore,
                ratingScore = ratingScore,
                acceptanceScore = acceptanceScore,
                totalScore = total.coerceIn(0.0, 1.0)
            )
        }
    }
}