package com.ddakta.location.ws

import com.ddakta.location.dto.LocationUpdateDto
import com.ddakta.location.domain.LocationUpdate
import com.ddakta.location.exception.ForbiddenException
import com.ddakta.location.service.LocationService
import com.ddakta.utils.security.AuthenticationPrincipal
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.stereotype.Component
import org.springframework.validation.BeanPropertyBindingResult
import org.springframework.validation.ValidationUtils
import org.springframework.validation.Validator
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler

@Component
class LocationWebSocketHandler(
    private val service: LocationService,
    private val validator: Validator
) : TextWebSocketHandler() {
    private val mapper = jacksonObjectMapper()

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        val dto = mapper.readValue(message.payload, LocationUpdateDto::class.java)
        val errors = BeanPropertyBindingResult(dto, dto::class.java.name)
        ValidationUtils.invokeValidator(validator, dto, errors)
        if (errors.hasErrors()) {
            session.close(CloseStatus.BAD_DATA.withReason("Invalid data"))
            return
        }

        // 세션에 저장된 principal 정보 꺼내기
        val principal = session.attributes["principal"] as? AuthenticationPrincipal
            ?: throw ForbiddenException("WebSocket 연결에 사용자 정보가 없습니다")
        val driverId = principal.userId.toString()

        val update = LocationUpdate(driverId, dto.latitude!!, dto.longitude!!, dto.timestamp!!)
        service.updateLocation(update)
    }
}
