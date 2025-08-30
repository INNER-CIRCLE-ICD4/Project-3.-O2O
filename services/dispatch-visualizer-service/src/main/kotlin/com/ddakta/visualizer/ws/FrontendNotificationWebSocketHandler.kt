package com.ddakta.visualizer.ws

import mu.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.util.concurrent.CopyOnWriteArrayList

@Component
class FrontendNotificationWebSocketHandler : TextWebSocketHandler() {

    private val logger = KotlinLogging.logger {}
    private val sessions = CopyOnWriteArrayList<WebSocketSession>()

    override fun afterConnectionEstablished(session: WebSocketSession) {
        sessions.add(session)
        logger.info { "Frontend WebSocket session established: ${session.id}" }
    }

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        // 프론트엔드로부터 메시지를 받을 일은 없지만, 혹시 모를 경우를 대비
        logger.warn { "Received unexpected message from frontend: ${message.payload}" }
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        sessions.remove(session)
        logger.info { "Frontend WebSocket session closed: ${session.id} with status: $status" }
    }

    override fun handleTransportError(session: WebSocketSession, exception: Throwable) {
        logger.error(exception) { "Frontend WebSocket transport error: ${exception.message}" }
    }

    fun broadcast(message: String) {
        sessions.forEach { session ->
            if (session.isOpen) {
                try {
                    session.sendMessage(TextMessage(message))
                } catch (e: Exception) {
                    logger.error(e) { "Failed to send message to frontend session ${session.id}" }
                }
            }
        }
    }
}
