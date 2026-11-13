# 라이브 채팅과 세로 전체 화면 방송

- 작성일: 2026-11-13
- 관련 이슈 / PR: 미생성. 백엔드와 프론트 모두 `feature/live/portrait-chat` 브랜치(develop 기준)

## 목적

방송을 쇼츠처럼 세로로 화면을 꽉 채워 보여주고, 입찰자들이 방송 중에 서로 대화할 수 있게 한다.

## 핵심 요청

> 스트리밍 기능 개선을 하고싶은데 스트리밍 송출 시 쇼츠처름 화면을 꽉 체우는 세로방향으로 뜨도록 하고 라이브 체팅 기능을 추가하려고 해 입찰자들끼리 소통할 수 있도록

## 선택과 이유

아래 선택은 모두 AI가 정했다. 사용자가 따로 고른 항목은 없다.

| 검토한 방법 | 선택 | 이유 |
| --- | --- | --- |
| 세로 화면을 프론트에서 처리(세로 촬영 요청 + object-cover) | 채택 | IVS 스테이지 생성 API에는 화면 방향 설정이 없다. 송출 해상도는 송출 클라이언트가 정한다. 백엔드 변경 없음 |
| 채팅을 기존 경매 SSE(`GET /ws/auctions/{id}`)에 `auction-chat` 이벤트로 추가 | 채택 | 새 연결과 의존성 없이 붙는다. 이름 있는 이벤트라 기존 `auction-live`, `auction-tick` 구독 화면에 영향이 없다 |
| WebSocket(STOMP) 채널 신설 | 제외 | 의존성과 인증 경로가 새로 필요하다. 전송은 POST로 충분하다 |
| 전송 권한: 로그인 회원 전체 | 채택 | 요청의 '입찰자'를 로그인 회원으로 해석했다. 실제 입찰 이력이 있는 회원으로 좁힐지는 미확인 |
| 전송 가능 시점: 방송이 열려 있는 동안(LIVE, DISCONNECTED) | 채택 | 라이브 채팅이므로 방송이 없으면 409로 거절한다. 조회는 항상 가능 |
| 표시 이름: 회원 `name` 그대로 | 채택 | 닉네임 필드가 없다. 실명 노출 여부는 미확인 |
| 전송 제한: 회원별 경매마다 10초에 5건, Redis 고정 윈도우 | 채택 | 시청 토큰 발급 제한과 같은 방식이다. 수치는 임시값 |

## 반영 내용

백엔드 (`com.artbid.chat`)

| API | 권한 | 응답 |
| --- | --- | --- |
| `GET /api/auctions/{auctionId}/chat/messages?beforeId=&size=` | 누구나 | 오래된 순 목록. 기본 50건, 최대 100건 |
| `POST /api/auctions/{auctionId}/chat/messages` `{content}` | 로그인 회원 | 201과 메시지. 저장 후 SSE `auction-chat`으로 같은 내용 전파 |

- 메시지: `id, auctionId, senderId, senderName, host, content, createdAt`. `host`는 위탁자 여부이며 서버가 정한다.
- 오류는 `{code, message}` 형식. 401 `CHAT_AUTH_REQUIRED`, 409 `CHAT_NOT_AVAILABLE`, 429 `CHAT_RATE_LIMITED`, 400 `CHAT_INVALID_MESSAGE`(빈 값, 200자 초과, 본문 없음)
- 새 테이블 `chat_message`. 현재 설정(`ddl-auto: update`)으로 생성된다.
- 설정 `app.chat.rate-limit`, `app.chat.rate-limit-window` 추가

프론트 (2026-F/frontend)

- `LivePortraitShell`: 헤더와 하단 메뉴를 덮는 세로 전체 화면 틀. 넓은 화면에서는 가운데 9:16 기둥
- 시청 화면: 영상 `object-cover`, 음소거로 시작하고 소리 켜기 버튼 제공
- 송출 화면: 카메라를 720x1280(9:16)으로 요청. 시작, 종료, 공유를 영상 위에 배치
- `LiveChat`, `useLiveChat`: 최근 대화 조회, SSE 수신, 재연결 시 재조회, 전송과 오류 안내

