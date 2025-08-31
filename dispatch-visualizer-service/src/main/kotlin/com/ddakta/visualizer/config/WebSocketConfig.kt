package com.ddakta.visualizer.config

import com.ddakta.visualizer.ws.FrontendNotificationWebSocketHandler
import com.ddakta.visualizer.ws.MatchingServiceWebSocketHandler
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestTemplate
import org.springframework.web.socket.client.WebSocketConnectionManager
import org.springframework.web.socket.client.standard.StandardWebSocketClient
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry
import org.springframework.web.socket.handler.TextWebSocketHandler

@Configuration
@EnableWebSocket
class WebSocketConfig(
    private val frontendNotificationWebSocketHandler: FrontendNotificationWebSocketHandler,
    private val matchingServiceWebSocketHandler: MatchingServiceWebSocketHandler
) : WebSocketConfigurer {

    @Bean
    fun matchingServiceWebSocketClient(): StandardWebSocketClient {
        return StandardWebSocketClient()
    }

    @Bean
    fun matchingServiceWebSocketConnectionManager(
        matchingServiceWebSocketClient: StandardWebSocketClient
    ): WebSocketConnectionManager {
        val uri = "ws://localhost:8082/ws/rides"
        val connectionManager = WebSocketConnectionManager(
            matchingServiceWebSocketClient,
            matchingServiceWebSocketHandler,
            uri
        )
        connectionManager.isAutoStartup = true
        return connectionManager
    }

    

    @Bean
    fun restTemplate(): RestTemplate {
        return RestTemplate()
    }

    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        registry.addHandler(frontendNotificationWebSocketHandler, "/ws/notifications")
            .setAllowedOrigins("*")
    }
}