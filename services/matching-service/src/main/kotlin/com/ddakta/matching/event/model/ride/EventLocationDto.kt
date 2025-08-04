package com.ddakta.matching.event.model.ride

import com.ddakta.matching.domain.vo.Location
import com.ddakta.matching.dto.internal.LocationInfo

data class EventLocationDto(
    val latitude: Double,
    val longitude: Double,
    val address: String?,
    val h3Index: String
) {
    companion object {
        fun from(location: Location): EventLocationDto {
            return EventLocationDto(
                latitude = location.latitude,
                longitude = location.longitude,
                address = location.address,
                h3Index = location.h3Index
            )
        }
        
        fun from(locationInfo: LocationInfo): EventLocationDto {
            return EventLocationDto(
                latitude = locationInfo.latitude,
                longitude = locationInfo.longitude,
                address = null,
                h3Index = locationInfo.h3Index
            )
        }
    }
}