2026-11-14 추가 (프론트)

> 일단 서버 끄고 6번 기능 추가해서 새로 pr올리자

요약: 6번은 AI가 제안한 개선 목록의 '방송 화면에 현재가와 입찰 버튼'이다.

- `LiveBidBar`: 채팅 입력창 위에 현재가, 남은 시간, 입찰 버튼을 띄운다. 경매 상세의 입찰 패널과 같은 `useAuctionLive`, `submitBid`를 쓴다. 백엔드 변경 없음
- 입찰은 두 단계(입찰 버튼 → 금액 선택 후 확정)로 잘못 누르는 것을 막는다. 금액은 최소 단위의 1, 5, 10배 버튼으로 고른다. AI가 정한 방식이다
- 송출 화면에는 입찰 버튼 없이 현재가와 남은 시간만 보여준다

## 검증

| 확인 항목 | 방법 | 결과 |
| --- | --- | --- |
| 채팅 도메인, 서비스, 컨트롤러 | `./gradlew test --tests "com.artbid.chat.*"` | 통과 |
| 비로그인 전송 거절, 본문의 회원 ID 무시, 잘못된 입력 | 위 컨트롤러 테스트(MockMvc 단독 구성) | 401, 토큰 회원으로 처리, 400 확인 |
| 백엔드 전체 테스트 | `./gradlew test` (로컬 PostgreSQL, Redis 미기동) | 468개 중 15개 실패, 31개 건너뜀. 14개는 `localhost:5432` 연결 거부로 컨텍스트 로드 실패, 1개는 `PaymentTransactionServiceNotificationTest`의 결제 시각 검증 실패. 채팅 테스트는 모두 통과. DB를 띄운 재실행은 하지 않았다 |
| 프론트 타입, 린트, 빌드 | `npx tsc --noEmit`, `npm run lint`, `npm run build` | 통과. 린트 경고는 기존 `<img>` 경고와 같은 종류 |

| 로컬 서버 실제 호출 (2026-11-14) | Docker PostgreSQL, Redis와 `bootRun`으로 띄운 서버에 curl | 조회 200과 빈 목록, 비로그인 전송 401 `CHAT_AUTH_REQUIRED`, 시청 페이지 200 |
| 입찰 막대 추가 후 프론트 (2026-11-14) | `npx tsc --noEmit`, `npm run lint`, `npm run build` | 통과 |

미검증: 방송 화면에서의 실제 입찰, 로그인 회원의 실제 전송과 SSE 수신, 브라우저 화면, 실기기 세로 촬영, AWS IVS 송출부터 시청까지. 로컬에 AWS 자격 증명이 없어 방송을 시작할 수 없었다.

## 남은 작업

- 표시 이름(실명 노출)과 전송 권한 범위(입찰 이력 필요 여부)를 팀에서 확인
- SSE 구독이 서버 메모리에 있어 인스턴스가 여러 대면 채팅이 일부 화면에 전달되지 않는다. 입찰 알림과 같은 제약이다.
- 2026-11-14 사용자가 메시지 브로커 필요성을 물었다. AI 의견은 서버를 늘릴 때 Redis Pub/Sub을 SSE 공통 지점(`AuctionSseRegistry`)에 넣는 것이다. 사용자 선택은 미확인이며 팀 논의 예정이다.
- 노트북 웹캠처럼 세로 촬영을 지원하지 않는 카메라는 가로로 송출되고 시청 화면에서 가운데가 잘린다.
- 메시지 삭제와 신고, 금칙어, 보관 기간은 없다.
- 프론트 `Livestream.status` 타입에 `DISCONNECTED`가 없다. 이번 범위에서 고치지 않았다.
