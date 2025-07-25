package com.ddakta.auth.support

import com.ddakta.auth.TestContainersConfig
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestContainersConfig::class)
@ActiveProfiles("test")
@TestPropertySource(
    properties = [
        "jwt.secret=test-secret-key-that-is-at-least-32-characters-long",
        "jwt.access-token-validity=900000",
        "jwt.refresh-token-validity=604800000"
    ]
)
@Transactional
abstract class BaseIntegrationTest : TestSupport()