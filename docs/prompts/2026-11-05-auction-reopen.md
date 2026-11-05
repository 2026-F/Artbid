# 마감된 경매 다시 열기

- 작성일: 2026-11-05
- 관련 이슈 / PR: 미생성. 후속 작업 대상: [BE #105](https://github.com/2026-F/Artbid/pull/105), [FE #59](https://github.com/2026-F/frontend/pull/59)
- 브랜치: BE, FE 모두 `feature/auction/reopen` (develop 기준)

## 목적

시연을 위해 마감된 작품을 관리자가 다시 경매에 올릴 수 있게 한다. BE #105 이후 유찰 작품(`IN_AUCTION`)에 새 경매를 열 수 없던 문제도 함께 해결한다.

## 핵심 요청

> 시연용으로 다시 열려고하는거야 아까 피알은 머지를 다 해버렸어. 관리자가 다시 마감된 작품을 열수 있도록 설정하게 만들어줘

## 선택과 이유

| 검토한 방법 | 선택 | 이유 |
| --- | --- | --- |
| 같은 작품에 새 경매 생성 | 채택 | 이전 입찰과 정산이 이력으로 남고, 새 경매 ID라 Redis 실시간 캐시와 정산이 겹치지 않는다. 목록과 상세는 최대 경매 ID를 쓰므로 새 경매를 자동으로 보여준다 |
| 기존 경매 행을 SQL이나 API로 되살리기 | 제외 | 다시 마감될 때 같은 경매에 정산이 또 생긴다. `settlement.auction_id`에 유니크 제약이 없다. Redis 캐시 삭제도 필요하다 |
| 미결제 낙찰 정산을 무효 처리 | 채택 | 이전 낙찰자가 재경매 중인 작품을 결제하지 못하게 한다. AI 판단이며 운영 정책은 담당자 확인 필요 |
| 결제 완료 또는 결과 불명 결제가 있으면 거절 | 채택 | CLAUDE.md 기준. 결과 불명 결제는 PG에서 승인됐을 수 있다 |
| 무효 표시로 `FAILED` 사용, `expiredAt`은 비움 | 채택 | `payment_status` CHECK 제약으로 새 상태값을 저장할 수 없다. `expiredAt`으로 기한 만료와 구분한다 |
| 마감 시각 대신 진행 시간(분) 입력 | 채택 | 서버와 브라우저의 시간대 차이에 영향받지 않는다 |
| `CLOSED`에서 다른 상태로의 수동 변경 차단 | 채택 | 이 경로가 정산 중복을 만든다. 재오픈은 새 API로만 한다 |

## 반영 내용

### 백엔드

1. `auction/service/AuctionReopenService.java`: 재오픈 규칙. 경매와 정산 행을 잠근다.
2. `auction/controller/AuctionController.java`: `POST /api/auctions/{id}/reopen`, 본문 `{ durationMinutes }`, ADMIN 전용. 응답은 새 경매.
3. `auction/service/AuctionService.java`: 마감된 경매의 상태 되돌리기를 409로 거절.
4. `artwork/domain/Artwork.java`: `reopenForAuction()`. REJECTED 작품은 거절.
5. `settlement/domain/Settlement.java`: `cancelForReopen()`. `SettlementRepository.findAllByAuctionIdForUpdate()` 추가.
6. `common/exception/InvalidAuctionRequestException.java`(400)와 `GlobalExceptionHandler` 처리 추가.

### 프론트엔드

1. `lib/api.ts`: `reopenAuction` 추가.
2. `components/AdminArtworkRow.tsx`: 마감된 경매에 진행 시간 입력과 "다시 열기" 버튼 추가. 마감된 경매의 상태 선택은 비활성화.

## 검증

| 확인 항목 | 방법 | 결과 |
| --- | --- | --- |
| 백엔드 전체 테스트 | 로컬 PostgreSQL, Redis로 `./gradlew test --rerun-tasks` | 394개 중 실패 0, 건너뜀 9 |
| 권한과 입력 | 로컬 서버 실제 API | 비로그인과 BIDDER 403, 진행 시간 0은 400 |
| 유찰 재오픈 | 실제 API | 200, 새 경매 ONGOING, 현재가는 시작가, 작품 상세가 새 경매를 가리킴, 새 경매 입찰 200 |
| 미결제 낙찰 재오픈 | 실제 API와 DB 조회 | 200, 이전 정산 FAILED, 그 정산의 결제 시도 409 `SETTLEMENT_NOT_PAYABLE` |
| 재마감 | 실제 API와 DB 조회 | 이전 경매 정산 1건, 새 경매 정산 1건으로 중복 없음 |
| 결제 완료 건 | 로컬 DB에서 PAID로 바꾼 뒤 실제 API | 409, 작품 SOLD 유지 |
| 중복과 상태 되돌리기 | 실제 API | 같은 경매 재시도 409, 진행 중 경매 409, CLOSED에서 ONGOING 변경 409 |
| 프론트 정적 검사 | `npx tsc --noEmit`, `npm run lint`, `npm run build` | 통과 |

실제 API 시나리오는 Kafka 없이 실행했고 테스트 서버에만 `max.block.ms=300`을 주었다. 마감은 로컬 DB에서 마감 시각을 과거로 바꿔 유도했다.

미검증:
- 관리자 화면을 브라우저에서 눌러 확인하지 않았다.
- 결과 불명 결제 거절과 동시 재오픈은 단위 테스트와 코드 확인까지만 했다. 실제 동시 요청은 실행하지 않았다.

## Kafka 비활성화 (같은 날 추가)

> 아니야 카프카 안쓸거야

> 아예 안쓸거야 재오픈 기능이랑 비활성화로 디벨롭에 머지해줘

요약: AI가 시연 전 Kafka 연결 확인을 안내했고, 사용자가 Kafka를 쓰지 않기로 했다.

Kafka에 연결할 수 없으면 outbox 발행이 단일 스케줄러 스레드를 막아 경매 마감이 늦어진다. `jstack`으로 `KafkaProducer.waitOnMetadata` 대기를 확인했다. 낙찰과 정산은 `AuctionClosingService`에서 동기로 끝나고 Kafka 리스너는 로그만 남기거나 비어 있어 업무 영향이 없다.

| 검토한 방법 | 선택 | 이유 |
| --- | --- | --- |
| 설정으로 끄기 (`OUTBOX_PUBLISHER_ENABLED`, `KAFKA_LISTENER_AUTO_STARTUP`, 기본 false) | 채택 | 변경이 작고 되돌리기 쉽다 |
| Kafka 코드와 의존성 제거 | 보류 | 사용자가 Kafka를 아예 쓰지 않기로 했으나 이번 병합 범위는 재오픈과 비활성화로 지정했다. 제거는 후속 작업이다 |

## 남은 작업

- Kafka 코드와 의존성 제거, README와 CLAUDE.md의 Kafka 도입 예정 문구 수정은 후속 작업이다.
- 꺼둔 동안 낙찰 시 `outbox_event` 행은 PENDING으로 쌓인다. 다시 켜면 그때 발행된다.
- 무효 처리된 정산이 낙찰자 정산 내역에 결제 실패로 보인다. 알림은 보내지 않는다.
- `settlement.auction_id` 유니크 제약과 `closeOne`의 기존 정산 확인은 이번 범위에서 추가하지 않았다.
