# 경매 단위 방송 시작, 종료 API 권한 검사

- 작성일: 2026-11-28
- 관련 이슈 / PR: 이 브랜치 `fix/streaming-start-end-auth` PR. 발견 기록 [2026-11-25-live-show-multi-lot.md](2026-11-25-live-show-multi-lot.md) "발견한 기존 문제" (BE PR #153)

## 목적

`POST /api/auctions/{auctionId}/stream/start`, `/end`가 로그인과 위탁자 확인 없이 동작했다. 누구나 남의 방송을 켜고 끌 수 있고 유료 IVS 스테이지를 만들 수 있었다.

## 핵심 요청

요약: 라이브 시청 화면 백엔드 작업 목록(작업용 임시 문서, 작업 후 삭제)의 P0-4를 진행한다. 사용자가 "별도 fix PR로 진행"을 골랐다(AI 추천안).

## 선택과 이유

| 검토한 방법 | 선택 | 이유 |
| --- | --- | --- |
| 위탁자 본인과 관리자 허용 | 채택 | 작업 목록의 완료 조건. 같은 컨트롤러의 `/disconnect`, `/reconnect` 위탁자 확인을 그대로 쓴다 |
| 라이브 편성에 묶인 경매의 시작 차단(409) | 이 PR에서 제외 | `live_show_lot`이 PR #153에만 있다. 라이브 편성 후속 PR에서 넣는다 |
| IVS 호출을 트랜잭션 밖으로 이동 | 제외 | 이번 범위가 아니다. 라이브 편성 쪽은 PR #153 리뷰에서 반영했다 |

## 반영 내용

- `StreamingController.startStream`, `endStream`: 로그인 필수(401 `STREAMING_AUTH_REQUIRED`). 역할은 JWT 필터가 넣은 `JwtAuthenticationFilter.ROLE_ATTRIBUTE`만 쓴다
- `StreamingService.startStream`, `endStream`: 관리자가 아니면 경매 작품 위탁자 본인인지 확인한다(403 `STREAMING_ACCESS_DENIED`). 시작은 스테이지를 만들기 전에 확인한다. 종료는 방송 조회(404)보다 먼저 확인한다
- 응답 형식과 성공 상태 코드는 그대로다

FE 영향: `frontend/src/lib/api.ts`의 `startStream`, `endStream`은 axios 인터셉터로 토큰을 붙인다. 위탁자로 로그인한 화면에서는 바뀌는 것이 없다. 비로그인이나 다른 회원이 호출하던 화면이 있으면 401, 403을 받는다.

## 검증

| 확인 항목 | 방법 | 결과 |
| --- | --- | --- |
| 비로그인 401 | `StreamingControllerTest` start, end | 통과 |
| 타인 403 | `StreamingControllerTest`, `StreamingServiceTest`(다른 위탁자, 비로그인, 없는 경매) | 통과. 스테이지를 만들지 않음 확인 |
| 관리자 허용 | `StreamingServiceTest` | 통과 |
| 역할 위조 | 헤더의 역할 값을 무시하는지 `StreamingControllerTest` | 통과 |
| streaming 패키지 | `./gradlew test --tests 'com.artbid.streaming.*'` | 49건 통과 |

미검증: 실제 JWT 필터를 거친 요청, 실제 IVS 호출(유료 자원).
