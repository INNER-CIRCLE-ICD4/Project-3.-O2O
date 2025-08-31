package com.ddakta.visualizer.ws

import mu.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler

@Component
class MatchingServiceWebSocketHandler(
    private val frontendNotificationWebSocketHandler: FrontendNotificationWebSocketHandler
) : TextWebSocketHandler() {

    private val logger = KotlinLogging.logger {}

    override fun afterConnectionEstablished(session: WebSocketSession) {
        logger.info { "Connected to Matching Service WebSocket: ${session.id}" }
    }

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        logger.info { "Received from Matching Service: ${message.payload}" }
        // 매칭 서비스로부터 받은 메시지를 프론트엔드로 브로드캐스트
        frontendNotificationWebSocketHandler.broadcast(message.payload)
    }

    override fun handleTransportError(session: WebSocketSession, exception: Throwable) {
        logger.error(exception) { "Matching Service WebSocket transport error: ${exception.message}" }
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        logger.warn { "Matching Service WebSocket closed: ${status.code} - ${status.reason}" }
    }
}
