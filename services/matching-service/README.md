# 매칭 서비스 (Matching Service)

## 📋 서비스 개요

매칭 서비스는 O2O 라이드헬링 플랫폼의 핵심 서비스로, 승객과 드라이버를 최적으로 매칭하는 역할을 담당합니다. 헝가리안 알고리즘을 기반으로 한 고급 매칭 로직을 구현하여 거리, 평점, 수락률 등 다양한 요소를 고려한 효율적인 매칭을 제공합니다.

### 주요 기능
- 🚖 실시간 승객-드라이버 매칭
- 📊 다중 요소 기반 매칭 점수 계산
- 🔄 배치 처리를 통한 성능 최적화
- 💰 동적 요금 계산 및 서지 프라이싱
- 📡 WebSocket을 통한 실시간 상태 업데이트
- 🛡️ Circuit Breaker를 통한 장애 대응

## 🏗️ 아키텍처 설명

### 시스템 구성도
```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│   Gateway       │────▶│ Matching Service│────▶│ Location Service│
└─────────────────┘     └─────────────────┘     └─────────────────┘
                               │                          │
                               ▼                          ▼
                        ┌─────────────┐            ┌─────────────┐
                        │   Kafka     │            │    Redis    │
                        └─────────────┘            └─────────────┘
```

### 핵심 컴포넌트

#### 1. 매칭 알고리즘
- **HungarianAlgorithm**: 최적 이분 매칭을 위한 헝가리안 알고리즘 구현
- **MatchingScoreCalculator**: 거리(70%), 평점(20%), 수락률(10%) 기반 점수 계산
- **BatchMatchingProcessor**: 대량 매칭 요청 배치 처리
- **DriverSelector**: 드라이버 필터링 및 랭킹 로직

#### 2. 서비스 레이어
- **MatchingService**: 매칭 프로세스 오케스트레이션
- **RideService**: 라이드 생명주기 관리
- **DriverCallService**: 드라이버 호출 및 응답 처리
- **FareCalculationService**: 요금 계산 로직
- **SurgePriceService**: 수요 기반 동적 가격 책정

#### 3. 외부 서비스 연동
- **Location Service**: 드라이버 위치 조회, H3 지리공간 연산
- **User Service**: 사용자 프로필 및 결제 정보 조회

### ⚠️ Location Service 임시 처리 방안

현재 Location Service가 완전히 구현되지 않은 상태에서 다음과 같은 임시 처리 방안을 적용하고 있습니다:

#### 1. Fallback 메커니즘
```kotlin
// LocationServiceFallback 클래스에서 처리
- Redis 캐시 우선 조회
- 하버사인 공식을 이용한 거리 계산
- 기본값 반환 (평점 4.5, 수락률 0.8 등)
```

#### 2. Mock 데이터
- WireMock을 통한 테스트 환경 구성
- 실제 서비스와 동일한 API 인터페이스 제공
- `/test-infrastructure/wiremock/mappings/` 디렉토리에 Mock 정의

#### 3. Redis 캐싱 전략
```
키 구조:
- drivers:available:{h3Index} - 지역별 가용 드라이버
- driver:location:{driverId} - 드라이버 위치 정보
- driver:stats:{driverId} - 드라이버 통계 정보
```

## 🚀 설치 및 실행 방법

### 사전 요구사항
- JDK 17 이상
- Docker & Docker Compose
- Gradle 7.x 이상

### 1. 데이터베이스 및 인프라 구성
```bash
# 개발 환경 인프라 실행
docker-compose -f docker-compose.dev.yml up -d

# 데이터베이스 마이그레이션 (자동 실행됨)
# Flyway가 src/main/resources/db/migration 스크립트 실행
```

### 2. 애플리케이션 실행
```bash
# Gradle 빌드
./gradlew :services:matching-service:build

# 애플리케이션 실행
./gradlew :services:matching-service:bootRun

# 또는 JAR 파일로 실행
java -jar services/matching-service/build/libs/matching-service-*.jar
```

### 3. 테스트 환경 실행
```bash
# 테스트용 인프라 실행
cd services/matching-service
docker-compose -f docker-compose.test.yml up -d

# 통합 테스트 실행
./gradlew test
```

## ⚙️ 환경 변수 설정

### 필수 환경 변수
```bash
# 데이터베이스 설정
DB_USERNAME=ddakta
DB_PASSWORD=ddakta123
DB_URL=jdbc:postgresql://localhost:5432/ddakta_matching

# Kafka 설정
KAFKA_BOOTSTRAP_SERVERS=localhost:9092

# Redis 설정
REDIS_HOST=localhost
REDIS_PORT=6379

# 외부 서비스 URL
LOCATION_SERVICE_URL=http://location-service:8083
USER_SERVICE_URL=http://user-service:8081

# Eureka 서버
EUREKA_SERVER=http://localhost:8761/eureka
```

