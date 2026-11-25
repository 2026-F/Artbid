# MY 진행 중인 결제 조회 계약

`GET /api/settlements/{id}/payments/active`는 인증된 낙찰자가 정산 ID로 현재 결제 상태를 다시 확인할 때 사용한다.

```json
{
  "settlementStatus": "PENDING",
  "expiredAt": null,
  "paymentDeadlineAt": "2026-11-28T12:00:00+09:00",
  "serverNow": "2026-11-25T12:00:00.123456+09:00",
  "payment": { "id": 31, "status": "REQUESTED" }
}
```

| 응답 | 의미 |
| --- | --- |
| `200` | 같은 PostgreSQL 스냅샷에서 읽은 정산과 진행 중인 결제. 진행 중인 결제가 없으면 `payment: null`. |
| `401 AUTH_REQUIRED` | 로그인하지 않았거나 토큰이 유효하지 않다. |
| `403 FORBIDDEN` | 해당 정산의 낙찰자가 아니다. |
| `404 NOT_FOUND` | 정산이 없다. |

성공 응답은 `Cache-Control: no-store`를 보낸다. 조회 중 PG 호출과 상태 변경은 발생하지 않는다.

## 기한과 상태 판정

- 이 API의 시각은 `Asia/Seoul` 오프셋을 포함한다. 기존 정산 API의 오프셋 없는 `paymentDeadlineAt`과 `expiredAt`, DB 저장 형식은 유지한다. 배포 Compose의 JVM 시간대는 `Asia/Seoul`이며 전체 앱을 로컬에서 연동할 때도 동일한 시간대를 사용한다.
- `PENDING`에 `payment.status === 'REQUESTED'`가 있으면 기한 전후 모두 **결제 확인 중**으로 표시하고 신규 결제를 막는다. 만료 배치도 진행 중인 결제의 결과가 확정될 때까지 보류한다.
- `PENDING`과 `payment: null`인 경우에만 기한을 판정한다. 비교 기준은 `serverNow`에 응답 수신 시점부터의 단조 시계 경과 시간(`performance.now()` 차이)을 더한 예상 서버 시각이다. 오프셋을 포함해 두 시각을 파싱한다. 기한보다 앞설 때만 버튼을 활성화하며 정각부터 비활성화한다. 서버 시각·기한을 읽을 수 없거나 조회에 실패하면 버튼을 비활성화하고 다시 확인을 제공한다.
- `PAID`는 결제 완료다. `FAILED`에서 `expiredAt`이 있으면 기한 만료, 없으면 주문 종료다.
- 조회는 응답 시점의 상태다. 최종 허용 여부는 기존 결제 생성 API가 정산 행 잠금 안에서 판정한다. `PAYMENT_ALREADY_EXISTS` 또는 `SETTLEMENT_NOT_PAYABLE`이면 최신 정산과 진행 중인 결제를 다시 조회한다.

## 신규 알림

- `AUCTION_WON`은 `/mypage/payments`, `PAYMENT_COMPLETED`는 `/mypage/orders`로 연결한다.
- 이미 저장된 `/mypage` 링크는 프론트엔드 #92에서 호환 처리한다. 운영에 알림 변경을 반영하기 전에 프론트엔드 운영 환경에서 두 경로의 진입을 확인한다.
