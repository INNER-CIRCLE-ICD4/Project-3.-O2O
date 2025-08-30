package com.ddakta.visualizer.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestTemplate
import org.springframework.web.socket.client.WebSocketConnectionManager
import org.springframework.web.socket.client.standard.StandardWebSocketClient
import org.springframework.web.socket.handler.TextWebSocketHandler

package com.ddakta.visualizer.config

import com.ddakta.visualizer.ws.FrontendNotificationWebSocketHandler
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestTemplate
import org.springframework.web.socket.client.WebSocketConnectionManager
import org.springframework.web.socket.client.standard.StandardWebSocketClient
import org.springframework.web.socket.handler.TextWebSocketHandler
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry

@Configuration
@EnableWebSocket
class WebSocketConfig(
    private val frontendNotificationWebSocketHandler: FrontendNotificationWebSocketHandler
) : WebSocketConfigurer {

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

    @Bean
    fun matchingServiceWebSocketHandler(): TextWebSocketHandler {
        return MatchingServiceWebSocketHandler()
    }

    @Bean
    fun restTemplate(): RestTemplate {
        return RestTemplate()
    }

    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        registry.addHandler(frontendNotificationWebSocketHandler, "/ws/notifications")
            .setAllowedOrigins("*") // 개발 환경에서 모든 Origin 허용
    }
}
