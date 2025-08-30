# 위치 서비스 (Location Service)

## 📋 서비스 개요

위치 서비스는 O2O 라이드헬링 플랫폼에서 드라이버의 실시간 위치를 관리하고, 매칭 서비스 등 다른 서비스가 필요한 위치 기반 정보를 제공하는 핵심 컴포넌트입니다. 기존의 REST API 기반 위치 업데이트 방식에서 WebSocket 기반으로 전환하여 실시간성과 확장성을 강화했습니다.

## 🏗️ 아키텍처 설명

### 시스템 구성도

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│   Driver App    │────▶│ Location Service│────▶│      Redis      │
│ (WebSocket Client)│     │ (WebSocket Server)│     │ (Geo, H3 Sets)  │
└─────────────────┘     └─────────────────┘     └─────────────────┘
                               │
                               ▼
                        ┌─────────────────┐
                        │ Matching Service│
                        │ (API Client)    │
                        └─────────────────┘
```

### 핵심 컴포넌트

-   **WebSocket Handler (`LocationWebSocketHandler`)**: 드라이버 앱으로부터 실시간 위치 업데이트를 WebSocket을 통해 수신합니다.
-   **Location Service (`LocationService`)**: 수신된 위치 데이터를 처리하고, Redis에 저장하며, H3 기반 드라이버 검색 로직을 수행합니다.
-   **Redis**: 드라이버의 실시간 위치(위도, 경도), H3 인덱스, 상태(ONLINE/OFFLINE) 정보를 저장합니다.
-   **H3 Library (`com.uber.h3core.H3Core`)**: 위도/경도를 H3 인덱스로 변환하고, H3 인덱스 기반의 인접 셀(k-ring) 검색을 수행합니다.

## 🚀 주요 기능

-   **WebSocket 기반 실시간 위치 업데이트**: 드라이버 앱이 WebSocket을 통해 자신의 위치를 서버에 실시간으로 전송합니다.
-   **H3 인덱스 기반 위치 관리**: 드라이버의 위치를 H3 인덱스로 변환하여 Redis에 저장하고 관리합니다.
-   **H3 기반 주변 드라이버 검색**: 특정 H3 인덱스와 그 인접 지역(k-ring) 내에 있는 운행 가능한 드라이버를 효율적으로 검색하여 반환합니다.
-   **드라이버 상태 관리**: 드라이버의 온라인/오프라인 상태를 관리하며, 검색 시 운행 가능한 드라이버만 반환합니다.

## 📡 API 엔드포인트

### 1. H3 인덱스 기반 주변 드라이버 검색

-   **Endpoint**: `GET /api/v1/locations/h3-drivers`
-   **Controller**: `LocationController.kt`
-   **설명**: 특정 H3 인덱스를 중심으로 인접 지역 내의 운행 가능한 드라이버 목록을 조회합니다.
-   **파라미터**:
    -   `h3Index` (String, 필수): 검색의 중심이 될 H3 인덱스 문자열.
    -   `kRingSize` (Int, 선택, 기본값: `1`): 중심 H3 인덱스로부터 검색할 인접 지역의 범위 (k-ring 크기). `0`은 중심 셀만, `1`은 중심 셀과 직접 인접한 셀들을 포함합니다.
-   **응답**: `List<NearbyDriverDto>` (드라이버 ID, 위도, 경도 포함)

## 💾 데이터 모델 (Redis)

위치 서비스는 Redis를 사용하여 드라이버의 실시간 위치 및 상태 정보를 관리합니다.

-   **`drivers:geo` (Geo Set)**: 드라이버의 위도/경도 정보를 저장하는 Geo Set. `GEORADIUS`와 같은 반경 검색에 사용될 수 있습니다.
-   **`driver:location:{driverId}` (Hash)**: 각 드라이버의 상세 위치 정보(위도, 경도, H3 인덱스)를 저장하는 Hash.
    -   `latitude`: 드라이버의 현재 위도
    -   `longitude`: 드라이버의 현재 경도
    -   `h3Index`: 드라이버의 현재 H3 인덱스
-   **`h3:drivers:{h3Index}` (Set)**: 특정 H3 인덱스에 속한 드라이버 ID들을 저장하는 Set. H3 기반 검색 시 사용됩니다.
-   **`driver:status:{driverId}` (String)**: 각 드라이버의 현재 상태(예: `ONLINE`, `OFFLINE`, `BUSY`)를 저장하는 String.

모든 위치 및 상태 정보는 `DRIVER_TTL_MINUTES` (기본 5분) 동안만 유효하며, 이후 자동 만료됩니다.

## 📊 성능 고려사항: H3 인덱싱 vs Redis Geo

위치 서비스는 드라이버 검색에 H3 인덱싱을 적극적으로 활용합니다. 이는 기존의 Redis Geo (`GEORADIUS`) 기반 반경 검색과 비교하여 특정 유형의 쿼리에서 성능 및 아키텍처적 이점을 제공하기 때문입니다.

| 특징             | Redis Geo (`GEORADIUS`)                               | H3 인덱싱                                                              |
| :--------------- | :---------------------------------------------------- | :--------------------------------------------------------------------- |
| **검색 형태**    | 원형 반경 내 검색                                     | 육각형 셀 및 인접 셀(k-ring) 내 검색                                   |
| **"인접 구역"**  | 직접적인 개념 없음. 여러 번의 쿼리 또는 복잡한 후처리 필요 | `kRing` 함수를 통해 매우 효율적으로 인접 셀 검색 가능                  |
| **주요 용도**    | 임의의 중심점으로부터 정확한 반경 내 지점 검색        | 특정 지역 및 그 인접 지역 내 지점 검색, 지역 기반 데이터 집계 및 분석 |
| **성능 이점**    | 임의 반경 검색에 최적                                 | **"특정 지역 및 인접 구역" 검색에 압도적으로 유리**                    |
| **아키텍처 적합성** | 일반적인 위치 검색                                    | 라이드 헤일링과 같은 격자 기반 서비스에 매우 적합                      |

`matching-service`가 H3 인덱스를 기반으로 드라이버를 검색하고 인접 지역의 드라이버를 필요로 하므로, H3 인덱싱은 이러한 요구사항을 효율적으로 만족시키는 데 필수적입니다.

## 💡 향후 개선 방향

-   **실제 도로망 기반 거리 및 도착 시간(ETA) 계산 기능**: 현재는 H3 기반 드라이버 검색만 제공합니다. `matching-service`가 정확한 매칭 점수 계산 및 사용자 안내를 위해 실제 도로망 기반의 거리 및 ETA 계산 기능이 필요합니다. 이를 위해 외부 라우팅 엔진(예: OSRM) 또는 상용 지도 API 연동이 필요합니다.
-   **드라이버 앱과의 WebSocket 인증/인가**: 현재 WebSocket 연결 시 `driverId`를 `session.attributes`에서 가져오는데, 실제 환경에서는 JWT 토큰 등을 이용한 강력한 인증/인가 메커니즘이 필요합니다.

## 🚀 설치 및 실행 방법

### 1. 데이터베이스 및 인프라 구성

```bash
# 개발 환경 인프라 실행 (Redis 포함)
docker-compose -f docker-compose.yml up -d
```

### 2. 애플리케이션 실행

```bash
# Gradle 빌드
./gradlew :services:location-service:build

# 애플리케이션 실행
./gradlew :services:location-service:bootRun
```

## ⚙️ 환경 변수 설정

-   `app.ws.allowed-origins`: WebSocket 연결을 허용할 Origin 목록 (쉼표로 구분).
-   `spring.data.redis.host`: Redis 호스트명.
-   `spring.data.redis.port`: Redis 포트.
-   `spring.kafka.bootstrap-servers`: Kafka 브로커 주소.
-   `spring.datasource.url`: PostgreSQL JDBC URL.
-   `spring.datasource.username`: PostgreSQL 사용자명.
-   `spring.datasource.password`: PostgreSQL 비밀번호.
