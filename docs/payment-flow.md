# Settlement와 Payment 연결

작성일: 2026-09-24 / 담당: 이혜령

## 전체 흐름

Settlement에 낙찰자, 낙찰가, 수수료, 배송비, 결제기한을 저장함.
PaymentService에서 결제 요청을 받아 Mock PG를 호출하고 Payment와 Settlement에 결과를 반영함.
낙찰 확정에서 Settlement를 생성하는 연결은 후속 작업임. 현재 Test Fixture로 생성함.

## 구현 내용

- Settlement.create()에서 totalAmount를 계산함. finalPrice + premiumFee + shippingFee이며 Long overflow도 검사함.
- Transaction 안에서 Settlement에 PESSIMISTIC_WRITE Lock을 걸고 낙찰자, 결제기한, 결제 상태를 검사함.
- 동일 Settlement에 REQUESTED 또는 PAID인 Payment가 있으면 새 결제를 거절함.
- Payment를 REQUESTED로 저장하고 Commit한 뒤 Transaction 밖에서 Mock PG를 호출함.
- 새 Transaction에서 같은 Settlement를 Lock하고 결과를 저장함. 성공이면 Payment와 Settlement 모두 PAID로 변경함.
- PG가 명확히 거절하면 Payment만 FAILED로 변경함. Settlement는 PENDING을 유지해 새 Payment로 재시도할 수 있음.
- Timeout이면 REQUESTED를 유지함. 자동 재시도하거나 FAILED로 확정하지 않음.
- PaymentService는 Propagation.NEVER를 사용함. 외부 Transaction에서 호출하면 PG 호출 전에 거절함.

## API 계약

현재 코드 기준 /api와 Long ID를 유지함. 아래 응답 방식은 기존 명세와 달라 팀 검토가 필요함.

| API | Request | Response |
| --- | --- | --- |
| POST /api/settlements/{id}/payments | `{"method":"MOCK_PG"}` | 최종 결과 PAID/FAILED는 200, 결과 불명 REQUESTED는 202 |
| GET /api/payments/{id} | Body 없음 | 본인 Payment의 상태, 금액, 거래 ID, 요청/결제 시각, 실패 사유 |

금액은 Client에서 받지 않고 DB의 totalAmount를 사용함.
PG 거절은 처리된 결제 결과이므로 FAILED Body와 200을 반환함. Timeout은 Payment ID와 202를 반환함.
202는 저장된 요청의 결과가 아직 불명이라는 뜻이며, 자동 처리 Worker가 있다는 뜻은 아님.

PaymentController 전용 ExceptionHandler에서 입력 오류 400, Authentication 누락 401,
타인 접근 403, 리소스 없음 404, 결제 상태 충돌 409, Lock 획득 실패 503을 반환함.
다른 Domain의 GlobalExceptionHandler는 수정하지 않음.

## Authentication 연결

서버가 검증한 Principal.name을 Member ID로 사용하는 계약임. Request Body나 임의 Header의 Member ID는 신뢰하지 않음.
현재 저장소에는 Authentication 구현이 없어 일반 HTTP 요청은 401을 반환함.
API Test에서 Principal을 주입해 검증하며 실제 계정으로 사용하려면 Authentication 연결이 필요함.
테스트 편의를 위한 공개 Authentication 우회는 추가하지 않음.

## Mock PG

서버 설정 `app.payment.mock-outcome`으로 SUCCESS(기본값), DECLINED, TIMEOUT을 선택함.
SUCCESS는 Payment ID에 대응하는 `txn_mock_{paymentId}`를 반환함. 실제 PG 연동은 없음.
Integration Test에서는 MockBean으로 PG 응답과 대기 시간을 제어함.

## Test 실행

Java 17, backend 디렉터리 기준임.

```powershell
.\gradlew.bat test --tests 'com.artbid.payment.*' --tests 'com.artbid.settlement.*'
```

위 실행은 Unit/API Test 대상이며 PostgreSQL Test는 기본 Skip함.
PostgreSQL Integration Test까지 실행하려면 다음과 같이 설정함.

```powershell
$env:PAYMENT_POSTGRES_TEST='true'
.\gradlew.bat --no-daemon test --tests 'com.artbid.payment.*' --tests 'com.artbid.settlement.*' --rerun-tasks
Remove-Item Env:PAYMENT_POSTGRES_TEST
```

기본 연결은 로컬 PostgreSQL의 artbid DB이며 사용자/암호는 로컬 개발 설정과 동일함.
PAYMENT_TEST_DB_URL, PAYMENT_TEST_DB_USER, PAYMENT_TEST_DB_PASSWORD로 Test 연결을 별도 지정할 수 있음.
실행마다 `payment_it_` + UUID 형태의 독립 Schema를 사용하고 Context 종료 시 정리함.
Test의 Hibernate create-drop은 이 Schema에만 적용하며 기존 public Schema를 사용하지 않음.
Test DB 계정에는 Schema 생성 권한이 필요함. Test에서는 lock_timeout을 3초로 설정함.

검증 항목: 서버 금액 사용, Payment/Settlement 저장, PG 호출 시 Transaction 비활성,
PG 거절 후 새 시도, Timeout 후 추가 결제 차단, 권한/기한 검사,
결과 저장 실패 시 Rollback, 동시 요청 시 PG 호출 1회, 성공 결과 재수신 시 최초 기록 유지.

2026-09-24 실행 결과: 총 40건 통과, 실패/오류/Skip 0건임.
Payment Entity 20건, Settlement Entity 3건, Mock PG 3건, API 5건,
PostgreSQL Integration Test 9건으로 구성함. 기존 경매 등 다른 Domain의 Test는 실행하지 않음.
DB 재조회 시 시각이 달라지는 문제를 확인해 요청/결제 시각을 PostgreSQL의 Microsecond 정밀도로 맞춤.

## 남은 작업

- 낙찰 확정 → Settlement 생성 연결 및 중복 Settlement 생성 방지
- Authentication 연결과 Member ID 계약 확정
- Timeout 및 PG 성공 후 DB 저장 실패에 대한 거래 조회/Recovery. 현재 REQUESTED는 자동으로 해소되지 않음
- 기존 데이터 Migration: totalAmount, shippingFee, paymentDeadlineAt 및 Payment 상태 보정 필요함
- 기존 데이터의 실제 상태를 확인하기 전 일괄 PENDING/REQUESTED로 보정하지 않음
- 운영 DB Lock timeout과 부하 기준 설정. 현재 검증은 로컬 PostgreSQL 소규모 동시 요청 기준임
- 미결제 후속 처리, 판매자 Settlement Batch, 환불은 별도 범위로 논의함

기존 Settlement 조회 API의 Authentication/Response 정리는 이번 Payment API 변경 범위 밖임.
따라서 서비스 전체의 권한 검증이 완료된 상태는 아님.
