package com.ddakta.location.controller

import com.ddakta.location.domain.LocationUpdate
import com.ddakta.location.dto.LocationUpdateDto
import com.ddakta.location.dto.NearbyDriverDto
import com.ddakta.location.service.LocationService
import com.ddakta.utils.security.AuthenticationPrincipal
import com.ddakta.utils.security.CurrentUser
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/locations")
class LocationController(
    private val locationService: LocationService
) {
    // (1) 위치 업데이트: OAuth로 인증된 드라이버만
    @PostMapping
    fun updateLocation(
        @CurrentUser principal: AuthenticationPrincipal,
        @Validated @RequestBody dto: LocationUpdateDto
    ): ResponseEntity<Void> {
        val update = LocationUpdate(
            driverId  = principal.userId.toString(),
            latitude  = dto.latitude!!,
            longitude = dto.longitude!!,
            timestamp = dto.timestamp!!
        )
        locationService.updateLocation(update)
        return ResponseEntity.ok().build()
    }

    // (2) 주변 드라이버 검색
    @GetMapping("/nearby")
    fun getNearbyDrivers(
        @RequestParam latitude: Double,
        @RequestParam longitude: Double,
        @RequestParam radius: Double = 2000.0
    ): ResponseEntity<List<NearbyDriverDto>> {
        val nearby = locationService.findNearbyDrivers(latitude, longitude, radius)
        return ResponseEntity.ok(nearby)
    }
}
