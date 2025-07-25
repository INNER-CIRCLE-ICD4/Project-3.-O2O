package com.ddakta.auth.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {
    
    @Bean
    fun openAPI(): OpenAPI {
        val securitySchemeName = "bearerAuth"
        
        return OpenAPI()
            .info(
                Info()
                    .title("O2O 차량 호출 서비스 - 인증 API")
                    .version("1.0.0")
                    .description("인증 서비스 API 문서")
            )
            .addSecurityItem(SecurityRequirement().addList(securitySchemeName))
            .components(
                Components()
                    .addSecuritySchemes(
                        securitySchemeName,
                        SecurityScheme()
                            .name(securitySchemeName)
                            .type(SecurityScheme.Type.HTTP)
                            .scheme("bearer")
                            .bearerFormat("JWT")
                    )
            )
    }
}