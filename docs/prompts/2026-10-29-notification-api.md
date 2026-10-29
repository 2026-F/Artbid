# 알림 목록 조회·읽음 처리 API

- 작성일: 2026-10-29
- 관련 이슈 / PR: #40, PR 생성 전

## 목적

회원이 자기 알림을 목록으로 보고 읽음 처리할 수 있게 한다.
제약: 백엔드에 알림 코드가 없었다. 프론트 `/notifications` 화면은 빈 목록과 비활성화된 "모두 읽음" 버튼만 있다. 알림을 만드는 곳이 없으면 목록이 항상 비어 있다.

## 핵심 요청

> [회원·결제·정산] 알림 목록 조회·읽음 처리 API

이슈 본문은 AI 작업 환경에서 GitHub 접근이 안 돼 확인하지 못했다.

요약: AI가 알림 생성 범위(조회·읽음 + 생성 기능만 / 생성 기능 + 결제 완료 연결 / 낙찰·결제·발송 모두 연결)를 물었다.
사용자 선택: 생성 기능 + 결제 완료 연결.

## 선택과 이유

| 검토한 방법 | 선택 | 이유 |
| --- | --- | --- |
| 생성 기능 + 결제 완료만 연결 | 채택 (사용자 선택, AI 추천) | 결제 코드는 다른 진행 중 브랜치와 겹치지 않음 |
| 낙찰·발송까지 연결 | 보류 | 경매 마감은 `feature/kafka`, 발송은 배송 PR과 겹쳐 충돌 가능 |
| 생성 방식: 커밋 후 이벤트 리스너, 새 트랜잭션 | 채택 (AI 결정) | 롤백된 결제에 알림이 가지 않음. 알림 저장 실패가 결제를 롤백하지 않음 |
| 읽음 상태: readAt 하나로 관리 | 채택 (AI 결정) | 읽은 시각까지 보존. 불리언 컬럼 중복 없음 |
| 다른 회원 알림 읽음 처리: 404 | 채택 (AI 결정) | 알림 존재 여부를 노출하지 않음 |
| 모두 읽음: 일괄 UPDATE 한 번 | 채택 (AI 결정) | 알림 수만큼 조회·저장하지 않음 |
| 목록 응답: 프론트 공통 PageResponse 모양 + unreadCount | 채택 (AI 결정) | 프론트 타입 재사용, 배지·버튼 활성화에 사용 |
| 알림 종류 enum에 후속 값 미리 정의 | 채택 (AI 결정) | 기존 DB CHECK 제약 문제 예방 (정산 배치, 배송 작업과 같은 이유) |
| 링크: 앱 내부 경로(/로 시작)만 허용 | 채택 (AI 결정) | 외부 주소로 보내는 링크 방지 |

## 반영 내용

- `notification` 패키지: `Notification`, `NotificationType`, 리포지토리, `NotificationService`, `NotificationRequestedEvent`, `NotificationEventListener`, 컨트롤러, 예외 처리
- `GET /api/notifications?page=&size=` (size 1~50, 기본 20), `GET /api/notifications/unread-count`
- `PATCH /api/notifications/{id}/read`, `PATCH /api/notifications/read-all`
- `PaymentTransactionService.complete`: 처음 결제 완료될 때 낙찰자에게 PAYMENT_COMPLETED 알림 요청
- README 현재 구현에 알림 항목 추가

## 검증

| 확인 항목 | 방법 | 결과 |
| --- | --- | --- |
| 도메인, 서비스, 리스너, 컨트롤러, 결제 알림 단위 테스트 | `./gradlew build` (AI 작업 환경, Linux, Java 21) | 통과 |
| 커밋 후 생성, 롤백 시 미생성, 모두 읽음 | PostgreSQL 16 통합 테스트 | 통과 |
| 전체 테스트 | PostgreSQL 16, Redis 7 실행 후 `./gradlew build` | 210개 중 실패 0, 건너뜀 9 (기존 조건부 테스트) |
| 실제 서버 흐름 | `bootRun` 후 로그인, Mock PG 결제 | 결제 PAID 후 알림 1건 생성, 읽음 처리, 안 읽은 개수 0 |
| 권한과 잘못된 입력 | 같은 서버 | 비로그인 401, 다른 회원 알림 읽음 404, 다른 회원 목록에 미노출, size=100은 400 |
| 기존 조건부 테스트 `PaymentFlowPostgresTest` | `PAYMENT_POSTGRES_TEST=true`로 실행 | 9개 실패. 변경 전 develop에서도 같은 9개 실패 확인. 원인: 테스트 설정이 `TossPaymentClient` 빈을 등록하지 않음 (Toss 연동 이후 기존 문제) |

미검증: Windows 로컬 실행, 프론트 연동.

## 남은 작업

- 낙찰(경매 마감), 발송, 위탁자 지급 대기, 결제 기한 임박 알림 연결
- 실시간 알림 전달(SSE 등) 필요 여부
- 오래된 알림 보관 기간과 삭제
- `PaymentFlowPostgresTest` 설정 수정 (결제 담당 확인)
