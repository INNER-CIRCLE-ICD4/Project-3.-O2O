package com.ddakta.payment.service

import com.ddakta.payment.event.DriveEndEvent
import com.ddakta.payment.event.PaymentEventListener
import com.ddakta.payment.event.PaymentEventProvider
import com.ddakta.payment.repository.PaymentRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.timeout
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.util.concurrent.TimeUnit

@SpringBootTest
@TestPropertySource(properties = [
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration"
])
@ActiveProfiles("test")
@Testcontainers
class PaymentEventConsumerTest {

    companion object {
        @Container
        val kafkaContainer = KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.3.0"))
        @JvmStatic
        @DynamicPropertySource
        fun overrideProps(registry: DynamicPropertyRegistry) {
            registry.add("spring.kafka.bootstrap-servers") { kafkaContainer.bootstrapServers }
        }
    }

    @Autowired
    private lateinit var kafkaTemplate: KafkaTemplate<String, String>

    @MockBean
    private lateinit var paymentService: PaymentService

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @SpyBean
    private lateinit var paymentEventListener: PaymentEventListener

    private lateinit var fakeEventProducer: FakeEventProducer

    @BeforeEach
    fun setup() {
        paymentEventListener = PaymentEventListener(objectMapper, paymentService)
        fakeEventProducer = FakeEventProducer(kafkaTemplate, objectMapper)
    }

    val event = DriveEndEvent(
        matchId = 1234L,
        userId = "test",
        amount = 10000,
        payMethod = "CARD"
    )

    val invalidEvent = DriveEndEvent(
        matchId = 1234L,
        userId = "Invalid_Event",
        amount = 0,
        payMethod = "CARD"
    )

    @Test
    @DisplayName("정상적인 결제 이벤트 처리 테스트")
    fun test01() {

        // when
        fakeEventProducer.sendDriveEndEvent(event).get(5, TimeUnit.SECONDS)

        // then
        verify(paymentService, timeout(5000)).executePayment(event)
    }

    @Test
    @DisplayName("여러 건의 결제 이벤트 순차 처리 테스트")
    fun test02() {
        // given
        val events = listOf(
            DriveEndEvent(1L, "user_id_uuid_1", 5000, "CARD"),
            DriveEndEvent(2L, "user_id_uuid_2", 7000, "CARD"),
            DriveEndEvent(3L, "user_id_uuid_3", 3000, "CARD")
        )

        // when
        events.forEach { event ->
            fakeEventProducer.sendDriveEndEvent(event).get(5, TimeUnit.SECONDS)
        }

        // then
        val captor = argumentCaptor<DriveEndEvent>()
        verify(paymentService, timeout(5000).times(3)).executePayment(captor.capture())

        val receivedEvents = captor.allValues
        assertThat(receivedEvents).containsExactlyInAnyOrderElementsOf(events)
    }
}
