# 라이브 편성 2단계: 참여 토큰, SSE, 채팅, 작품 정보, 수수료 조회

- 작성일: 2026-11-28
- 관련 이슈 / PR: [BE PR #157](https://github.com/2026-F/Artbid/pull/157) (base `feature/live/live-show`). 선행 [BE PR #153](https://github.com/2026-F/Artbid/pull/153), 설계 원본 [2026-11-25-live-show-multi-lot.md](2026-11-25-live-show-multi-lot.md). 경매 방송 권한은 별도 [BE PR #156](https://github.com/2026-F/Artbid/pull/156), 기록 [2026-11-28-streaming-start-end-auth.md](2026-11-28-streaming-start-end-auth.md)

## 목적

FE가 시청 화면을 경매 단위에서 라이브 편성 단위(`/live-shows/{id}`)로 옮길 수 있게 남은 백엔드 API를 만든다.

## 핵심 요청

> `docs/live-show-backend-tasks.md` 이거 보고 작업 이어서 해줘. 작업 끝나면 저 md파일은 삭제하고 pr 올려줘

요약: 작업 목록(작업용 임시 문서, 커밋하지 않음)의 P0-1~P0-4, P1-5, P1-6, P2-7을 진행한다. P2-8(경매 단위 API 제거)은 FE 전환 PR 병합이 선행 조건이라 제외했다. 임시 문서의 남은 작업과 FE 연동 순서는 아래 "남은 작업"으로 옮겼다.

AI가 결정 두 가지를 물었고 사용자가 모두 추천안을 골랐다.

| 질문 | 사용자 선택 |
| --- | --- |
| 수수료, 배송비 조회 방식 | A. `GET /api/fee-policy` → `{ buyerPremiumRate, shippingFee }` |
| P0-4(경매 방송 시작, 종료 권한) | 별도 fix PR (BE PR #156). 편성에 묶인 경매의 409 차단은 `live_show_lot`이 필요해 이 PR에 넣었다 |

## 선택과 이유

| 검토한 방법 | 선택 | 이유 |
| --- | --- | --- |
| 라이브 토큰의 비로그인 시청 | 허용 | 작업 목록대로 기존 경매 토큰 동작을 코드로 확인해 맞췄다(`StreamingTokenService`: 비로그인 SUBSCRIBE, IP별 발급 제한) |
| SSE 등록부를 `AuctionSseRegistry`와 공유 | 제외, 라이브 전용 `LiveShowSseRegistry` | 같은 타입 빈이 둘이 되거나 기존 경매 SSE를 바꿔야 한다. 등록, 해제 방식만 따랐다(AI 판단) |
| show-state 전송 시점 | 커밋 후(`@TransactionalEventListener`) | 롤백된 전환을 알리지 않는다. 경매 SSE와 같은 원칙 |
| 라이브 채팅을 경매 채팅 조회에 섞기 | 제외 | 라이브 채팅도 `auctionId`를 채우므로 경매 채팅 조회는 `showId`가 없는 메시지만 본다. 기존 데이터는 모두 `showId`가 null이라 기존 동작은 같다(AI 판단, 사용자 확인 전) |
| 라이브 채팅 SSE | `show-chat`으로만 보냄 | 경매 SSE(`auction-chat`)로는 보내지 않는다 |
| 수수료 상수 | `FeePolicy` 한 곳에 모음 | 정산 생성과 조회 API가 같은 값과 반올림(`Math.round(finalPrice * rate)`)을 쓴다. 값은 그대로(10%, 30,000원) |
| 남은 스테이지 정리 | 5분 주기 스케줄러, 50개씩 | 삭제 성공 시에만 `stageArn`을 비워 다시 시도하지 않는다. 주기와 개수는 AI 판단 |

## 반영 내용

### API (신규, 기존 응답 필드는 유지)

| API | 권한 | 내용 |
| --- | --- | --- |
| `POST /api/live-shows/{showId}/stream/tokens` | 누구나 | 진행자는 PUBLISH, 그 외(비로그인 포함) SUBSCRIBE. LIVE가 아니면 409, 없으면 404, AWS 실패 502 `IVS_UNAVAILABLE`, 발급 제한 429 `STAGE_TOKEN_RATE_LIMITED`. 응답은 `StageTokenResponse`(stageArn 없음) |
| `GET /ws/live-shows/{showId}` | 누구나 | 이벤트 `show-state`(`ShowStateResponse`: showId, status, currentLotId, currentLot), `show-chat`(`ChatMessageResponse`). 구독 직후 현재 상태를 한 번 보낸다. 없는 라이브는 바로 닫는다 |
| `GET /api/live-shows/{showId}/chat/messages?beforeId=&size=` | 누구나 | 오래된 것부터. 없는 라이브 404 |
| `POST /api/live-shows/{showId}/chat/messages` | 로그인 | 비로그인 401, 빈 내용과 200자 초과 400 `LIVE_SHOW_INVALID_REQUEST`, LIVE 아님 409, 닉네임 없음 409 `CHAT_NICKNAME_REQUIRED`, 속도 제한 429 `CHAT_RATE_LIMITED` |
| `GET /api/fee-policy` | 누구나 | `{ buyerPremiumRate: 0.1, shippingFee: 30000 }` |

### 응답과 스키마 변경

- `LotResponse`에 `artistName`, `productionYear` 추가. 작가가 없으면 `artistName`은 null. 경매, 작품, 작가를 각각 한 번씩 모아 조회한다
- `ChatMessageResponse`에 `showId` 추가(경매 채팅이면 null). 경매 채팅 SSE와 조회 응답에도 필드가 하나 늘어난다
- `chat_message.show_id`(nullable)와 `idx_chat_message_show_id (show_id, id)` 추가. `ddl-auto: update`로 생성된다
- 라이브 채팅의 `auctionId`는 보낸 시점의 현재 작품 경매, 첫 작품을 열기 전이면 1번 작품 경매(설계 원본)

### 동작 변경

- `LiveShowService`: 시작, 다음 작품 열기, 종료 때 `LiveShowStateChangedEvent` 발행. 종료 후 스테이지를 지우면 `stageArn`을 비운다. 삭제에 실패하면 남겨 두고 `LiveShowStageSweeper`(`app.live-show.stage-sweep-interval-ms`, 기본 300000)가 다시 지운다
- `StreamingService.startStream`: 라이브 편성에 들어 있는 경매는 409 `LIVESTREAM_IN_LIVE_SHOW`로 막는다. 스테이지를 만들기 전에 확인한다
- `SettlementCreationService`: 상수를 `FeePolicy`로 옮겼다. 계산 결과는 같다

다른 담당 영역 변경: 채팅(`ChatMessage`, `ChatMessageResponse`, `ChatMessageRepository` 메서드 이름, `LiveChatService`), 방송(`StreamingService`, `StreamingExceptionHandler`), 정산(`SettlementCreationService`, `FeePolicy`, `FeePolicyController`).

## 검증

| 확인 항목 | 방법 | 결과 |
| --- | --- | --- |
| 라이브 토큰, 채팅, SSE, 상태 이벤트, 작품 정보, 스테이지 정리 | 단위 테스트 52건(`com.artbid.liveshow.*`, 목 저장소와 MockMvc) | 통과 |
| 권한 | 비로그인 채팅 401, 진행자 외 PUBLISH 미발급, 시작 전, 종료 라이브 토큰 409 | 통과 |
| 잘못된 입력 | 빈 채팅, 201자, 없는 라이브 404, 없는 라이브 SSE 즉시 종료 | 통과 |
| 편성에 묶인 경매 방송 시작 409 | `StreamingServiceTest` | 통과. IVS 미호출 |
| 수수료 반올림 | `FeePolicyControllerTest` | 통과 |
| 전체 테스트 | 로컬 Docker PostgreSQL, Redis를 띄우고 `./gradlew test` | 552건 모두 통과(컨텍스트 로드, PostgreSQL 통합 테스트 포함. 결제 DB 통합 테스트는 `PAYMENT_POSTGRES_TEST` 미설정으로 제외) |
| 스키마 | 로컬 PostgreSQL `\d chat_message`, `pg_indexes` | `show_id` 열, `idx_chat_message_show_id`, `live_show`와 `live_show_lot` 인덱스, `uk_live_show_lot_auction_id` 확인 |
| 실제 서버 | `./gradlew bootRun` 후 curl | `GET /api/fee-policy` 200, `GET /api/live-shows?status=LIVE` 200, 없는 라이브 토큰과 채팅 조회 404, 비로그인 채팅 401, 없는 라이브 SSE 즉시 종료 |

검증 중 사고: 실제 서버 확인 때 이 브랜치(PR #156 권한 수정 없음)에서 비로그인으로 `POST /api/auctions/1/stream/start`를 호출해 IVS 스테이지 생성 시도가 일어났다. 이 PC에 AWS 자격 증명이 없어 502로 끝났고 스테이지 생성 로그도 없어 자원은 생성되지 않았다. 기존 문제(PR #156에서 수정)가 그대로 재현된 것이다.

미검증: 실제 IVS 토큰 발급과 스테이지 삭제(유료 자원), 로그인한 진행자로 실제 라이브를 만들어 SSE로 `show-state`, `show-chat`을 받는 흐름, 여러 서버 인스턴스에서의 SSE 전달(경매 SSE와 같이 인스턴스 메모리 기준).

## 남은 작업

작업용 임시 문서(`docs/live-show-backend-tasks.md`, 커밋하지 않고 삭제)에서 옮긴 항목이다.

| 순서 | 저장소 | 내용 | 선행 |
| --- | --- | --- | --- |
| 1 | FE | 시청 화면을 `/live-shows/{id}` 기준으로 전환. 작품별 탭, Lot 번호(`lotNo`와 `lots.length`), 라이브 제목. `show-state`를 받으면 `currentLot.auctionId`의 경매 SSE로 구독을 옮긴다 | PR #153과 이 PR 병합 |
| 2 | FE | 작품 카드의 작품 상세 추가 호출을 `LotResponse.artistName`, `productionYear`로 대체. `frontend/src/lib/fees.ts` 상수를 `GET /api/fee-policy` 응답으로 대체 | 1 |
| 3 | FE | `/live` 로비와 세로 넘기기(`GET /api/live-shows?status=LIVE`) | PR #153 |
| 4 | BE | 경매 단위 방송, 토큰, 채팅 API 제거(작업 목록 P2-8) | FE 전환 병합 |

- 라이브 채팅을 경매 채팅 조회에서 빼는 결정은 담당자 확인이 필요하다
- PR #156과 함께 병합하면 `StreamingService.java`는 자동 병합되고 `StreamingServiceTest.java`가 충돌한다(`git merge-tree`로 확인). 이 PR의 409 테스트가 `startStream(1L)`을 쓰므로 나중에 병합하는 쪽에서 `startStream(1L, 5L, MemberRole.CONSIGNOR)`로 맞춘다
