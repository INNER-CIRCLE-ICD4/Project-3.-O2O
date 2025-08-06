package com.ddakta.location.controller

import com.ddakta.location.dto.LocationUpdateDto
import com.ddakta.location.domain.LocationUpdate
import com.ddakta.location.exception.ForbiddenException
import com.ddakta.location.repository.RedisGeoLocationRepository
import com.ddakta.location.service.LocationService
import com.ddakta.utils.security.AuthenticationPrincipal
import com.ddakta.utils.security.CurrentUser
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/locations")
class LocationController(
    private val locationService: LocationService
) {
//    @PostMapping
//    fun postLocation(
//        @CurrentUser principal: AuthenticationPrincipal,
//        @Validated @RequestBody dto: LocationUpdateDto
//    ): ResponseEntity<Void> {
//        // 1) @CurrentUser로 꺼낸 principal 정보로 드라이버 확인
//        if (!principal.isDriver()) {
//            throw ForbiddenException("Only drivers can send location")
//        }
//
//        // 2) 도메인 객체 생성
//        val update = LocationUpdate(
//            driverId  = principal.userId.toString(),
//            latitude  = dto.latitude!!,
//            longitude = dto.longitude!!,
//            timestamp = dto.timestamp!!
//        )
//
//        // 3) 위치 서비스 호출
//        locationService.updateLocation(update)
//        return ResponseEntity.ok().build()
//    }
}
