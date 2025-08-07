package com.ddakta.location.ws

import com.ddakta.location.dto.LocationUpdateDto
import com.ddakta.location.service.JwtUtil
import com.ddakta.location.service.LocationService
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import io.jsonwebtoken.security.Keys
import org.springframework.http.HttpHeaders
import org.springframework.web.socket.WebSocketHttpHeaders
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.ActiveProfiles
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.client.standard.StandardWebSocketClient
import org.springframework.web.socket.handler.TextWebSocketHandler
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper
import java.net.URI
import java.util.Date
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class LocationWebSocketIntegrationTest {

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var jwtUtil: JwtUtil

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockBean
    private lateinit var locationService: LocationService

    @Test
    fun `웹소켓_핸드쉐이크_및_위치_메시지_처리_테스트`() {
        // 1) JWT 생성 (subject=test-driver, 만료 1분 뒤)
        val secretKey = Keys.hmacShaKeyFor((jwtUtil as JwtUtil).secret.toByteArray())
        val token = Jwts.builder()
            .setSubject("test-driver")
            .setExpiration(Date(System.currentTimeMillis() + 60_000))
            .signWith(secretKey, SignatureAlgorithm.HS256)
            .compact()

        // 2) WebSocket 헤더 준비
        val headers = WebSocketHttpHeaders()
        headers.add(HttpHeaders.AUTHORIZATION, "Bearer $token")

        // 3) 메시지 전송 완료 대기용 latch
        val latch = CountDownLatch(1)

        // 4) 클라이언트 핸들러
        val clientHandler = object : TextWebSocketHandler() {
            override fun afterConnectionEstablished(session: org.springframework.web.socket.WebSocketSession) {
                val dto = LocationUpdateDto(
                    latitude = 37.5665,
                    longitude = 126.9780,
                    timestamp = System.currentTimeMillis()
                )
                session.sendMessage(TextMessage(objectMapper.writeValueAsString(dto)))
            }
            override fun handleTransportError(session: org.springframework.web.socket.WebSocketSession, exception: Throwable) {
                latch.countDown()
            }
        }

        // 5) 실제 WebSocket 연결
        val client = StandardWebSocketClient()
        val handshake = client.doHandshake(clientHandler, headers, URI("ws://localhost:$port/ws/locations"))
        val session = handshake.get(5, TimeUnit.SECONDS)
        assert(session.isOpen)

        // 6) 서비스 호출 검증
        // 잠시만 기다렸다가
        latch.await(500, TimeUnit.MILLISECONDS)
        val captor = ArgumentCaptor.forClass(com.ddakta.location.domain.LocationUpdate::class.java)
        verify(locationService).updateLocation(captor.capture())

        // 7) 페이로드 내용 검사
        val captured = captor.value

        assertEquals("test-driver", captured.driverId)
        assertEquals(37.5665, captured.latitude)
        assertEquals(126.9780, captured.longitude)
    }
}
