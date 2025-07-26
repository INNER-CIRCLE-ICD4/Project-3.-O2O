package com.ddakta.common.user.event

import com.ddakta.common.user.cache.UserCacheService
import com.ddakta.common.user.dto.RiderInfo
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

@Component
class UserEventListener(
    private val userCacheService: UserCacheService,
    private val objectMapper: ObjectMapper
) {
    
    private val logger = LoggerFactory.getLogger(javaClass)
    
    @KafkaListener(
        topics = ["user-events"],
        groupId = "\${spring.application.name}-user-events",
        containerFactory = "kafkaListenerContainerFactory"
    )
    fun handleUserEvent(message: String, acknowledgment: Acknowledgment) {
        try {
            val event = objectMapper.readValue(message, UserEvent::class.java)
            logger.debug("Received user event: ${event.javaClass.simpleName} for user ${event.aggregateId}")
            
            when (event) {
                is UserCreatedEvent -> handleUserCreated(event)
                is UserUpdatedEvent -> handleUserUpdated(event)
                is UserDeletedEvent -> handleUserDeleted(event)
                is RiderStatusChangedEvent -> handleRiderStatusChanged(event)
                is RiderLocationUpdatedEvent -> handleRiderLocationUpdated(event)
            }
            
            acknowledgment.acknowledge()
        } catch (e: Exception) {
            logger.error("Error processing user event: $message", e)
            // 에러 처리 전략에 따라 재시도 또는 DLQ로 전송
        }
    }
    
    private fun handleUserCreated(event: UserCreatedEvent) {
        // 새 사용자 생성 시 캐시 무효화 (다음 조회 시 최신 정보 가져오기)
        logger.info("User created: ${event.aggregateId}")
        userCacheService.invalidateUser(event.aggregateId)
    }
    
    private fun handleUserUpdated(event: UserUpdatedEvent) {
        logger.info("User updated: ${event.aggregateId}, fields: ${event.updatedFields}")
        // 캐시 무효화 후 재조회하여 최신 정보 갱신
        userCacheService.invalidateUser(event.aggregateId)
    }
    
    private fun handleUserDeleted(event: UserDeletedEvent) {
        logger.info("User deleted: ${event.aggregateId}")
        userCacheService.invalidateUser(event.aggregateId)
    }
    
    private fun handleRiderStatusChanged(event: RiderStatusChangedEvent) {
        logger.info("Rider status changed: ${event.aggregateId}, online: ${event.isOnline}, accepting: ${event.acceptingRides}")
        
        // 라이더 상태 즉시 업데이트
        userCacheService.getUserInfo(event.aggregateId)?.let { userInfo ->
            if (userInfo is RiderInfo) {
                val updatedRider = userInfo.copy(
                    isOnline = event.isOnline,
                    acceptingRides = event.acceptingRides
                )
                userCacheService.updateUserInfo(updatedRider)
            }
        } ?: run {
            // 캐시에 없으면 무효화하여 다음 조회 시 최신 정보 가져오기
            userCacheService.invalidateUser(event.aggregateId)
        }
    }
    
    private fun handleRiderLocationUpdated(event: RiderLocationUpdatedEvent) {
        logger.debug("Rider location updated: ${event.aggregateId}")
        
        // 라이더 위치 업데이트
        userCacheService.getUserInfo(event.aggregateId)?.let { userInfo ->
            if (userInfo is RiderInfo) {
                val updatedRider = userInfo.copy(
                    currentLocation = event.location,
                    lastActiveAt = event.timestamp
                )
                userCacheService.updateUserInfo(updatedRider)
            }
        }
    }
}