# Common User Client Module

O2O 라이드 서비스의 마이크로서비스 간 유저 정보 공유를 위한 공통 클라이언트 모듈입니다.

## 주요 기능

- **자동 유저 정보 주입**: `@WithUserInfo` 어노테이션으로 컨트롤러에서 자동 유저 정보 주입
- **분산 캐싱**: Redis + Local Cache를 통한 고성능 캐싱
- **실시간 동기화**: Kafka 이벤트 기반 유저 정보 실시간 업데이트
- **장애 격리**: Circuit Breaker 패턴으로 auth-service 장애 시 격리
- **자동 설정**: Spring Boot Auto Configuration으로 의존성 추가만으로 사용 가능

## 사용 방법

### 1. 의존성 추가

```kotlin
// build.gradle.kts
dependencies {
    implementation(project(":common-user-client"))
}
```

### 2. 설정

```yaml
# application.yml
ddakta:
  user-client:
    enabled: true
  auth-service:
    url: http://auth-service:8080
  jwt:
    secret: ${JWT_SECRET}

spring:
  redis:
    host: ${REDIS_HOST:localhost}
    port: ${REDIS_PORT:6379}
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
```

### 3. 컨트롤러에서 사용

```kotlin
@RestController
@RequestMapping("/api/v1/rides")
class RideController {
    
    @PostMapping
    fun createRide(
        @WithUserInfo passenger: PassengerInfo,  // 자동 주입
        @RequestBody request: CreateRideRequest
    ): RideResponse {
        println("${passenger.getDisplayName()}의 운행 요청")
        // 비즈니스 로직
    }
    
    @GetMapping("/{id}")
    fun getRide(
        @PathVariable id: UUID,
        @WithUserInfo(required = false) user: UserInfo?  // 선택적 주입
    ): RideResponse {
        // 비즈니스 로직
    }
}
```

### 4. 서비스에서 직접 사용

```kotlin
@Service
class YourService(
    private val userCacheService: UserCacheService
) {
    
    fun processWithUser(userId: UUID) {
        val userInfo = userCacheService.getUserInfo(userId)
            ?: throw UserNotFoundException(userId)
            
        when (userInfo) {
            is RiderInfo -> {
                // 라이더 전용 로직
                if (userInfo.isAvailable()) {
                    // 운행 가능 상태
                }
            }
            is PassengerInfo -> {
                // 승객 전용 로직
                if (userInfo.canRequestRide()) {
                    // 운행 요청 가능
                }
            }
        }
    }
}
```

## 아키텍처

### 캐싱 전략

1. **Local Cache (Caffeine)**
   - TTL: 1분
   - 최대 크기: 10,000개
   - 히트율 목표: >95%

2. **Redis Cache**
   - TTL: 5분
   - 분산 환경 공유
   - 장애 시 fallback

3. **Auth Service**
   - Circuit Breaker 적용
   - 실패율 50% 이상 시 차단
   - 30초 후 재시도

### 이벤트 처리

지원하는 이벤트:
- `UserCreatedEvent`: 유저 생성
- `UserUpdatedEvent`: 유저 정보 업데이트
- `UserDeletedEvent`: 유저 삭제
- `RiderStatusChangedEvent`: 라이더 상태 변경 (온라인/오프라인)
- `RiderLocationUpdatedEvent`: 라이더 위치 업데이트

### 성능 지표

- 유저 정보 조회: <50ms (캐시 히트 시 <5ms)
- 캐시 히트율: >95%
- 이벤트 지연시간: <100ms
- 동시 처리: 1,000 TPS

## 모니터링

### 캐시 통계 확인

```kotlin
@RestController
class MonitoringController(
    private val userCacheService: UserCacheService
) {
    
    @GetMapping("/admin/cache-stats")
    fun getCacheStats() = userCacheService.getCacheStats()
}
```

### Circuit Breaker 상태

```kotlin
// actuator endpoint
GET /actuator/health
GET /actuator/circuitbreakers
GET /actuator/circuitbreakerevents
```

## 문제 해결

### Redis 연결 실패
- 로컬 캐시만으로 동작
- Circuit Breaker가 auth-service 직접 호출 차단

### Kafka 연결 실패
- 이벤트 수신 불가로 실시간 업데이트 지연
- 캐시 TTL에 의존하여 최종 일관성 보장

### Auth Service 장애
- Circuit Breaker Open 상태
- 캐시된 데이터만 제공
- 30초 후 자동 재시도