package com.ddakta.location.controller

import com.ddakta.location.dto.NearbyDriverDto
import com.ddakta.location.service.LocationService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/locations")
class LocationController(
    private val locationService: LocationService
) {

    /**
     * H3 인덱스 기반으로 주변 드라이버를 검색하는 API
     * @param h3Index 중심 H3 인덱스
     * @param kRingSize 검색할 인접 지역의 범위 (k-ring 크기)
     * @return 주변 드라이버 리스트
     */
    @GetMapping("/h3-drivers")
    fun getDriversInH3Cells(
        @RequestParam h3Index: String,
        @RequestParam(defaultValue = "1") kRingSize: Int
    ): ResponseEntity<List<NearbyDriverDto>> {
        val nearbyDrivers = locationService.findDriversInH3Cells(h3Index, kRingSize)
        return ResponseEntity.ok(nearbyDrivers)
    }
}