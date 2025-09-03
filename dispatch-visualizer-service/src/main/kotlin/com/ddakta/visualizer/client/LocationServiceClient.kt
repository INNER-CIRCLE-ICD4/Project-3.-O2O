package com.ddakta.visualizer.client

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.client.WebSocketConnectionManager
import org.springframework.web.socket.client.standard.StandardWebSocketClient
import org.springframework.web.socket.handler.TextWebSocketHandler

@Component
class LocationServiceClient(
    private val objectMapper: ObjectMapper
) : TextWebSocketHandler() {

    @Value("\${location.service.ws.url}")
    private lateinit var locationServiceWsUrl: String

    private val logger = KotlinLogging.logger {}
    private lateinit var session: WebSocketSession
    private lateinit var connectionManager: WebSocketConnectionManager

    @PostConstruct
    fun connect() {
        val client = StandardWebSocketClient()
        connectionManager = WebSocketConnectionManager(client, this, locationServiceWsUrl)
        connectionManager.setAutoStartup(true) // 애플리케이션 시작 시 자동 연결
        connectionManager.start()
        logger.info { "Connecting to Location Service WebSocket at $locationServiceWsUrl" }
    }

    override fun afterConnectionEstablished(session: WebSocketSession) {
        this.session = session
        logger.info { "Connected to Location Service WebSocket: ${session.id}" }
    }

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        logger.debug { "Received message from Location Service: ${message.payload}" }
    }

    override fun handleTransportError(session: WebSocketSession, exception: Throwable) {
        logger.error(exception) { "Location Service WebSocket transport error: ${exception.message}" }
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        logger.warn { "Location Service WebSocket closed: ${status.code} - ${status.reason}" }
        // 재연결 로직 추가 가능
    }

    @PreDestroy
    fun disconnect() {
        if (::connectionManager.isInitialized && connectionManager.isRunning) {
            connectionManager.stop()
            logger.info { "Disconnected from Location Service WebSocket" }
        }
    }

    fun sendLocationUpdate(driverId: String, latitude: Double, longitude: Double) {
        if (::session.isInitialized && session.isOpen) {
            val messagePayload = objectMapper.createObjectNode().apply {
                put("driverId", driverId)
                put("latitude", latitude)
                put("longitude", longitude)
                put("timestamp", System.currentTimeMillis())
            }.toString()
            session.sendMessage(TextMessage(messagePayload))
            logger.debug { "Sent location update for driver $driverId" }
        } else {
            logger.warn { "Location Service WebSocket session not open. Cannot send update for driver $driverId" }
        }
    }
}

