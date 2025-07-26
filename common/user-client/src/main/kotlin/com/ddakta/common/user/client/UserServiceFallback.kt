package com.ddakta.common.user.client

import com.ddakta.common.user.dto.UserInfo
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class UserServiceFallback : UserServiceClient {
    
    private val logger = LoggerFactory.getLogger(javaClass)
    
    override fun getUserInfo(userId: UUID): UserInfo? {
        logger.warn("Fallback triggered for getUserInfo with userId: $userId")
        return null
    }
    
    override fun getUsersBatch(userIds: List<UUID>): Map<UUID, UserInfo> {
        logger.warn("Fallback triggered for getUsersBatch with ${userIds.size} userIds")
        return emptyMap()
    }
}