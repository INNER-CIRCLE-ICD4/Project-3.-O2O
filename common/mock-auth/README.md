# Commons Mock Auth 모듈

개발 및 테스트 환경을 위한 Mock 인증 모듈입니다.

## 개요

이 모듈은 개발 목적으로 실제 OAuth 플로우를 우회하는 Mock 인증 엔드포인트를 제공합니다. 역할별 엔드포인트를 통해 테스트 사용자를 동적으로 생성할 수 있습니다.

### 설정 방법

서비스의 `application-local.yml`에서 활성화:
```yaml
test:
  auth:
    enabled: true
```

### API 엔드포인트

#### 승객(Passenger)으로 로그인
```bash
POST /api/test/auth/passenger/{userId}

# 예시: ID가 "john"인 테스트 승객 생성/로그인
curl -X POST http://localhost:8080/api/test/auth/passenger/john
```

#### 드라이버(Driver)로 로그인
```bash
POST /api/test/auth/driver/{userId}

# 예시: ID가 "mary"인 테스트 드라이버 생성/로그인
curl -X POST http://localhost:8080/api/test/auth/driver/mary
```

#### 관리자(Admin)로 로그인
```bash
POST /api/test/auth/admin/{userId}

# 예시: ID가 "admin1"인 테스트 관리자 생성/로그인
curl -X POST http://localhost:8080/api/test/auth/admin/admin1
```

### 응답 형식
```json
{
  "accessToken": "eyJhbGciOiJIUzUxMiJ9...",
  "refreshToken": "eyJhbGciOiJIUzUxMiJ9...",
  "user": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "email": "test.passenger.john@example.com",
    "name": "Test Passenger john",
    "role": "PASSENGER"
  }
}
```

### 동적 사용자 생성

- 어떤 `userId`든 입력하면 해당 사용자가 없을 경우 새로 생성됩니다
- 예측 가능한 이메일 형식으로 생성: `test.{role}.{userId}@example.com`
- 기존 JWT 인프라와 완벽하게 통합됩니다
- 개발 환경에서만 사용 가능 (프로덕션에서는 절대 사용 불가)

### 테스트에서 사용하기

```kotlin
// 통합 테스트에서
val response = mockMvc.perform(
    post("/api/test/auth/passenger/testuser123")
).andReturn()

val authResult = objectMapper.readValue(
    response.response.contentAsString,
    MockAuthService.TestAuthResult::class.java
)

// 인증된 요청에 액세스 토큰 사용
mockMvc.perform(
    get("/api/some-endpoint")
        .header("Authorization", "Bearer ${authResult.accessToken}")
)
```

### 서비스에 추가하기

1. `build.gradle.kts`에 의존성 추가:
```kotlin
dependencies {
    implementation(project(":common:mock-auth"))
}
```

2. `application-local.yml`에서 테스트 인증 활성화:
```yaml
test:
  auth:
    enabled: true
```

3. `MockAuthService` 구현 (auth-service에서만 필요)

## 보안 경고

⚠️ **절대로 프로덕션에서 `test.auth.enabled=true`를 활성화하지 마세요!**
- 실제 인증을 완전히 우회합니다
- 테스트 사용자는 실제 자격 증명이 없습니다
- 개발 및 테스트 환경에서만 사용하세요

## 테스트 예제

### 단위 테스트
```kotlin
@SpringBootTest
@AutoConfigureMockMvc
class AuthIntegrationTest {
    
    @Autowired
    lateinit var mockMvc: MockMvc
    
    @Test
    fun `승객으로 로그인 테스트`() {
        mockMvc.perform(post("/api/test/auth/passenger/test123"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accessToken").exists())
            .andExpect(jsonPath("$.user.role").value("PASSENGER"))
    }
    
    @Test
    fun `드라이버로 로그인 테스트`() {
        mockMvc.perform(post("/api/test/auth/driver/driver123"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accessToken").exists())
            .andExpect(jsonPath("$.user.role").value("DRIVER"))
    }
}
```

### 수동 테스트
```bash
# 1. auth-service 실행
./gradlew :services:auth-service:bootRun

# 2. 승객 로그인 테스트
curl -X POST http://localhost:8080/api/test/auth/passenger/test1

# 3. 반환된 accessToken으로 인증된 API 호출
curl -H "Authorization: Bearer {accessToken}" http://localhost:8080/api/auth/me
```