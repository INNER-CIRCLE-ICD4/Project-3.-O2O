package com.ddakta.matching.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "matching")
data class MatchingProperties(
    var batch: BatchProperties = BatchProperties(),
    var driverCall: DriverCallProperties = DriverCallProperties(),
    var distance: DistanceProperties = DistanceProperties(),
    var rating: RatingProperties = RatingProperties(),
    var acceptance: AcceptanceProperties = AcceptanceProperties(),
    var search: SearchProperties = SearchProperties()
) {
    data class BatchProperties(
        var size: Int = 100,
        var interval: Long = 1000 // milliseconds
    )

    data class DriverCallProperties(
        var timeout: Int = 15, // seconds
        var maxDrivers: Int = 3
    )

    data class DistanceProperties(
        var weight: Double = 0.7
    )

    data class RatingProperties(
        var weight: Double = 0.2
    )

    data class AcceptanceProperties(
        var weight: Double = 0.1
    )

    data class SearchProperties(
        var radiusKm: Double = 2.0,
        var maxDrivers: Int = 20
    )
    
    val distanceWeight: Double
        get() = distance.weight
        
    val ratingWeight: Double
        get() = rating.weight
        
    val acceptanceWeight: Double
        get() = acceptance.weight
}