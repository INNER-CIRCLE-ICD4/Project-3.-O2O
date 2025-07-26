package com.ddakta.common.user.client

import com.ddakta.common.user.dto.UserInfo
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import java.util.UUID

@FeignClient(
    name = "auth-service",
    url = "\${ddakta.auth-service.url}",
    fallback = UserServiceFallback::class
)
interface UserServiceClient {
    
    @GetMapping("/internal/users/{userId}")
    fun getUserInfo(@PathVariable userId: UUID): UserInfo?
    
    @GetMapping("/internal/users/batch")
    fun getUsersBatch(@RequestParam userIds: List<UUID>): Map<UUID, UserInfo>
}