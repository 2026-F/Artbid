# 작품, 경매, 미디어 권한 보완과 입찰 프론트 연동 정리

- 작성일: 2026-11-05
- 관련 이슈 / PR: 미생성. 작품 등록 임시 인증 처리는 BE #33 주석에서 출발했다.
- 브랜치: BE `fix/auth-ownership-checks`, FE `fix/bid-auth-sse` (둘 다 develop 기준)

## 목적

Kafka나 Redis Streams 도입 전에 프론트 연동 누락과 권한 구멍을 정리한다. 요청 본문의 회원 ID를 믿는 경로와 소유권 확인이 없는 경로를 막고, 입찰 화면이 현재 백엔드 계약(토큰 기반 입찰자, 이름 있는 SSE 이벤트)을 따르게 한다.

## 핵심 요청

> 카프카를 사용하거나 레디스 스트림을 사용하기전에 다 완료되어야 할 거 같아서 순서대로 진행해줘.

요약: 직전 분석에서 AI가 제안한 순서(1 입찰 연동, 2 권한 구멍, 3 검색과 알림, 4 결제와 정산과 배송)대로 진행한다. 이 기록은 1~3단계를 다룬다.

## 선택과 이유

| 검토한 방법 | 선택 | 이유 |
| --- | --- | --- |
| 작품 등록 위탁자를 토큰 회원으로 정함. 본문 `consignorId`는 필드에서 제거하고 보내도 무시 | 채택 | 기존 프론트와 `scripts/dev-data.mjs`가 보내는 본문을 깨지 않고 위조만 막는다 |
| 작품 등록 역할을 CONSIGNOR로 제한 | 채택 | 프론트 등록 화면과 판매자 프로필 규칙(`SellerProfileService`)이 같은 기준이다. ADMIN 등록 허용 여부는 미확인 |
| 작품 수정은 등록한 위탁자 또는 ADMIN | 채택 | CLAUDE.md의 리소스 소유권 확인 기준. ADMIN 허용은 심사 중 수정 가능성을 고려한 AI 판단이며 담당자 확인 필요 |
| 경매 생성은 ADMIN만, PREVIEW 작품만, 마감되지 않은 경매가 있으면 409 | 채택 | 상태 변경 API와 같은 관리자 기준. 심사 전 작품 공개와 중복 경매를 막는다 |
| 미디어 업로드 URL 발급과 완료 등록은 작품 위탁자 또는 ADMIN, objectKey는 `artworks/{id}/media/` 경로만 | 채택 | 남의 작품 업로드와 임의 객체 연결을 막는다 |
| Spring Security 도입으로 일괄 처리 | 보류 | 요청 범위를 넘는 구조 변경이다. 기존 컨트롤러별 확인 방식을 따랐다 |
| 프론트 SSE: 이름 있는 `auction-live`, `auction-tick`만 구독, version으로 역순 이벤트 무시 | 채택 | 이름 없는 이벤트는 백엔드가 이전 화면 호환용으로만 유지한다 |
| 프론트 폴링: SSE 끊김 동안에만 5초 폴링 | 채택 | 연결 중 중복 조회를 없앤다. EventSource 자동 재연결과 함께 동작한다 |
| 입찰 기록: `GET /api/auctions/{id}/bids` 재조회로 표시 | 채택 | SSE 이벤트에는 입찰자가 없다. 서버 기록 기준으로 "내 입찰"을 판단한다 |

## 반영 내용

### 백엔드 (`fix/auth-ownership-checks`)

1. `artwork/controller/ArtworkController.java`: 등록과 수정에서 Principal 회원 ID 필수(없으면 401 `AUTH_REQUIRED`), 등록은 CONSIGNOR만(403).
2. `artwork/dto/ArtworkCreateRequest.java`: `consignorId` 제거. `artwork/service/ArtworkService.java`: `register(consignorId, request)`, `update`에서 소유권 확인.
3. `artwork/exception/ArtworkAuthRequiredException.java` 추가, `ArtworkForbiddenException`에 메시지 생성자 추가, `ArtworkExceptionHandler`에 401 처리 추가.
4. `auction/controller/AuctionController.java`: 생성에 `requireAdmin`. `auction/service/AuctionService.java`: PREVIEW 확인과 미마감 경매 중복 확인, `@Transactional`.
5. `common/exception/AuctionCreationConflictException.java`(409), `media/exception/MediaAuthRequiredException.java`(401), `MediaAccessDeniedException.java`(403) 추가, `GlobalExceptionHandler`에 처리 추가. 응답은 이 핸들러의 기존 형식인 문자열 본문이다.
6. `media/controller/MediaController.java`, `media/service/MediaService.java`: 업로드 권한과 objectKey 경로 확인.
7. 테스트: `ArtworkControllerTest` 갱신(401, 403, 본문 consignorId 무시), `ArtworkOwnershipTest`, `AuctionCreationTest`, `MediaUploadPermissionTest` 추가, `AuctionControllerLiveTest`에 비관리자 생성 거절 추가.

