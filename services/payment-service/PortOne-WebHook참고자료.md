# 웹훅 연동하기

- ## type
    >웹훅을 트리거한 이벤트의 타입입니다. 가능한 type의 종류는 아래와 같습니다.

  - ### 결제 관련
    - `Transaction.Ready`: 결제창이 열렸을 때
    - `Transaction.Paid`: 결제(예약 결제 포함)가 승인되었을 때 (모든 결제 수단)
    - `Transaction.VirtualAccountIssued`: 가상계좌가 발급되었을 때
    - `Transaction.PartialCancelled`: 결제가 부분 취소되었을 때
    - `Transaction.Cancelled`: 결제가 완전 취소되었을 때
    - `Transaction.Failed`: 결제(예약 결제 포함)가 실패했을 때
    - `Transaction.PayPending`: 결제 승인 대기 상태가 되었을 때 (해외 결제시 발생 가능)
    - `Transaction.CancelPending`: (결제 취소가 비동기로 수행되는 경우) 결제 취소를 요청했을 때
    - `Transaction.DisputeCreated`: 분쟁이 발생되었을 때
    - `Transaction.DisputeResolved`: 분쟁이 해소되었을 때
  - ### 빌링키 발급 관련

    - `BillingKey.Ready`: 빌링키 발급창이 열렸을 때
    - `BillingKey.Issued`: 빌링키가 발급되었을 때
    - `BillingKey.Failed`: 빌링키 발급이 실패했을 때
    - `BillingKey.Deleted`: 빌링키가 삭제되었을 때
    - `BillingKey.Updated`: 빌링키가 업데이트되었을 때
    


- ## timestamp
    >해당 웹훅을 트리거한 이벤트의 발생 시각(RFC 3339 형식)입니다. 고객사 서버가 웹훅을 수신하는 데 실패하여 재시도가 일어나도 이 값은 동일하게 유지됩니다.

- ## data
    >웹훅을 트리거한 이벤트의 실제 세부 내용입니다.
  > type 에 따라 해당 필드의 스키마가 달라질 수 있으며, type 별 상세 스키마는 아래와 같습니다.

  - 공통
    - `storeId`: 웹훅을 트리거한 상점의 아이디입니다.
  - 결제 관련
    - `paymentId`: 고객사에서 채번한 결제 건의 고유 주문 번호입니다.
    - `transactionId`: 포트원에서 채번한 고유 결제 시도 번호입니다. 한 결제 건에 여러 시도가 있을 경우 transactionId가 달라질 수 있습니다.
    - `cancellationId`: (optional) 포트원에서 채번한 결제건의 취소 고유 번호입니다. type이 Transaction.PartialCancelled, Transaction.Cancelled, Transaction.CancelPending 일 때 존재합니다.
  - 빌링키 발급 관련
    - `billingKey`: 포트원에서 채번한 빌링키입니다.


<br>

>웹훅 메시지에는 별도 안내 없이 type 값이나 새로운 필드가 추가될 수 있습니다.
>>- 알지 못하는 type을 가진 메시지의 경우 에러를 발생시키지 말고 메시지 전체를 무시해 주세요. 
>>- 알지 못하는 필드가 있는 경우 해당 필드를 무시해 주세요.

<br>


#### 웹훅 본문 예시 1: type이 Transaction.Cancelled 일 때

```json
{
    "type": "Transaction.Cancelled",
    "timestamp": "2024-04-25T10:00:00.000Z",
    "data": {
        "paymentId": "example-payment-id",
        "storeId": "store-ae356798-3d20-4969-b739-14c6b0e1a667",
        "transactionId": "55451513-9763-4a7a-bb43-78a4c65be843",
        "cancellationId": "0cdd91e9-4e7c-44a3-a72e-1a6511826c2b"
    }
}
```
<br>

#### 웹훅 본문 예시 2: type이 BillingKey.Issued 일 때
```json
{
    "type": "BillingKey.Issued",
    "timestamp": "2024-04-25T10:00:00.000Z",
    "data": {
        "storeId": "store-61e0db3d-b967-47db-8b50-96002da90d55",
        "billingKey": "billing-key-75ae3cab-6afe-422d-bf34-3a7b1762451d"
    }
}
```
