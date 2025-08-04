package com.ddakta.matching.websocket

import com.ddakta.matching.dto.request.DriverLocationUpdateDto
import com.ddakta.matching.dto.response.RideLocationUpdateDto
import com.ddakta.matching.dto.response.RideStatusUpdateEventDto
import com.ddakta.matching.service.RideService
import mu.KotlinLogging
import org.springframework.messaging.handler.annotation.DestinationVariable
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.messaging.handler.annotation.SendTo
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.messaging.simp.annotation.SendToUser
import org.springframework.messaging.simp.annotation.SubscribeMapping
import org.springframework.stereotype.Controller
import java.security.Principal
import java.util.*

@Controller
class RideWebSocketController(
    private val messagingTemplate: SimpMessagingTemplate,
    private val rideService: RideService
) {
    
    private val logger = KotlinLogging.logger {}
    
    /**
     * 운행 상태 구독
     * 클라이언트는 /topic/ride/{rideId} 를 구독하여 운행 상태 업데이트를 받습니다
     */
    @SubscribeMapping("/ride/{rideId}")
    fun subscribeToRide(
        @DestinationVariable rideId: UUID,
        principal: Principal
    ): RideStatusUpdateEventDto {
        logger.debug { "User ${principal.name} subscribed to ride $rideId" }
        
        val ride = rideService.getRide(rideId)
        
        return RideStatusUpdateEventDto(
            rideId = ride.id,
            status = ride.status.name,
            driverId = ride.driverId,
            timestamp = ride.updatedAt,
            message = "Successfully subscribed to ride updates"
        )
    }
    
    /**
     * 드라이버 위치 업데이트
     * 드라이버가 /app/ride/{rideId}/location 으로 위치를 전송합니다
     * 구독자들은 /topic/ride/{rideId}/location 에서 업데이트를 받습니다
     */
    @MessageMapping("/ride/{rideId}/location")
    @SendTo("/topic/ride/{rideId}/location")
    fun updateDriverLocation(
        @DestinationVariable rideId: UUID,
        @Payload locationUpdate: DriverLocationUpdateDto,
        principal: Principal
    ): RideLocationUpdateDto {
        logger.debug { 
            "Driver ${principal.name} updating location for ride $rideId: " +
            "${locationUpdate.latitude}, ${locationUpdate.longitude}"
        }
        
        // 위치 업데이트 검증
        val ride = rideService.getRide(rideId)
        if (ride.driverId?.toString() != principal.name) {
            throw IllegalArgumentException("Driver not authorized for this ride")
        }
        
        return RideLocationUpdateDto(
            rideId = rideId,
            driverId = UUID.fromString(principal.name),
            latitude = locationUpdate.latitude,
            longitude = locationUpdate.longitude,
            heading = locationUpdate.heading,
            speed = locationUpdate.speed,
            accuracy = locationUpdate.accuracy,
            timestamp = locationUpdate.timestamp
        )
    }
    
    /**
     * 드라이버 호출 응답 구독
     * 드라이버는 /user/queue/driver-calls 를 구독하여 호출을 받습니다
     */
    @SubscribeMapping("/queue/driver-calls")
    @SendToUser("/queue/driver-calls")
    fun subscribeToDriverCalls(principal: Principal): Map<String, Any> {
        logger.debug { "Driver ${principal.name} subscribed to driver calls" }
        
        return mapOf(
            "status" to "subscribed",
            "driverId" to principal.name,
            "message" to "Successfully subscribed to driver call notifications"
        )
    }
    
    /**
     * 승객의 운행 업데이트 구독
     * 승객은 /user/queue/ride-updates 를 구독하여 자신의 운행 업데이트를 받습니다
     */
    @SubscribeMapping("/queue/ride-updates")
    @SendToUser("/queue/ride-updates")
    fun subscribeToRideUpdates(principal: Principal): Map<String, Any> {
        logger.debug { "Passenger ${principal.name} subscribed to ride updates" }
        
        return mapOf(
            "status" to "subscribed",
            "passengerId" to principal.name,
            "message" to "Successfully subscribed to ride update notifications"
        )
    }
    
    /**
     * 실시간 메시지 전송
     * 운행 참여자 간 메시지 전송
     */
    @MessageMapping("/ride/{rideId}/message")
    fun sendMessage(
        @DestinationVariable rideId: UUID,
        @Payload message: Map<String, String>,
        principal: Principal
    ) {
        logger.debug { "User ${principal.name} sending message for ride $rideId" }
        
        // 운행 검증
        val ride = rideService.getRide(rideId)
        val isPassenger = ride.passengerId.toString() == principal.name
        val isDriver = ride.driverId?.toString() == principal.name
        
        if (!isPassenger && !isDriver) {
            throw IllegalArgumentException("User not authorized for this ride")
        }
        
        val enhancedMessage = message.toMutableMap()
        enhancedMessage["senderId"] = principal.name
        enhancedMessage["senderType"] = if (isPassenger) "PASSENGER" else "DRIVER"
        enhancedMessage["timestamp"] = System.currentTimeMillis().toString()
        
        // 운행 참여자들에게 메시지 브로드캐스트
        messagingTemplate.convertAndSend("/topic/ride/$rideId/messages", enhancedMessage)
    }
    
    /**
     * 운행 참여자에게 직접 알림 전송
     */
    fun notifyRideParticipants(
        rideId: UUID,
        passengerId: UUID,
        driverId: UUID?,
        notification: Map<String, Any>
    ) {
        // 승객에게 알림
        messagingTemplate.convertAndSendToUser(
            passengerId.toString(),
            "/queue/notifications",
            notification
        )
        
        // 드라이버에게 알림
        driverId?.let {
            messagingTemplate.convertAndSendToUser(
                it.toString(),
                "/queue/notifications",
                notification
            )
        }
        
        // 운행 토픽 구독자들에게도 브로드캐스트
        messagingTemplate.convertAndSend("/topic/ride/$rideId/notifications", notification)
    }
}