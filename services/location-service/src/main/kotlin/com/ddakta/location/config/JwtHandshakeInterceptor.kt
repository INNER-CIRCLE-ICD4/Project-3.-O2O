package com.ddakta.location.config

import com.ddakta.location.exception.ForbiddenException
import com.ddakta.location.service.JwtUtil
import com.ddakta.utils.security.AuthenticationPrincipal
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse
import org.springframework.stereotype.Component
import org.springframework.web.socket.WebSocketHandler
import org.springframework.web.socket.server.HandshakeInterceptor

@Component
class JwtHandshakeInterceptor(
    private val jwtUtil: JwtUtil
) : HandshakeInterceptor {

    override fun beforeHandshake(
        request: ServerHttpRequest,
        response: ServerHttpResponse,
        wsHandler: WebSocketHandler,
        attributes: MutableMap<String, Any>
    ): Boolean {
        val authHdr = request.headers.getFirst("Authorization")
            ?: throw ForbiddenException("Missing Authorization")
        val token = authHdr.removePrefix("Bearer ").trim()

        // 공통 JwtUtil로 토큰 검증
        if (!jwtUtil.validateToken(token)) throw ForbiddenException("Invalid JWT")

        // 공통 JwtUtil이 반환하는 principal
        val principal: AuthenticationPrincipal = jwtUtil.parsePrincipal(token)

        // 토큰 안에 role이 DRIVER가 아니라면 연결 거절
        if (!principal.isDriver()) {
            throw ForbiddenException("Only drivers allowed")
        }

        // WebSocket 세션에 principal 전체를 담아 둔다
        attributes["principal"] = principal
        return true
    }

    override fun afterHandshake(
        request: ServerHttpRequest, response: ServerHttpResponse,
        wsHandler: WebSocketHandler, exception: Exception?
    ) { }


}
