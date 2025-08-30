# Payment Service 상세 분석 문서

이 문서는 `payment-service`의 핵심 기능인 API, Kafka 이벤트 리스너, 그리고 내부 로직 및 정책에 대해 상세하게 설명합니다.

## 1. API 엔드포인트 상세

### 1.1. 사용자 결제 내역 조회
- **Endpoint**: `GET /api/v1/payments/{userId}`
- **Controller**: `PaymentController.kt`
- **설명**: 특정 사용자의 전체 결제 내역 리스트를 조회합니다.

### 1.2. 결제 취소 요청
- **Endpoint**: `POST /api/v1/payments/{paymentId}/cancel`
- **Controller**: `PaymentController.kt`
- **설명**: 특정 `paymentId`에 해당하는 결제를 취소합니다. 내부적으로 `PaymentService`의 `cancelPayment` 메서드를 호출하여 PG사 취소 요청(Mock) 및 상태 변경, `PaymentCancelled` 이벤트 발행 등의 로직을 수행합니다.

### 1.3. PG사 웹훅(Webhook) 수신
- **Endpoint**: `POST /api/v1/payments/webhook`
- **Controller**: `PGWebHookListener.kt`
- **설명**: 외부 결제 게이트웨이(PortOne)로부터 결제 상태 변경에 대한 비동기 알림을 수신합니다. 이 웹훅은 PG사에서 결제가 실제로 성공, 실패, 취소되었을 때 호출됩니다.
- **처리 타입**:
  - `Transaction.Paid`: 결제 성공. `paymentService.paymentSuccess`를 호출합니다.
  - `Transaction.Failed`: 결제 실패. `paymentService.paymentFail`을 호출합니다.
  - `Transaction.Cancelled`: 결제 취소. `paymentService.cancelPayment`를 호출합니다.

---

## 2. Kafka 이벤트 리스너(컨슈머) 상세

### 2.1. 운행 종료 이벤트 리스너
- **Topic**: `drive-end`
- **Listener**: `PaymentEventListener.kt` - `driveEndEventListener`
- **설명**: `matching-service` 등 다른 서비스로부터 운행이 최종적으로 완료되었을 때 발행되는 `DriveEndEvent`를 구독합니다. 이 이벤트를 수신하는 것이 **결제 프로세스를 시작하는 핵심 트리거**입니다.
- **동작**: 수신한 이벤트 정보를 바탕으로 `paymentService.executePayment`를 호출하여 결제를 요청합니다.

### 2.2. 결제 재시도 이벤트 리스너
- **Topic**: `retry-payment`
- **Listener**: `PaymentEventListener.kt` - `paymentRetryEventListener`
- **설명**: 결제 실패 후, 특정 조건 하에 재시도가 필요할 때 발행되는 `PaymentRetryEvent`를 구독합니다.
- **동작**: `paymentService.retryPayment`를 호출하여 기존 결제 건에 대해 PG사에 다시 결제를 요청합니다.

---

## 3. 핵심 로직 및 정책

### 3.1. 결제 처리 정책
1.  **시작**: `drive-end` Kafka 이벤트 수신으로 모든 결제 프로세스가 시작됩니다.
2.  **검증**: `PaymentService`는 해당 운행(`matchId`)에 대해 이미 진행 중인 결제가 있는지 확인하여 중복 처리를 방지합니다.
3.  **생성**: `Payment` 엔티티를 `PENDING` 상태로 DB에 생성합니다.
4.  **요청**: `PGService`를 통해 외부 PG사에 결제를 요청합니다.
5.  **결과 처리**: PG사로부터 웹훅을 통해 전달된 성공 또는 실패 결과에 따라 `Payment`의 상태를 `SUCCESS` 또는 `FAILED`로 업데이트하고, 각각 `PaymentSuccessEvent` 또는 `PaymentFailedEvent`를 Kafka로 발행하여 다른 서비스에 알립니다.

### 3.2. 결제 취소 정책
1.  **시작**: 사용자가 취소 API(`POST /api/v1/payments/{paymentId}/cancel`)를 호출하여 시작됩니다.
2.  **검증**: 이미 취소된 결제인지 확인합니다.
3.  **요청**: `PGService`를 통해 외부 PG사에 결제 취소를 요청합니다.
4.  **상태 변경**: PG사로부터 취소 성공을 확인하면, `Payment`의 상태를 `CANCELLED`로 변경합니다.
5.  **이벤트 발행**: `PaymentCancelledEvent`를 발행하여 취소 사실을 다른 서비스에 알립니다.

### 3.3. 멱등성(Idempotency) 정책
- **목적**: 네트워크 오류 등으로 인해 동일한 요청이나 이벤트가 여러 번 수신되더라도, 실제 로직은 단 한 번만 수행되도록 보장합니다.
- **구현**:
  - **웹훅 수신 시**: `PGWebHookListener`는 Redis를 사용합니다. PG사가 보내주는 고유 `transactionId` 또는 `cancellationId`를 키(key)로 하여, 웹훅을 처리하기 전에 Redis에 해당 키가 있는지 확인합니다. 키가 이미 존재하면, 이미 처리된 웹훅으로 간주하고 로직을 수행하지 않아 중복 처리를 완벽하게 방지합니다.
  - **결제 생성 시**: `PaymentService`는 `matchId`를 기준으로 DB를 조회하여, 동일한 `matchId`에 대한 결제가 이미 존재하는지 확인하여 중복 생성을 막습니다.

### 3.4. Mock PG 정책 (개발 환경)
- **목적**: 외부 PG사와 실제 연동을 하지 않고도 개발 환경에서 결제 흐름을 테스트하기 위함입니다.
- **구현 (`PGServiceImpl.kt`)**:
  - **결제 요청**: `processPayment` 메서드는 실제 외부 API를 호출하는 대신, 로그만 남기고 **무조건 성공을 가정**합니다. 그 후, PG사가 비동기적으로 성공 웹훅을 보낸 것처럼 **즉시 `paymentService.paymentSuccess`를 호출**하여 성공 흐름을 시뮬레이션합니다.
  - **취소 요청**: `cancelPayment` 메서드 또한 항상 `true`를 반환하여 취소가 성공했다고 가정합니다.
