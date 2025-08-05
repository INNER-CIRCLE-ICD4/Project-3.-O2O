# Location Service 임시 구현 가이드

## 개요

이 문서는 matching-service에서 location-service의 부재를 처리하기 위한 임시 구현 방안을 설명합니다. Location-service가 완전히 구현될 때까지 사용되는 폴백 메커니즘과 Mock 설정을 포함합니다.

## 현재 상황

Location-service는 아직 완전히 구현되지 않았으나, matching-service는 다음과 같은 위치 관련 기능에 의존합니다:

- 드라이버 위치 실시간 조회
- H3 인덱스 기반 지리공간 연산
- 거리 계산 및 경로 최적화
- 교통 상황 반영

## 임시 처리 방안

### 1. Fallback 메커니즘

#### LocationServiceFallback 클래스
```kotlin
@Component
class LocationServiceFallback : LocationServiceClient {
    // Redis 캐시 우선 조회
    // 캐시 미스 시 기본값 반환
    // 하버사인 공식을 이용한 직선 거리 계산
}
```

#### 주요 폴백 동작

1. **드라이버 위치 조회**
   - Redis 캐시에서 먼저 조회
   - 캐시 키: `driver:location:{driverId}`
   - 캐시 형식: `latitude,longitude,h3Index`
   - 캐시 미스 시 null 반환

2. **가용 드라이버 조회**
   - Redis Set에서 지역별 드라이버 조회
   - 캐시 키: `drivers:available:{h3Index}`
   - 기본 평점: 4.5
   - 기본 수락률: 0.8
   - 기본 완료 운행 수: 95

3. **거리 계산**
   - 하버사인 공식 사용 (직선 거리)
   - 평균 속도 30km/h 가정
   - 예상 시간: km당 2분
   - 교통 상황: "UNKNOWN" 고정

4. **H3 인덱스 연산**
   - 이웃 인덱스 조회 시 입력값만 반환
   - 실제 H3 라이브러리 미사용

### 2. Redis 캐싱 전략

#### 캐시 구조
```
# 드라이버 위치 정보
driver:location:{driverId} = "latitude,longitude,h3Index"
TTL: 60초

# 지역별 가용 드라이버
drivers:available:{h3Index} = Set<driverId>
TTL: 30초

# 드라이버 상태 정보
driver:stats:{driverId} = JSON {rating, acceptanceRate, completedTrips}
TTL: 300초
```

#### 캐시 워밍
- DriverLocationConsumer가 Kafka 이벤트를 통해 캐시 업데이트
- 실시간 위치 업데이트 시뮬레이션 가능

### 3. WireMock 설정

#### 테스트 환경 Mock 응답
```json
{
  "request": {
    "method": "GET",
    "urlPathPattern": "/api/v1/drivers/available"
  },
  "response": {
    "status": 200,
    "jsonBody": {
      "drivers": [
        {
          "id": "{{randomValue type='UUID'}}",
          "location": {
            "latitude": "{{randomValue type='DOUBLE' range='[37.49, 37.52]'}}",
            "longitude": "{{randomValue type='DOUBLE' range='[127.02, 127.07]'}}"
          },
          "rating": "{{randomValue type='DOUBLE' range='[4.0, 5.0]'}}",
          "acceptanceRate": "{{randomValue type='DOUBLE' range='[0.8, 0.99]'}}"
        }
      ]
    }
  }
}
```

#### 테스트 시나리오
1. **정상 응답**: 2-5명의 랜덤 드라이버 반환
2. **빈 응답**: 드라이버 없음 시나리오
3. **타임아웃**: Circuit Breaker 테스트
4. **에러 응답**: 5xx 에러 시뮬레이션

## Circuit Breaker 설정

```yaml
resilience4j:
  circuitbreaker:
    instances:
      location-service:
        slidingWindowSize: 10
        minimumNumberOfCalls: 5
        failureRateThreshold: 50
        waitDurationInOpenState: 30s
```

### 동작 방식
1. 10개 요청 중 5개 이상 실패 시 Circuit Open
2. 30초 동안 모든 요청 차단 (Fallback 즉시 실행)
3. Half-Open 상태에서 테스트 요청 수행
4. 성공 시 Close, 실패 시 다시 Open

## 개발 환경 설정

### 1. Docker Compose 설정
```yaml
services:
  wiremock:
    image: wiremock/wiremock:latest
    ports:
      - "8083:8080"
    volumes:
      - ./test-infrastructure/wiremock:/home/wiremock
    command: --local-response-templating
```

### 2. 환경 변수
```bash
# Location Service URL (WireMock)
LOCATION_SERVICE_URL=http://localhost:8083

# Redis 설정
REDIS_HOST=localhost
REDIS_PORT=6379
```

