package com.ddakta.auth.common.annotation

/**
 * 현재 로그인한 사용자 정보를 주입받기 위한 어노테이션
 * 
 * Usage:
 * ```
 * // User 엔티티를 받는 경우 (DB 조회 발생)
 * @GetMapping("/profile")  
 * fun getProfile(@CurrentUser user: User): ResponseEntity<UserProfile>
 * 
 * // AuthenticationPrincipal만 받는 경우 (DB 조회 없음, 성능 최적화)
 * @PostMapping("/logout")
 * fun logout(@CurrentUser principal: AuthenticationPrincipal): ResponseEntity<Void>
 * ```
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class CurrentUser