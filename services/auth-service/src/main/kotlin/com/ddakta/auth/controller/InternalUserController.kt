package com.ddakta.auth.controller

import com.ddakta.auth.dto.UserInfo
import com.ddakta.auth.service.UserService
import com.ddakta.utils.response.ApiResponse
import io.swagger.v3.oas.annotations.Hidden
import org.springframework.web.bind.annotation.*
import java.util.*

/**
 * 다른 서비스에서 사용하는 내부 API
 * user-client 모듈이 이 API를 호출하여 유저 정보를 조회
 */
@RestController
@RequestMapping("/internal/users")
@Hidden // Swagger 문서에서 숨김 (내부 API)
class InternalUserController(
    private val userService: UserService
) {
    
    @GetMapping("/{userId}")
    fun getUserInfo(@PathVariable userId: UUID): ApiResponse<UserInfo> {
        val user = userService.findById(userId)
        return ApiResponse.success(UserInfo.from(user))
    }
    
    @GetMapping("/batch")
    fun getUsersBatch(@RequestParam userIds: List<UUID>): ApiResponse<Map<UUID, UserInfo>> {
        val users = userService.findByIds(userIds)
        val userInfoMap = users.associate { it.id to UserInfo.from(it) }
        return ApiResponse.success(userInfoMap)
    }
    
    @GetMapping("/by-email/{email}")
    fun getUserByEmail(@PathVariable email: String): ApiResponse<UserInfo> {
        val user = userService.findByEmail(email)
        return ApiResponse.success(UserInfo.from(user))
    }
}