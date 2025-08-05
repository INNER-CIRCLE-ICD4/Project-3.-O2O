package com.ddakta.matching.config

import io.swagger.v3.oas.annotations.OpenAPIDefinition
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType
import io.swagger.v3.oas.annotations.info.Contact
import io.swagger.v3.oas.annotations.info.Info
import io.swagger.v3.oas.annotations.info.License
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.security.SecurityScheme
import io.swagger.v3.oas.annotations.servers.Server
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.examples.Example
import io.swagger.v3.oas.models.media.Content
import io.swagger.v3.oas.models.media.MediaType
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.responses.ApiResponse
import io.swagger.v3.oas.models.responses.ApiResponses
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@OpenAPIDefinition(
    info = Info(
        title = "DDAKTA Matching Service API",
        version = "v1",
        description = """
            # DDAKTA 매칭 서비스 API
            
            승객과 드라이버를 연결하는 실시간 매칭 서비스 API입니다.
            
            ## 주요 기능
            - **운행 관리**: 운행 요청, 조회, 취소, 상태 업데이트
            - **드라이버 호출**: 드라이버 호출 수락/거절, 통계 조회
            - **실시간 추적**: WebSocket을 통한 실시간 위치 추적
            - **매칭 알고리즘**: Hungarian 알고리즘 기반 최적 매칭
            
            ## API 버전 관리
            - 현재 버전: v1
            - 버전 형식: /api/v{version}/
            - 하위 호환성 보장
            
            ## 인증
            - 승객: X-User-Id 헤더
            - 드라이버: X-Driver-Id 헤더
            - 관리자: Bearer Token (JWT)
            
            ## Rate Limiting
            - 일반 API: 100 requests/minute per IP
            - 위치 업데이트: 10 requests/second per driver
            
            ## 에러 코드
            | 코드 | 설명 |
            |------|------|
            | RIDE_NOT_FOUND | 운행을 찾을 수 없음 |
            | DUPLICATE_RIDE_REQUEST | 중복된 운행 요청 |
            | INVALID_RIDE_STATE | 잘못된 운행 상태 |
            | DRIVER_CALL_NOT_FOUND | 호출을 찾을 수 없음 |
            | DRIVER_CALL_EXPIRED | 호출 시간 만료 |
            | NO_AVAILABLE_DRIVER | 이용 가능한 드라이버 없음 |
            | SERVICE_UNAVAILABLE | 서비스 일시 중단 |
            
            ## 문의
            - 기술 지원: tech@ddakta.com
            - API 키 발급: api@ddakta.com
        """,
        contact = Contact(
            name = "DDAKTA API Support",
            email = "api@ddakta.com",
            url = "https://api.ddakta.com/support"
        ),
        license = License(
            name = "Proprietary",
            url = "https://ddakta.com/terms"
        )
    ),
    servers = [
        Server(url = "http://localhost:8080", description = "Local Development"),
        Server(url = "https://api-dev.ddakta.com", description = "Development"),
        Server(url = "https://api-staging.ddakta.com", description = "Staging"),
        Server(url = "https://api.ddakta.com", description = "Production")
    ],
    security = [SecurityRequirement(name = "bearerAuth")]
)
@SecurityScheme(
    name = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    bearerFormat = "JWT",
    scheme = "bearer"
)
class OpenApiConfig {
    
    @Bean
    fun customOpenAPI(): OpenAPI {
        return OpenAPI()
            .components(
                Components()
                    .addResponses("BadRequest", createBadRequestResponse())
                    .addResponses("Unauthorized", createUnauthorizedResponse())
                    .addResponses("Forbidden", createForbiddenResponse())
                    .addResponses("NotFound", createNotFoundResponse())
                    .addResponses("Conflict", createConflictResponse())
                    .addResponses("InternalServerError", createInternalServerErrorResponse())
                    .addResponses("ServiceUnavailable", createServiceUnavailableResponse())
            )
    }
    
    private fun createBadRequestResponse(): ApiResponse {
        return ApiResponse()
            .description("잘못된 요청")
            .content(
                Content()
                    .addMediaType(
                        "application/json",
                        MediaType()
                            .addExamples(
                                "ValidationError",
                                Example()
                                    .value(mapOf(
                                        "success" to false,
                                        "code" to "VALIDATION_ERROR",
                                        "message" to "요청 데이터가 유효하지 않습니다",
                                        "errors" to listOf(
                                            mapOf(
                                                "field" to "pickupLocation.latitude",
                                                "message" to "위도는 -90에서 90 사이여야 합니다"
                                            )
                                        )
                                    ))
                            )
                    )
            )
    }
    
    private fun createUnauthorizedResponse(): ApiResponse {
        return ApiResponse()
            .description("인증 실패")
            .content(
                Content()
                    .addMediaType(
                        "application/json",
                        MediaType()
                            .addExamples(
                                "Unauthorized",
                                Example()
                                    .value(mapOf(
                                        "success" to false,
                                        "code" to "UNAUTHORIZED",
                                        "message" to "인증이 필요합니다"
                                    ))
                            )
                    )
            )
    }
    
    private fun createForbiddenResponse(): ApiResponse {
        return ApiResponse()
            .description("권한 없음")
            .content(
                Content()
                    .addMediaType(
                        "application/json",
                        MediaType()
                            .addExamples(
                                "Forbidden",
                                Example()
                                    .value(mapOf(
                                        "success" to false,
                                        "code" to "FORBIDDEN",
                                        "message" to "해당 리소스에 접근할 권한이 없습니다"
                                    ))
                            )
                    )
            )
    }
    
    private fun createNotFoundResponse(): ApiResponse {
        return ApiResponse()
            .description("리소스를 찾을 수 없음")
            .content(
                Content()
                    .addMediaType(
                        "application/json",
                        MediaType()
                            .addExamples(
                                "NotFound",
                                Example()
                                    .value(mapOf(
                                        "success" to false,
                                        "code" to "RESOURCE_NOT_FOUND",
                                        "message" to "요청한 리소스를 찾을 수 없습니다"
                                    ))
                            )
                    )
            )
    }
    
    private fun createConflictResponse(): ApiResponse {
        return ApiResponse()
            .description("충돌 발생")
            .content(
                Content()
                    .addMediaType(
                        "application/json",
                        MediaType()
                            .addExamples(
                                "Conflict",
                                Example()
                                    .value(mapOf(
                                        "success" to false,
                                        "code" to "CONFLICT",
                                        "message" to "요청이 현재 리소스 상태와 충돌합니다"
                                    ))
                            )
                    )
            )
    }
    
    private fun createInternalServerErrorResponse(): ApiResponse {
        return ApiResponse()
            .description("서버 내부 오류")
            .content(
                Content()
                    .addMediaType(
                        "application/json",
                        MediaType()
                            .addExamples(
                                "InternalServerError",
                                Example()
                                    .value(mapOf(
                                        "success" to false,
                                        "code" to "INTERNAL_SERVER_ERROR",
                                        "message" to "서버 내부 오류가 발생했습니다"
                                    ))
                            )
                    )
            )
    }
    
    private fun createServiceUnavailableResponse(): ApiResponse {
        return ApiResponse()
            .description("서비스 일시 중단")
            .content(
                Content()
                    .addMediaType(
                        "application/json",
                        MediaType()
                            .addExamples(
                                "ServiceUnavailable",
                                Example()
                                    .value(mapOf(
                                        "success" to false,
                                        "code" to "SERVICE_UNAVAILABLE",
                                        "message" to "서비스가 일시적으로 사용할 수 없습니다. 잠시 후 다시 시도해주세요"
                                    ))
                            )
                    )
            )
    }
}