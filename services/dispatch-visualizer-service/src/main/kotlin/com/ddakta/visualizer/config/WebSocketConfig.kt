package com.ddakta.visualizer.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestTemplate
import org.springframework.web.socket.client.WebSocketConnectionManager
import org.springframework.web.socket.client.standard.StandardWebSocketClient
import org.springframework.web.socket.handler.TextWebSocketHandler

@Configuration
class WebSocketConfig {

    // WebSocket 클라이언트 (matching-service의 /ws/rides 엔드포인트 연결용)
    @Bean
    fun matchingServiceWebSocketClient(): StandardWebSocketClient {
        return StandardWebSocketClient()
    }

    @Bean
    fun matchingServiceWebSocketConnectionManager(
        matchingServiceWebSocketClient: StandardWebSocketClient,
        matchingServiceWebSocketHandler: TextWebSocketHandler // 이 핸들러는 나중에 구현
    ): WebSocketConnectionManager {
        // TODO: matching-service의 실제 WebSocket URL로 변경 필요
        val uri = "ws://localhost:8082/ws/rides" 
        val connectionManager = WebSocketConnectionManager(
            matchingServiceWebSocketClient,
            matchingServiceWebSocketHandler,
            uri
        )
        connectionManager.setAutoStartup(true) // 애플리케이션 시작 시 자동 연결
        return connectionManager
    }

    // matching-service로부터 메시지를 받을 핸들러 (나중에 구현)
    // @Bean
    // fun matchingServiceWebSocketHandler(): TextWebSocketHandler {
    //     return object : TextWebSocketHandler() {
    //         override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
    //             // matching-service로부터 받은 메시지 처리 로직
    //             println("Received from matching-service: ${message.payload}")
    //         }
    //     }
    // }

    @Bean
    fun restTemplate(): RestTemplate {
        return RestTemplate()
    }
}
