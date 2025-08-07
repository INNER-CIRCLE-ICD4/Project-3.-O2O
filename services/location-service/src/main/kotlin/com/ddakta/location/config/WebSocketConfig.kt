package com.ddakta.location.config

import com.ddakta.location.ws.LocationWebSocketHandler
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry

@Configuration
@EnableWebSocket
class WebSocketConfig(
    private val handler: LocationWebSocketHandler,
    private val interceptor: JwtHandshakeInterceptor,
    @Value("\${app.ws.allowed-origins}") private val allowedOrigins: String
) : WebSocketConfigurer {
    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        registry
            .addHandler(handler, "/ws/locations")
            .addInterceptors(interceptor)
            .setAllowedOrigins(*allowedOrigins.split(",").map(String::trim).toTypedArray())
    }
}
