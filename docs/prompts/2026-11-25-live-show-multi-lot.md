# 여러 작품을 연달아 진행하는 라이브 편성

- 작성일: 2026-11-25
- 관련 이슈 / PR: [BE PR #153](https://github.com/2026-F/Artbid/pull/153) (1단계). 이전 작업 [2026-11-13-live-chat-portrait.md](2026-11-13-live-chat-portrait.md) (BE #125, FE #77)

## 목적

지금은 방송 1개가 경매 1개(작품 1개)에 묶여 있다(`Livestream.auctionId`). 테마 라이브(예: 올해의작가특별전) 하나에서 여러 작품을 차례로 진행하고, 시청자가 영상과 작품 정보를 함께 보며 작품별로 입찰하게 한다. 동시에 열린 라이브는 숏폼처럼 넘겨 본다.

## 핵심 요청

> 하나의 라이브에서 여러 작품을 연달아 방송할 수 있어야함 ... 영상 + 작품상세 볼수있게 띄워줘야함 / 각각의 작품마다 낙찰할 수 있는 탭 필요함 / 그리고 라이브 여러개가 동시에 열릴경우 숏폼처럼 넘기는거 / + 라이브챗 DB조사 및 추가

AI가 정책 질문 4개를 하고 사용자가 답했다.

| 질문 | 사용자 답 |
| --- | --- |
| 다음 작품으로 넘기는 방식 | 진행자가 넘긴다 |
| 작품별 마감 방식 | 지금처럼(정해진 진행 시간 + 마감 30초 이내 입찰 시 30초 연장) |
| 차례가 안 온 작품에 미리 입찰 | 없음 |
| 라이브 편성을 만드는 사람 | 위탁자 |

## 현재 상태 (2026-11-25 코드 기준)

| 요구사항 | 상태 |
| --- | --- |
| 한 라이브에 여러 작품 | 없음. 방송, 참여 토큰, 채팅, SSE가 모두 경매 ID 기준 |
| 영상 + 작품 상세 | 프론트 시청 화면에 단일 작품 카드와 입찰 시트(미커밋, FE `docs/prompts/2026-11-25-live-bid-sheet-ui.md`) |
| 작품별 입찰 탭 | 없음 |
| 숏폼 넘기기 | 없음. 방송 중 목록 API 없음 |
| 라이브챗 | `chat_message` 테이블, 조회/전송 API, SSE, 속도 제한, 닉네임까지 완료(BE #125). 경매 ID 기준 |

## 설계안 (AI 제안, 검토 전)

### 도메인

경매는 지금처럼 관리자가 만든다. 위탁자는 자기 작품의 경매 중 아직 시작하지 않은 것을 골라 라이브에 묶는다. 각 Lot은 기존 `Auction`을 그대로 쓴다. 입찰, 마감, 정산, 결제 로직은 바꾸지 않는다.

| 엔티티 | 주요 필드 |
| --- | --- |
| `LiveShow` | id, hostId(위탁자 회원), title, description, scheduledAt, status(SCHEDULED, LIVE, ENDED), currentLotId, stageArn, startedAt, endedAt |
| `LiveShowLot` | id, showId, auctionId(유일), lotNo, durationMinutes, status(WAITING, OPEN, DONE), openedAt |

### 규칙

| 항목 | 규칙 |
| --- | --- |
| 편성 생성 | CONSIGNOR만. 모든 경매의 작품 위탁자가 요청자 본인. 경매 상태가 SCHEDULED 또는 PREVIEW. 다른 라이브에 들어 있지 않음 |
| 미리 입찰 금지 | Lot을 열기 전 경매는 PREVIEW 이하로 남아 기존 `assertBiddable`이 거절한다. 관리자 수동 상태 변경(`PATCH /api/auctions/{id}/status`)도 라이브에 묶인 경매는 막는다 |
| 다음 작품 열기 | 진행자만. 라이브가 LIVE이고 현재 Lot의 경매가 CLOSED일 때만. 다음 Lot 경매를 ONGOING, 마감 = 지금 + durationMinutes로 바꾸고 `AuctionLiveChangedEvent`를 발행해 입찰 캐시를 비운다(재오픈과 같은 방식) |
| 마감 | 기존 스케줄러와 30초 연장 규칙 그대로. 진행자가 앞당겨 끝내지 않는다 |
| 권한 | 시작, 종료, 다음 작품은 서버가 검증한 회원 ID와 `LiveShow.hostId`로 확인. 비로그인 401, 타인 403 |

### API (신규, 기존 API는 유지)

| API | 권한 | 용도 |
| --- | --- | --- |
| `POST /api/live-shows` | 위탁자 | 편성 생성(title, scheduledAt, lots[auctionId, durationMinutes]) |
| `GET /api/live-shows?status=LIVE` | 누구나 | 로비와 숏폼 목록. 현재 Lot의 작품 제목, 이미지, 현재가 포함 |
| `GET /api/live-shows/{id}` | 누구나 | 라이브 정보와 Lot 목록(작품 요약, 상태, 현재가, 마감 시각). 작품별 탭에 사용 |
| `POST /api/live-shows/{id}/start`, `/end` | 진행자 | IVS 스테이지 생성, 삭제 |
| `POST /api/live-shows/{id}/lots/next` | 진행자 | 다음 작품 열기 |
| `POST /api/live-shows/{id}/stream/tokens` | 누구나 | 진행자 PUBLISH, 그 외 SUBSCRIBE |
| `GET/POST /api/live-shows/{id}/chat/messages` | 조회 누구나, 전송 로그인 | 라이브 단위 채팅 |
| SSE `GET /ws/live-shows/{id}` | 누구나 | `show-state`(Lot 전환, 상태), `show-chat` |

현재 Lot의 현재가와 남은 시간은 기존 `/ws/auctions/{auctionId}` SSE를 그대로 쓰고, Lot이 바뀌면 프론트가 구독을 옮긴다.

### 채팅

`chat_message`에 `showId`(nullable)를 추가하고 `(showId, id)` 인덱스를 둔다. 라이브 채팅은 작품이 바뀌어도 한 대화로 이어진다. `auctionId`는 보낸 시점의 현재 Lot 경매로 채운다(첫 Lot을 열기 전이면 1번 Lot). 기존 경매 채팅 API는 그대로 둔다.

### 작업 순서

| 순서 | 저장소 | 내용 |
| --- | --- | --- |
| 1 | BE | 편성 도메인, 생성, 조회, 시작/종료, 다음 작품, 관리자 상태 변경 차단 |
| 2 | BE | 라이브 단위 참여 토큰, SSE, 채팅 |
| 3 | FE | 시청 화면(영상 + 작품 카드 + 작품별 탭 + 입찰 시트), 진행자 화면(다음 작품 버튼), 위탁자 편성 생성 |
| 4 | FE | `/live` 로비와 세로 스와이프 숏폼 |

## 추가 확인 (2026-11-25)

AI가 추천안을 붙여 물었고 사용자가 모두 추천안을 골랐다.

| 항목 | 선택 |
| --- | --- |
| 기존 경매별 방송 API 처리 | 당분간 유지하고 새 화면은 라이브 편성만 사용. 이후 제거 |
| 방송 종료 시 아직 안 연 작품 | 편성에서 빠져 대기 상태로 돌아가고, 다른 라이브에 다시 넣을 수 있음 |
| Lot 진행 시간 | 위탁자가 작품마다 1~60분으로 정함 |

AI가 추가로 정한 것(사용자 확인 전):

- 진행 중인 작품이 있을 때 방송을 끝내도 그 경매는 원래 마감 시각까지 계속 입찰을 받는다. 진행자가 경매를 앞당겨 끝내지 않는다는 답(마감은 지금처럼)에 맞췄다.
- 끝난(ENDED) 라이브는 다시 시작하지 않는다. 남은 작품은 새 라이브로 편성한다.

## 반영 내용 (1단계, 2026-11-25)

브랜치 `feature/live/live-show` (origin/develop 기준)

- `com.artbid.liveshow`: `LiveShow`, `LiveShowLot` 엔티티(새 테이블 `live_show`, `live_show_lot`, `ddl-auto: update`로 생성), 서비스, 컨트롤러, `{code, message}` 예외 처리
- API: `POST /api/live-shows`, `GET /api/live-shows?status=`, `GET /api/live-shows/{id}`, `POST /api/live-shows/{id}/start`, `/lots/next`, `/end`
- 오류 코드: 401 `LIVE_SHOW_AUTH_REQUIRED`, 403 `LIVE_SHOW_FORBIDDEN`, 404 `LIVE_SHOW_NOT_FOUND`, 409 `LIVE_SHOW_CONFLICT`, 400 `LIVE_SHOW_INVALID_REQUEST`
- `Auction.openForLive`: 시작 전 경매를 지금부터 진행 시간 동안 ONGOING으로 연다
- `AuctionService.updateStatus`: 라이브에 묶인 경매는 관리자 수동 상태 변경을 409로 막는다
- `IvsStageClient.createShowStage`: 라이브 단위 스테이지 생성(이름 `artbid-show-{id}`)
- 다음 작품 열기는 라이브 행을 잠그고(`SELECT ... FOR UPDATE`) 처리해 연속 클릭에도 한 작품만 열린다. 경매 상태 변경 이벤트를 발행해 입찰 캐시를 비운다

### 검증

| 확인 항목 | 방법 | 결과 |
| --- | --- | --- |
| 편성, 시작, 다음 작품, 종료 규칙 | `LiveShowServiceTest` 14건(목 저장소) | 통과 |
| 권한 거절 | 같은 테스트: 비위탁자, 역할 없음, 남의 작품, 비진행자의 시작/다음/종료 | 403 예외 확인 |
| 잘못된 입력 | 빈 목록, 중복 경매, 진행 시간 61분, 빈 제목 | 400 예외 확인 |
| 관리자 수동 열기 차단 | `AuctionCreationTest` 6건 | 통과 |
| 전체 테스트 | `./gradlew test` | 511건 중 15건 실패. 14건은 로컬 PostgreSQL 미실행으로 컨텍스트 로드 실패. 1건(`PaymentTransactionServiceNotificationTest`)은 결제 시각 비교 오류로 한 번 실패했으나 단독 재실행 3회와 develop 원본에서 모두 통과해 시각 의존으로 흔들리는 기존 테스트로 판단 |

미검증: 실제 PostgreSQL에서 테이블 생성과 유일 제약, 행 잠금 동작, 실제 IVS 스테이지 생성. 컨트롤러 계층의 401 응답(Principal 없음)은 테스트를 작성하지 않았다.

## 발견한 기존 문제

- `POST /api/auctions/{id}/stream/start`, `/end`는 로그인과 위탁자 확인이 없다(`StreamingController`, `StreamingService.startStream`, `endStream`). 누구나 남의 방송을 켜고 끌 수 있고, 유료 IVS 스테이지를 만들 수 있다. 별도 fix 브랜치로 다룬다.