### 3. 로컬 테스트 데이터 생성
```kotlin
// Redis에 테스트 드라이버 데이터 삽입
fun populateTestDrivers() {
    val driverIds = (1..20).map { UUID.randomUUID() }
    val h3Indexes = listOf("8830e1d8b9fffff", "8830e1d8bbfffff", "8830e1d8bdfffff")
    
    driverIds.forEach { driverId ->
        val h3Index = h3Indexes.random()
        val lat = 37.5 + Random.nextDouble(-0.05, 0.05)
        val lng = 127.0 + Random.nextDouble(-0.05, 0.05)
        
        // 위치 정보 저장
        redisTemplate.opsForValue().set(
            "driver:location:$driverId",
            "$lat,$lng,$h3Index",
            60, TimeUnit.SECONDS
        )
        
        // 가용 드라이버 Set에 추가
        redisTemplate.opsForSet().add("drivers:available:$h3Index", driverId.toString())
    }
}
```

## Location Service 통합 시 수정 사항

### 1. LocationServiceFallback 제거
- 폴백 클래스 삭제
- FeignClient에서 fallback 속성 제거

### 2. 실제 H3 라이브러리 통합
```kotlin
// 현재 (임시)
fun getNeighboringH3Indexes(h3Index: String, ringSize: Int): List<String> {
    return listOf(h3Index) // 단순 반환
}

// 수정 후
fun getNeighboringH3Indexes(h3Index: String, ringSize: Int): List<String> {
    return h3Core.kRing(h3Index, ringSize) // 실제 H3 연산
}
```

### 3. 거리 계산 API 사용
```kotlin
// 현재 (하버사인 직선 거리)
val distance = haversineDistance(fromLat, fromLng, toLat, toLng)

// 수정 후 (실제 도로 기반)
val routeInfo = locationServiceClient.calculateRoute(from, to)
val distance = routeInfo.distanceMeters
val duration = routeInfo.durationSeconds
```

### 4. 실시간 위치 스트리밍
```kotlin
// Kafka 토픽 구독
@KafkaListener(topics = ["driver-location-updates"])
fun handleLocationUpdate(event: DriverLocationUpdatedEvent) {
    // 실시간 위치 처리
}
```

## 모니터링 및 알림

### 메트릭
- `location_service_fallback_count`: 폴백 실행 횟수
- `location_service_circuit_breaker_state`: Circuit Breaker 상태
- `redis_cache_hit_rate`: 캐시 적중률

### 로깅
```kotlin
logger.warn { "Location service unavailable, using fallback" }
logger.info { "Returning ${drivers.size} cached drivers" }
```

### 알림 설정
- Circuit Breaker Open 시 Slack 알림
- 캐시 적중률 50% 미만 시 경고
- Fallback 사용률 상승 시 알림

## 테스트 가이드

### 1. 단위 테스트
```kotlin
@Test
fun `should return cached drivers when location service is down`() {
    // Given: Redis에 드라이버 데이터 설정
    // When: location service 호출 실패
    // Then: 캐시된 데이터 반환 확인
}
```

### 2. 통합 테스트
```kotlin
@Test
fun `should complete matching with fallback`() {
    // Given: WireMock 중지
    // When: 매칭 요청
    // Then: Fallback으로 매칭 완료 확인
}
```

### 3. 부하 테스트
```javascript
// K6 스크립트
export default function() {
  // Location service 없이 매칭 성능 테스트
  http.post('/api/v1/rides', rideRequest);
}
```

## FAQ

### Q: Redis가 다운되면 어떻게 되나요?
A: Redis와 location-service 모두 다운된 경우, 빈 드라이버 리스트를 반환하여 매칭이 실패합니다. 이는 안전한 실패(fail-safe) 전략입니다.

### Q: 실제 거리와 하버사인 거리의 차이는?
A: 도시 환경에서 약 20-40% 차이가 날 수 있습니다. 하버사인은 직선 거리만 계산하므로 실제 도로 거리보다 짧습니다.

### Q: 캐시 TTL은 왜 짧게 설정되어 있나요?
A: 드라이버 위치는 실시간으로 변하므로 60초 이상 캐싱하면 부정확한 매칭이 발생할 수 있습니다.

### Q: WireMock 응답을 커스터마이즈하려면?
A: `/test-infrastructure/wiremock/mappings/` 디렉토리의 JSON 파일을 수정하면 됩니다.

## 결론

이 임시 구현은 location-service가 완성될 때까지 matching-service가 독립적으로 개발되고 테스트될 수 있도록 합니다. 실제 서비스 통합 시 최소한의 코드 변경으로 전환할 수 있도록 설계되었습니다.