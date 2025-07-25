# Auth Service

O2O 플랫폼의 인증 및 권한 관리를 담당하는 마이크로서비스입니다.

## 목차

- [주요 기능](#주요-기능)
- [기술 스택](#기술-스택)
- [프로젝트 구조](#프로젝트-구조)
- [시작하기](#시작하기)
- [OAuth 설정 가이드](#oauth-설정-가이드)
  - [Google OAuth 설정](#google-oauth-설정)
  - [Apple OAuth 설정](#apple-oauth-설정)
- [API 엔드포인트](#api-엔드포인트)
- [테스트](#테스트)
- [환경 설정](#환경-설정)
- [보안 고려사항](#보안-고려사항)

## 주요 기능

- **OAuth 2.0 인증**: Google, Apple 소셜 로그인 지원
- **JWT 토큰 관리**: Access Token 및 Refresh Token 발급/갱신
- **자동 회원가입**: OAuth 최초 로그인 시 자동 회원가입
- **역할 기반 권한**: PASSENGER(승객), DRIVER(기사) 역할 관리
- **토큰 세션 관리**: Redis를 사용한 토큰 세션 관리
- **보안**: Spring Security 기반 인증/인가

## 기술 스택

- **Language**: Kotlin
- **Framework**: Spring Boot 3.2.0
- **Security**: Spring Security, JWT (jjwt 0.11.5)
- **Database**: PostgreSQL (JPA/Hibernate)
- **Cache**: Redis (Spring Data Redis)
- **Migration**: Flyway
- **API Docs**: SpringDoc OpenAPI (Swagger)
- **Test**: JUnit 5, TestContainers, Mockito

## 프로젝트 구조

```
src/main/kotlin/com/ddakta/auth/
├── common/
│   └── annotation/
│       └── CurrentUser.kt          # 현재 사용자 정보 주입 어노테이션
├── config/
│   ├── CurrentUserArgumentResolver.kt
│   ├── JwtAuthenticationEntryPoint.kt
│   ├── JwtAuthenticationFilter.kt
│   ├── JwtProperties.kt
│   ├── OAuth2SuccessHandler.kt    # OAuth 인증 성공 핸들러
│   ├── OpenApiConfig.kt
│   ├── RedisConfig.kt
│   ├── SecurityConfig.kt
│   └── WebMvcConfig.kt
├── controller/
│   └── AuthController.kt           # 인증 API 컨트롤러
├── domain/
│   ├── entity/
│   │   └── User.kt                # 사용자 엔티티
│   ├── enum/
│   │   └── AuthProvider.kt        # OAuth 프로바이더 (GOOGLE, APPLE)
│   ├── model/
│   │   └── TokenSession.kt        # Redis 토큰 세션 모델
│   └── repository/
│       ├── TokenSessionRepository.kt
│       └── UserRepository.kt
├── dto/
│   ├── AuthResponse.kt
│   ├── RefreshTokenRequest.kt
│   └── UserInfo.kt
├── security/
│   └── AuthenticationPrincipal.kt # 인증 주체 정보
└── service/
    ├── AuthService.kt             # 인증 비즈니스 로직
    └── JwtTokenProvider.kt        # JWT 토큰 생성/검증
```

## 시작하기

### 사전 요구사항

- JDK 17 이상
- Docker & Docker Compose
- Gradle 8.5

### 환경 설정

#### Docker 컨테이너 실행
```bash
cd services/auth-service
docker-compose up -d
```

이 명령어는 다음 서비스들을 실행합니다:
- PostgreSQL (포트: 5432)
- Redis (포트: 6379)

### 애플리케이션 실행

```bash
./gradlew :services:auth-service:bootRun
```

### API 문서 확인

애플리케이션 실행 후 다음 URL에서 Swagger UI를 확인할 수 있습니다:
- http://localhost:8080/swagger-ui.html

## OAuth 설정 가이드

### Google OAuth 설정

#### 1. Google Cloud Console 설정

1. [Google Cloud Console](https://console.cloud.google.com/) 접속
2. 프로젝트 생성 또는 선택
3. **API 및 서비스** → **사용자 인증 정보** 이동

#### 2. OAuth 2.0 클라이언트 ID 생성

1. **사용자 인증 정보 만들기** → **OAuth 클라이언트 ID** 선택
2. 애플리케이션 유형: **웹 애플리케이션** 선택
3. 설정 입력:
   - 이름: `O2O Auth Service`
   - 승인된 JavaScript 원본: `http://localhost:8080`
   - 승인된 리디렉션 URI: 
     - `http://localhost:8080/login/oauth2/code/google` (개발)
     - `https://your-domain.com/login/oauth2/code/google` (프로덕션)
4. 생성 후 **Client ID**와 **Client Secret** 저장

#### 3. application.yml 설정

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          google:
            client-id: ${GOOGLE_CLIENT_ID}
            client-secret: ${GOOGLE_CLIENT_SECRET}
            redirect-uri: "{baseUrl}/login/oauth2/code/{registrationId}"
            scope:
              - email
              - profile
```

### Apple OAuth 설정

#### 1. Apple Developer 계정 설정

1. [Apple Developer](https://developer.apple.com/) 접속
2. **Certificates, Identifiers & Profiles** 이동

#### 2. App ID 생성

1. **Identifiers** → **+** 버튼 클릭
2. **App IDs** 선택 → **Continue**
3. 설정 입력:
   - Description: `O2O Auth Service`
   - Bundle ID: `com.ddakta.o2o`
   - Capabilities에서 **Sign In with Apple** 체크
4. **Continue** → **Register**

#### 3. Service ID 생성

1. **Identifiers** → **+** 버튼 클릭
2. **Services IDs** 선택 → **Continue**
3. 설정 입력:
   - Description: `O2O Auth Service`
   - Identifier: `com.ddakta.o2o.auth`
4. **Continue** → **Register**
5. 생성된 Service ID 클릭 → **Sign In with Apple** 활성화
6. **Configure** 클릭:
   - Primary App ID: 위에서 생성한 App ID 선택
   - Domains and Subdomains: `your-domain.com`
   - Return URLs: 
     - `http://localhost:8080/login/oauth2/code/apple` (개발)
     - `https://your-domain.com/login/oauth2/code/apple` (프로덕션)

#### 4. Private Key 생성

1. **Keys** → **+** 버튼 클릭
2. **Sign in with Apple** 체크
3. **Configure** → 위에서 생성한 App ID 선택
4. **Continue** → **Register**
5. **Download** 클릭하여 `.p8` 파일 저장 (한 번만 다운로드 가능!)
6. Key ID 저장

#### 5. Client Secret 생성

Apple은 JWT 형태의 client secret을 사용합니다. 다음 정보로 JWT를 생성해야 합니다:

```javascript
// JWT Header
{
  "alg": "ES256",
  "kid": "YOUR_KEY_ID"
}

// JWT Payload
{
  "iss": "YOUR_TEAM_ID",
  "iat": 현재시간,
  "exp": 현재시간 + 6개월,
  "aud": "https://appleid.apple.com",
  "sub": "com.ddakta.o2o.auth"  // Service ID
}
```

#### 6. application.yml 설정

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          apple:
            client-id: ${APPLE_CLIENT_ID}  # Service ID
            client-secret: ${APPLE_CLIENT_SECRET}  # 생성한 JWT
            redirect-uri: "{baseUrl}/login/oauth2/code/{registrationId}"
            scope:
              - name
              - email
            authorization-grant-type: authorization_code
        provider:
          apple:
            authorization-uri: https://appleid.apple.com/auth/authorize
            token-uri: https://appleid.apple.com/auth/token
            user-info-uri: https://appleid.apple.com/auth/userinfo
```

## API 엔드포인트

### 인증 관련

#### OAuth 로그인
```
POST /api/v1/auth/login
```
Response:
```json
{
  "message": "Please use OAuth2 login endpoints",
  "google": "/oauth2/authorization/google",
  "apple": "/oauth2/authorization/apple"
}
```

#### OAuth 로그인 시작
- Google: `GET /oauth2/authorization/google?role=PASSENGER`
- Apple: `GET /oauth2/authorization/apple?role=DRIVER`

Query Parameters:
- `role` (optional): PASSENGER (default) 또는 DRIVER

#### 토큰 갱신
```
POST /api/v1/auth/refresh
Content-Type: application/json

{
  "refreshToken": "your-refresh-token"
}
```

#### 로그아웃
```
POST /api/v1/auth/logout
Authorization: Bearer {access-token}
```

#### 현재 사용자 정보
```
GET /api/v1/auth/me
Authorization: Bearer {access-token}
```

## 테스트

### 전체 테스트 실행
```bash
./gradlew :services:auth-service:test
```

### 테스트 구조
```
src/test/kotlin/com/ddakta/auth/
├── support/
│   ├── BaseIntegrationTest.kt    # 통합 테스트 베이스 클래스
│   ├── BaseUnitTest.kt          # 단위 테스트 베이스 클래스
│   └── TestSupport.kt           # 테스트 헬퍼 메소드
├── controller/
│   ├── AuthControllerTest.kt    # 컨트롤러 테스트
│   └── OAuth2IntegrationTest.kt # OAuth 통합 테스트
└── service/
    ├── AuthServiceTest.kt       # 서비스 테스트
    └── JwtTokenProviderTest.kt  # JWT 테스트
```

총 28개의 테스트가 포함되어 있으며, 모든 테스트는 한글 메소드명을 사용합니다.

## 환경 설정

### 환경 변수

| 변수명 | 설명 | 기본값 |
|--------|------|--------|
| `JWT_SECRET` | JWT 서명 키 (최소 32자) | (application.yml 참조) |
| `GOOGLE_CLIENT_ID` | Google OAuth Client ID | - |
| `GOOGLE_CLIENT_SECRET` | Google OAuth Client Secret | - |
| `APPLE_CLIENT_ID` | Apple Service ID | - |
| `APPLE_CLIENT_SECRET` | Apple Client Secret (JWT) | - |
| `DB_URL` | PostgreSQL URL | jdbc:postgresql://localhost:5432/auth_db |
| `DB_USERNAME` | DB 사용자명 | auth_user |
| `DB_PASSWORD` | DB 비밀번호 | auth_password |
| `REDIS_HOST` | Redis 호스트 | localhost |
| `REDIS_PORT` | Redis 포트 | 6379 |

### 프로파일

- `default`: 기본 설정
- `local`: 로컬 개발 환경
- `test`: 테스트 환경 (TestContainers 사용)
- `prod`: 프로덕션 환경

### JWT 설정

- Access Token 유효기간: 15분
- Refresh Token 유효기간: 7일

## 보안 고려사항

1. **JWT Secret**: 프로덕션 환경에서는 반드시 강력한 비밀키를 사용하세요.
2. **OAuth Credentials**: Client ID와 Secret은 환경 변수로 관리하세요.
3. **HTTPS**: 프로덕션 환경에서는 반드시 HTTPS를 사용하세요.
4. **CORS**: 필요한 도메인만 허용하도록 설정하세요.

## 문제 해결

### OAuth 로그인이 작동하지 않는 경우
1. Client ID와 Secret이 올바른지 확인
2. Redirect URI가 OAuth 프로바이더에 등록되어 있는지 확인
3. 프로파일별 설정 파일이 올바른지 확인

### 토큰이 거부되는 경우
1. JWT Secret이 올바른지 확인
2. 토큰 만료 시간 확인
3. Redis 연결 상태 확인

## 주요 변경사항

### 최근 리팩토링 내역
- **패키지 구조 단순화**: Clean Architecture에서 간단한 Spring Boot 구조로 변경
  - `api/controller` → `controller`
  - `api/dto` → `dto`
  - `infrastructure/config` → `config`
  - `application/service` → `service`
- **중복 기능 제거**: 
  - `CurrentUser`와 `LoginUser` 어노테이션을 `CurrentUser`로 통합
  - SQL 마이그레이션 파일 통합 (V1, V2 → V1)
- **Redis 쿼리 문제 해결**: `@Indexed` 어노테이션 추가로 쿼리 메소드 지원
- **인증 개선**: `JwtAuthenticationEntryPoint` 추가로 401 응답 처리
- **테스트 개선**: 
  - 모든 테스트 메소드명 한글화
  - 중복 코드를 헬퍼 클래스로 분리
  - OAuth 통합 테스트 추가

## 라이센스

이 프로젝트는 MIT 라이센스를 따릅니다.