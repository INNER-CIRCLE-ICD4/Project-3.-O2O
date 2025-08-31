# 배차 시각화 서비스 (Dispatch Visualizer Service)

이 서비스는 O2O 라이드헬링 플랫폼의 배차 과정을 시뮬레이션하고 시각적으로 확인하기 위한 도구입니다. 드라이버의 위치 업데이트, 승객의 배차 요청, 그리고 매칭 서비스로부터의 실시간 배차 알림 흐름을 한눈에 볼 수 있습니다.

## 🚀 주요 기능

-   **드라이버 시뮬레이션**: 임의의 위치에서 가상의 드라이버들을 생성하고, 주기적으로 위치를 업데이트하여 `location-service`로 전송합니다.
-   **승객 배차 요청 시뮬레이션**: 가상의 승객이 배차 요청을 생성하여 `matching-service`로 전송합니다.
-   **실시간 알림 시각화**: `matching-service`로부터 오는 배차 알림(드라이버 호출, 수락/거절 등)을 실시간으로 수신하여 웹 페이지에 표시합니다.

## 🏗️ 아키텍처 설명

```
┌───────────────────┐     ┌───────────────────┐     ┌───────────────────┐
│  Frontend (React) │────▶│ Dispatch Visualizer │────▶│  Location Service │
│ (Browser WebSocket)│     │   (Backend)       │     │   (WebSocket)     │
└───────────────────┘     └───────────────────┘     └───────────────────┘
          ▲                               │
          │                               │
          │                               ▼
          └───────────────────────────────▶│  Matching Service │
            (WebSocket for Notifications)  │   (REST API & WebSocket)  │
                                           └───────────────────┘
```

-   **Frontend (React)**: 사용자 인터페이스를 제공하며, 백엔드 API 호출 및 `matching-service`로부터의 WebSocket 알림을 수신합니다.
-   **Dispatch Visualizer Backend (Spring Boot)**: 시뮬레이션 로직을 관리하고, `location-service`와 `matching-service`에 대한 클라이언트 역할을 수행합니다. 또한, `matching-service`로부터 받은 알림을 프론트엔드로 중계합니다.
-   **Location Service**: 시뮬레이션된 드라이버의 위치 정보를 관리합니다.
-   **Matching Service**: 배차 요청을 처리하고, 드라이버 호출 및 매칭 결과를 실시간으로 알립니다.

## 🛠️ 사용 방법

### 1. 서비스 실행 준비

이 서비스를 사용하려면 `location-service`와 `matching-service`가 먼저 실행 중이어야 합니다. 각 서비스의 `README.md`를 참조하여 실행해주세요.

### 2. `dispatch-visualizer-service` 실행

#### 2.1. 백엔드 실행

```bash
# dispatch-visualizer-service 디렉토리로 이동
cd services/dispatch-visualizer-service

# Gradle 빌드
./gradlew build

# 애플리케이션 실행
./gradlew bootRun
```

#### 2.2. 프론트엔드 실행

```bash
# dispatch-visualizer-service/frontend 디렉토리로 이동
cd services/dispatch-visualizer-service/frontend

# 의존성 설치 (최초 1회)
npm install

# 개발 서버 실행
npm start
```

프론트엔드 개발 서버는 일반적으로 `http://localhost:3000`에서 실행됩니다.

### 3. 시뮬레이션 시작

웹 페이지(`http://localhost:3000`)에 접속하면 다음과 같은 컨트롤을 볼 수 있습니다.

-   **드라이버 시뮬레이션 시작**: `Number of Drivers` 입력 필드에 시뮬레이션할 드라이버 수를 입력하고 `Start Driver Simulation` 버튼을 클릭합니다. 가상의 드라이버들이 생성되어 `location-service`로 위치 정보를 전송하기 시작합니다.
-   **배차 요청**: `Request Ride` 버튼을 클릭합니다. 가상의 승객이 배차 요청을 생성하여 `matching-service`로 전송합니다.

### 4. 실시간 알림 확인

웹 페이지 하단의 `Notifications` 섹션에서 `matching-service`로부터 오는 실시간 배차 알림들을 확인할 수 있습니다. 드라이버 호출, 수락, 거절 등의 메시지가 순차적으로 표시되어 배차 알고리즘의 동작을 간접적으로 시각화합니다.

## ⚙️ 환경 변수 설정

`src/main/resources/application.yml` 파일을 통해 다음 환경 변수를 설정할 수 있습니다.

-   `location.service.ws.url`: `location-service`의 WebSocket 엔드포인트 URL (예: `ws://localhost:8083/ws/locations`)
-   `matching.service.api.url`: `matching-service`의 REST API 기본 URL (예: `http://localhost:8082`)

## 💡 향후 개선 방향

-   **지도 시각화**: 드라이버와 승객의 위치, 배차 요청 및 매칭 결과를 지도 위에 직접 표시하여 더욱 직관적인 시각화를 제공합니다.
-   **시뮬레이션 상세 제어**: 드라이버의 이동 경로, 속도, 승객의 픽업/드롭오프 위치 등을 더 세밀하게 제어할 수 있는 기능을 추가합니다.
-   **매칭 과정 상세 표시**: 매칭 알고리즘이 드라이버를 선택하고 호출을 보내는 과정을 단계별로 시각화합니다.