### 프로파일별 설정
- `local`: 로컬 개발 환경 (기본값)
- `prod`: 프로덕션 환경
- `test`: 테스트 환경

## 📡 API 엔드포인트

### 1. 라이드 요청
```http
POST /api/v1/rides
Content-Type: application/json

{
  "passengerId": "uuid",
  "pickupLocation": {
    "latitude": 37.5665,
    "longitude": 126.9780,
    "address": "서울시 중구 을지로"
  },
  "dropoffLocation": {
    "latitude": 37.5172,
    "longitude": 127.0473,
    "address": "서울시 강남구 테헤란로"
  }
}
```

### 2. 라이드 상태 조회
```http
GET /api/v1/rides/{rideId}
```

### 3. 라이드 취소
```http
POST /api/v1/rides/{rideId}/cancel
Content-Type: application/json

{
  "reason": "PASSENGER_CANCELLED",
  "cancelledBy": "PASSENGER"
}
```

### 4. 드라이버 호출 응답
```http
POST /api/v1/driver-calls/{callId}/respond
Content-Type: application/json

{
  "response": "ACCEPTED"
}
```

### 5. 요금 예상
```http
POST /api/v1/fares/estimate
Content-Type: application/json

{
  "pickupLocation": {...},
  "dropoffLocation": {...}
}
```

### 6. WebSocket 연결
```
ws://localhost:8082/ws/rides
```

## 🔧 Location Service 연동 시 필요한 수정사항

Location Service가 완전히 구현되면 다음 부분들을 수정해야 합니다:

### 1. Fallback 제거
```kotlin
// LocationServiceFallback 클래스의 임시 로직 제거
// 실제 서비스 호출로 대체
```

### 2. H3 인덱스 연산
```kotlin
// 현재: 단일 H3 인덱스만 반환
// 수정: 실제 이웃 H3 인덱스 계산
getNeighboringH3Indexes(h3Index, ringSize)
```

### 3. 거리 계산
```kotlin
// 현재: 하버사인 공식 직선 거리
// 수정: 실제 도로 기반 경로 거리
calculateDistance() // 교통 상황 반영
```

### 4. 실시간 위치 업데이트
```kotlin
// 드라이버 위치 실시간 스트리밍
// Kafka 토픽: driver-location-updates
```

## 🧪 테스트 방법

### 단위 테스트
```bash
./gradlew :services:matching-service:test
```

### 통합 테스트
```bash
# TestContainers 사용
./gradlew :services:matching-service:integrationTest
```

### 부하 테스트
```bash
# K6 스크립트 실행
k6 run test-infrastructure/k6-scripts/load-test.js
```

### 테스트 커버리지
- 단위 테스트: 80% 이상
- 통합 테스트: 주요 시나리오 커버
- E2E 테스트: 전체 워크플로우 검증

## 🔍 트러블슈팅 가이드

### 1. 매칭 실패
**증상**: NoAvailableDriverException 발생
```
원인:
- Location Service 연결 실패
- Redis 캐시 비어있음
- 해당 지역 드라이버 부재

해결:
1. Location Service 상태 확인
2. Redis 연결 상태 확인
3. Fallback 로직 동작 확인
```

### 2. 컴파일 오류
**증상**: 빌드 실패
```
원인:
- 의존성 버전 충돌
- Kotlin 버전 불일치

해결:
1. gradle clean build
2. 의존성 버전 확인
3. IntelliJ 캐시 무효화
```

### 3. Kafka 연결 실패
**증상**: 이벤트 발행/구독 실패
```
원인:
- Kafka 브로커 다운
- 토픽 미생성

해결:
1. docker-compose ps 확인
2. Kafka 토픽 생성 스크립트 실행
3. Consumer Group 상태 확인
```

### 4. Circuit Breaker Open
**증상**: 외부 서비스 호출 차단
```
원인:
- 연속된 호출 실패
- 타임아웃 초과

해결:
1. 대상 서비스 상태 확인
2. Circuit Breaker 설정 조정
3. Fallback 로직 검증
```

## 📊 모니터링

### Prometheus 메트릭
- `http://localhost:8082/actuator/prometheus`
- 매칭 성공률, 응답 시간, 에러율 등

### Grafana 대시보드
- `http://localhost:3000`
- 실시간 서비스 상태 모니터링

### 주요 메트릭
```
matching_requests_total - 총 매칭 요청 수
matching_success_rate - 매칭 성공률
driver_call_response_time - 드라이버 응답 시간
ride_completion_rate - 라이드 완료율
```

## 🤝 기여 방법

1. Feature 브랜치 생성
2. 변경사항 커밋
3. 테스트 통과 확인
4. Pull Request 생성

## 📝 라이선스

이 프로젝트는 내부 사용 목적으로 개발되었습니다.