package com.ddakta.utils.security

/**
 * 현재 로그인한 사용자 정보를 주입받기 위한 어노테이션
 * 모든 서비스에서 공통으로 사용
 * 
 * Usage:
 * ```
 * @GetMapping("/profile")  
 * fun getProfile(@CurrentUser principal: AuthenticationPrincipal): ResponseEntity<UserProfile>
 * 
 * @PostMapping("/rides")
 * fun createRide(
 *     @CurrentUser principal: AuthenticationPrincipal,
 *     @RequestBody request: CreateRideRequest
 * ): ResponseEntity<RideResponse>
 * ```
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class CurrentUser