### 프론트엔드 (`fix/bid-auth-sse`)

처음에는 세션 임시 폴더의 클론에서 작업했고 커밋 전에 폴더가 비워져 유실됐다. 사용자 지시로 로컬 FE 저장소에서 같은 내용을 다시 적용했다.

1. `components/BidPanel.tsx`: 랜덤 `bidderId`와 "입찰자 ID (데모용)" 입력 제거, 비로그인 시 로그인 안내, 401 메시지, 이름 있는 SSE 처리, 끊김 시에만 폴링, 서버 입찰 기록 표시.
2. `lib/api.ts`: `submitBid(auctionId, price)`, `fetchBids`, `subscribeAuctionEvents`를 이름 있는 이벤트 기준으로 변경, 알림 API 3개 추가.
3. `types/index.ts`: `Bid`를 백엔드 `BidResponse`에 맞춤, `AuctionLiveView`, `Notification`, `NotificationPage` 추가, `ArtworkCreateRequest.consignorId` 제거.
4. `components/ArtworkRegisterForm.tsx`, `app/mypage/seller/artworks/new/page.tsx`: `consignorId` 전송 제거.
5. `app/search/page.tsx`: `GET /api/artworks?keyword=` 검색과 페이지 이동. `components/PageNavigation.tsx`에 `keyword` 전달 추가.
6. `app/notifications/page.tsx`: 목록, 개별 읽음, 모두 읽음, 페이지 이동. `linkUrl`은 앱 내부 경로일 때만 이동한다.

## 검증

| 확인 항목 | 방법 | 결과 |
| --- | --- | --- |
| 백엔드 전체 테스트 | 로컬 PostgreSQL, Redis 실행 후 `./gradlew test` | 382개 중 실패 0, 건너뜀 9 (`PAYMENT_POSTGRES_TEST` 등 별도 활성화 대상) |
| 작품 등록 권한 | 로컬 서버 실제 API 호출 | 비로그인 401, BIDDER 403, CONSIGNOR 201. 본문 consignorId 999999는 무시되고 로그인 회원으로 저장 |
| 작품 수정 권한 | 실제 API | 비로그인 401, 타인 403, 본인 200 |
| 미디어 업로드 권한 | 실제 API | URL 발급 비로그인 401, 타인 403, 본인 200. 다른 작품 경로 objectKey 완료 등록 403 |
| 경매 생성 | 실제 API | 비로그인과 CONSIGNOR 403, 심사 전 작품 409, 승인 후 200, 중복 생성 409 |
| 입찰 | 실제 API | 비로그인 401(직접 호출 2회), 토큰 입찰 200, 입찰 기록의 bidderId가 토큰 회원과 일치. 스크립트 실행에서는 비로그인 호출이 1회 400을 반환했고 원인은 미확인이다 |
| SSE 이벤트 | `curl -N /ws/auctions/{id}` 구독 중 입찰 | `auction-live` SNAPSHOT, 이어서 `auction-live` BID 수신 |
| 알림, 검색 API | 실제 API | 알림 비로그인 401, 로그인 200. keyword 검색으로 테스트 작품 1건 조회 |
| 프론트 정적 검사 | `tsc --noEmit`, `next lint`, `next build` | 재적용 후 통과. 변경 파일에 lint 오류 없음 |

미검증:
- 프론트 화면을 브라우저에서 백엔드와 연결해 확인하지 않았다. 입찰 패널, 검색, 알림 화면은 타입 검사와 빌드, 백엔드 API 응답 확인까지만 했다.
- 미디어 실제 업로드(MinIO 또는 S3 PUT)는 하지 않았다. 권한 확인과 URL 발급까지만 확인했다.
- 동시에 같은 작품으로 경매를 생성하는 경합은 확인하지 않았다. 중복 확인은 조회 후 저장이라 동시 요청을 완전히 막지 못한다.

## 남은 작업

- 4단계 결제, 정산, 배송 화면은 상태 전이와 결제 타임아웃 처리 계약을 프론트와 먼저 맞춘 뒤 진행한다.
- `POST /api/artists`, `PATCH /api/artists/{id}`도 인증 없이 열려 있다. 이번 범위에서 바꾸지 않았다.
- 경매 생성 비로그인 응답은 기존 `requireAdmin` 방식이라 401이 아니라 403이다.
- presigned URL 업로드의 파일 크기 제한은 서명 조건에 없다. 유형은 기존 `MediaType` 판별을 따른다.
- `scripts/dev-data.mjs`는 여전히 `consignorId`를 보낸다. 동작에는 영향이 없다.
- PREVIEW에서 ONGOING으로 자동 전환하는 스케줄러는 없다. 정책(전환 시점) 확인이 필요하다.
- FE PR에서 이 기록을 연결한다.
