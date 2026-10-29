# 경매 실시간 현재가·잔여시간 캐시 갱신과 브로드캐스트

- 작성일: 2026-10-29
- 관련 이슈 / PR: [경매 코어] 실시간 현재가·잔여시간 캐시 갱신 + 브로드캐스트 (이슈 번호 미확인), PR 생성 전

## 목적

입찰마다 DB를 다시 읽지 않고 현재가와 마감 시각을 조회하고, 상태 변경과 잔여시간을 구독 중인 화면에 보낸다.
제약: Kafka 도입 작업(`feature/kafka`)이 진행 중이고 Redis 역할은 README상 미정이었다.

## 핵심 요청


요약: AI가 캐시와 Kafka의 역할 차이를 설명하고 A(서버 내부 이벤트로 구현 후 Kafka로 발행 지점 교체)와 B(`feature/kafka` 위에서 구현)를 제시했다.


이슈 본문은 AI 작업 환경에서 GitHub 접근이 안 돼 확인하지 못했다. 요구사항은 이슈 제목과 위 대화 기준이다.

## 선택과 이유

| 검토한 방법 | 선택 | 이유 |
| --- | --- | --- |
| A. Spring 이벤트로 전달, Kafka 머지 후 발행 지점만 교체 | 채택 (사용자 선택, AI 추천) | 지금 진행 가능. `feature/kafka`와 겹치는 파일 최소화 |
| B. `feature/kafka` 브랜치 위에서 Kafka로 구현 | 보류 | 해당 브랜치 머지 전까지 PR 진행 불가, 로컬 Kafka 필요 |
| 캐시 위치: Redis | 채택 (AI 추천) | 로컬 인프라와 연결 설정이 이미 있음. 다중 인스턴스에서도 같은 값을 봄 |
| 캐시 위치: 서버 메모리 | 제외 | 단일 인스턴스에서만 유효 |
| 잔여시간: 변경 시 즉시 + 15초 주기 동기화 | 채택 (사용자 선택) | 화면 타이머를 서버 시각으로 보정하고 SSE 연결 유지 |
| 잔여시간: 변경 시에만 전송 | 제외 | 사용자가 위 방식을 선택 |
| 오래된 변경 방지: 행 락 안에서 잡은 시각을 version으로 저장, Lua로 비교 후 갱신 | 채택 (AI 결정) | 커밋 후 처리는 요청 스레드마다 따로 돌아 이전 입찰이 늦게 반영될 수 있음 |
| 캐시 미스 채우기: 키가 없을 때만 스냅샷 저장 | 채택 (AI 결정) | 스냅샷을 읽는 사이 커밋된 변경을 덮어쓰지 않기 위함 |
| 마감 반영: `AuctionClosingService` 수정 | 제외 | `feature/kafka`가 같은 생성자와 메서드를 수정 중 |
| 마감 반영: `AuctionClosingScheduler`에서 마감 성공 후 DB 값으로 이벤트 발행 | 채택 (AI 결정) | 마감 서비스는 건드리지 않음 |
| 기존 이름 없는 SSE(`BidResult`, `AuctionClosedEvent`) 교체 | 제외 | 프론트 `subscribeAuctionEvents`가 onmessage로 받는 중. 새 이벤트는 이름 있는 이벤트로 추가 |

조장(Kafka 담당) 확인 여부: 미확인. AI가 확인용 문장을 제안했고 사용자는 확인 결과를 언급하지 않았다.

## 반영 내용

- `auction/live`: 실시간 상태(`AuctionLiveState`), 화면 응답(`AuctionLiveView`), 변경 이벤트, 캐시 인터페이스, 조회 서비스, 이벤트 리스너, 주기 동기화 스케줄러
- `infra/redis/RedisAuctionLiveCache`: `auction:{id}:live` 해시. 마감 후 하루 보관(최소 1시간). Redis 오류는 로그만 남기고 DB로 대체
- `BidService`: 입찰과 취소 시 변경 이벤트 발행. 기존 커밋 후 SSE는 유지
- `AuctionClosingScheduler`: 마감 성공 후 CLOSED 상태 발행
- `AuctionSseRegistry`: 이름 있는 이벤트 전송, 구독 중인 경매 목록
- `RealtimeController`: 구독 직후 현재 상태 1회 전송
- API 추가: `GET /api/auctions/{id}/live`. SSE 이벤트 추가: `auction-live`, `auction-tick`
- 설정 추가: `app.auction.live.tick-interval-ms` (기본 15000)

## 검증

| 확인 항목 | 방법 | 결과 |
| --- | --- | --- |
| 단위 테스트 (상태 계산, 리스너, 서비스, 스케줄러, 컨트롤러, BidService 이벤트) | `./gradlew build` (AI 작업 환경, Linux, Java 21) | 통과 |
| Redis Lua 동작 (오래된 변경 무시, 스냅샷 우선순위, TTL, Redis 장애) | 실제 Redis 7에 연결한 테스트 | 통과 |
| 전체 테스트 | 같은 환경에서 PostgreSQL 16, Redis 7 실행 후 `./gradlew build` | 222개 중 실패 0, 건너뜀 9 (기존 조건부 테스트) |
| 실제 서버 흐름 | `bootRun` 후 DB에 경매 생성, curl로 SSE 구독, 조회, 입찰, 취소, 마감 | 스냅샷, 입찰(안티 스나이핑 연장 포함), 취소, 주기 동기화(3초로 줄여 확인), 마감 이벤트 순서대로 수신. Redis 값과 TTL 확인 |

미검증: Windows 로컬 실행, 프론트 화면 연동(프론트에 수신 코드 없음), 다중 서버 인스턴스, Kafka 연동.

## 남은 작업

- 프론트: `GET /api/auctions/{id}/live`와 `auction-live`, `auction-tick` 이벤트 수신. 이벤트의 `version`이 더 작으면 무시
- Kafka 머지 후: `AuctionLiveChangedEvent` 발행 지점을 아웃박스/Kafka로 교체하고, 다중 인스턴스 SSE 전파 방식 결정
- 마감된 경매에도 구독이 남아 있으면 `auction-tick`이 계속 나간다. 마감 후 전송 중단 여부 결정
- Redis 역할 범위는 팀 확정 필요 (README에 현재 사용 범위만 반영)
