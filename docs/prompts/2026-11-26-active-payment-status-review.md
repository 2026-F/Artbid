# 활성 결제 응답의 중복 상태 제거

- 작성일: 2026-11-26
- 관련 PR: [Artbid #154](https://github.com/2026-F/Artbid/pull/154)
- 관련 리뷰: [중복 상태와 enum 이름 제안](https://github.com/2026-F/Artbid/pull/154#issuecomment-7184724433)
- 연계 이슈: [frontend #92](https://github.com/2026-F/frontend/issues/92)

## 목적

REQUESTED 결제만 반환하는 활성 결제 조회에서 중복 상태를 제거한다.

## 핵심 요청

요약: 활성 결제 조회 응답에서 항상 REQUESTED인 ActivePayment.status를 제거하고 관련 테스트를 수정한다. 정산 enum 이름 변경은 이번 작업에서 제외하며, 검증을 마친 변경 사항을 커밋한다.

## 선택과 이유

| 검토한 방법 | 선택 | 이유 |
| --- | --- | --- |
| ActivePayment.status 제거 | 채택 | 사용자가 승인했다. 조회 조건이 REQUESTED이므로 payment의 존재가 같은 정보를 나타낸다. |
| 정산 PaymentStatus를 SettlementStatus로 변경 | 제외 | 사용자가 이번 작업에서 제외했다. |

## 반영 내용

- 활성 결제 응답의 payment 객체에는 id만 반환한다. settlementStatus, expiredAt, payment: null 계약은 유지한다.
- DTO와 생성 코드를 변경하고 컨트롤러 테스트에서 status 필드가 없음을 검증한다.
- PostgreSQL 통합 테스트는 반환된 ID로 기존 결제 조회 API의 서비스 메서드를 호출해 REQUESTED 상태를 검증한다.
- 개별 결제 조회의 PaymentResponse.status와 저장 상태, 중복 결제 방지 규칙은 유지한다.

## 검증

| 확인 항목 | 방법 | 결과 |
| --- | --- | --- |
| 변경 공백 검사 | git diff --check | 통과 |
| 전체 테스트 및 결제 PostgreSQL 통합 테스트 | Java 21, 격리된 로컬 PostgreSQL·Redis, PAYMENT_POSTGRES_TEST=true로 ./gradlew test | 통과: 479건, 건너뜀 22건, 실패 0건, 오류 0건 |

## 남은 작업

- frontend #92 연동에서 활성 결제의 payment.status 대신 payment 존재 여부를 사용한다. 다른 브랜치 및 배포본의 소비 여부와 실제 프론트 연동은 미검증이다.
- PR 설명의 “ID·상태”를 “ID”로 정정하고 이 기록을 연결한다.
