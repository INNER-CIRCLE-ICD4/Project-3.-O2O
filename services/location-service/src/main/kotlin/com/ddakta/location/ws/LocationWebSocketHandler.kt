package com.ddakta.location.ws

import com.ddakta.location.service.LocationService
import com.fasterxml.jackson.databind.ObjectMapper
import com.uber.h3core.H3Core
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.util.concurrent.ConcurrentHashMap

@Component
class LocationWebSocketHandler(
    private val locationService: LocationService,
    private val objectMapper: ObjectMapper
) : TextWebSocketHandler() {

    private val logger = LoggerFactory.getLogger(this::class.java)
    private val sessions = ConcurrentHashMap<String, WebSocketSession>()
    private val h3 = H3Core.newInstance()

    override fun afterConnectionEstablished(session: WebSocketSession) {
        val driverId = session.attributes["driverId"] as? String
        if (driverId == null) {
            logger.warn ( "WebSocket session established without driverId. Closing session." )
            session.close(CloseStatus.BAD_DATA)
            return
        }
        sessions[driverId] = session
        logger.info ( "WebSocket session established for driver: $driverId" )
        // 드라이버 상태를 ONLINE으로 업데이트 (Redis에 저장)
        locationService.updateDriverStatus(driverId, "ONLINE")
    }

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        val driverId = session.attributes["driverId"] as? String
        if (driverId == null) {
            logger.warn ( "Received message from session without driverId. Closing session." )
            session.close(CloseStatus.BAD_DATA)
            return
        }

        try {
            val locationData = objectMapper.readTree(message.payload)
            val latitude = locationData.get("latitude").asDouble()
            val longitude = locationData.get("longitude").asDouble()
            val timestamp = locationData.get("timestamp").asLong()

            // H3 인덱스 계산
                        val h3Index = h3.latLngToCellAddress(latitude, longitude, 9) // 해상도 9

            locationService.updateLocation(
                driverId = driverId,
                latitude = latitude,
                longitude = longitude,
                h3Index = h3Index,
                timestamp = timestamp
            )
            logger.debug("Location updated for driver {}: {}, {} (H3: {})", driverId, latitude, longitude, h3Index)

        } catch (e: Exception) {
            logger.error ("Error processing location update for driver $driverId: ${e.message}" )
            session.sendMessage(TextMessage("Error processing location update"))
        }
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        val driverId = session.attributes["driverId"] as? String
        if (driverId != null) {
            sessions.remove(driverId)
            logger.info ("WebSocket session closed for driver: $driverId with status: $status" )
            // 드라이버 상태를 OFFLINE으로 업데이트 (Redis에 저장)
            locationService.updateDriverStatus(driverId, "OFFLINE")
        }
    }

    override fun handleTransportError(session: WebSocketSession, exception: Throwable) {
        val driverId = session.attributes["driverId"] as? String
        logger.error ("WebSocket transport error for driver $driverId: ${exception.message}" )
    }
}
