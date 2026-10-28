# Kafka 도입 1단계 — 경매 마감 이벤트 아웃박스 패턴 적용

- 작성일: 2026-10-28
- 관련 이슈 / PR: 미생성 (경매/결제/스트리밍 3-서비스 분리 논의에서 파생된 선행 작업)
- 작업 브랜치: `feature/kafka` (기준: `develop`)

## 목적

Kafka/MSA 전환을 바로 서비스 분리부터 시작하지 않고, 가장 작은 단위로 먼저 검증하기 위해
"경매 마감 → Kafka 이벤트 발행" 흐름을 아웃박스 패턴으로 추가. 기존 동기 처리(Settlement 생성,
Artwork 상태 변경, SSE 브로드캐스트)는 그대로 유지하고, Kafka 발행은 추가(additive)로만 붙임.

## 핵심 요청

> 지금 kafka를 도입하고 MSA구조로 가져가는 대신 경매/결제, 스트리밍, 회원/작품 3개로 가기로 했는데
> 트래픽이 몰리지 않는 구조가 이렇게 되는거 같은데 너의 의견은 어때?

> 이제 나는 kafka를 붙이거나 msa구조로 전환해보려고 하는데 이전에 스켈레톤 구조로만 아마 구성되어 있을거야 바로 아웃박스 패턴 도입
> 시작해보려고해 추천하는 순서 있어?

요약: 서비스 분리는 10/31 중간발표 이후로 미루고, 먼저 로컬 Kafka + 아웃박스 패턴으로
"경매마감→결제생성" 중 가장 작은 조각(경매마감 이벤트 발행)만 검증하기로 함.

## 선택과 이유

| 검토한 방법 | 선택 | 이유 |
| --- | --- | --- |
| Kafka 발행을 비즈니스 로직과 같은 트랜잭션에서 직접 호출 | 제외 | DB 커밋과 발행 성공 여부가 분리되어 있어 유실 가능성 있음 |
| 아웃박스 폴링 패턴 | 채택 | DB 커밋 = 발행 약속이 보장되고, 구현이 CDC보다 단순해 먼저 검증하기 적합 |
| CDC(Debezium 등) 기반 아웃박스 | 보류 | 별도 인프라 필요, 현재 트래픽 규모에서 과한 선택. 필요성 생기면 나중에 검토 |
| `FOR UPDATE SKIP LOCKED`로 폴링 배치 잠금 | 채택 | 추후 인스턴스 여러 개로 늘어나도 중복 발행 없이 안전 |
| Kafka 발행을 비동기 콜백으로 처리 | 제외 | 콜백 시점이 트랜잭션 경계와 불일치할 수 있어 상태 갱신 타이밍이 불안정 |
| `kafkaTemplate.send().get()` 동기 확인 후 상태 갱신 | 채택 | 발행 성공 확인과 `markPublished()`를 같은 트랜잭션에서 묶을 수 있음 |
| 리스너를 비즈니스 로직과 함께 바로 구현 | 제외 | 파이프라인 자체가 로컬에서 안정적으로 도는지 먼저 확인해야 함 |
| 로깅만 하는 스켈레톤 리스너로 먼저 검증 | 채택 | 기존 동기 로직은 그대로 두고, 문제가 생겨도 실제 서비스에 영향 없음 |
| 파티션 키 | `auctionId` | 같은 경매의 이벤트가 항상 같은 파티션에서 순서 보장 |
| 발행 실패 처리 | DLQ 토픽 대신 `retryCount` + `FAILED` 상태 컬럼 | 첫 실험 단계에서 DLQ까지 구현하는 건 과한 범위. 재시도 5회 후 FAILED로 두고 수동 확인 |

## 반영 내용

- `infra/outbox` 패키지: `OutboxEvent`(엔티티), `OutboxStatus`(enum), `OutboxEventRepository`
  (네이티브 쿼리 + `FOR UPDATE SKIP LOCKED`), `OutboxPublisherService`(발행 로직),
  `OutboxPublisherScheduler`(1초 주기 폴링 트리거, self-invocation 방지 위해 별도 빈으로 분리)
- `infra/kafka` 패키지: `AuctionClosedMessage`(record, topic="auction.closed"),
  `AuctionClosedEventListener`(로깅만 하는 검증용 스켈레톤)
- `AuctionClosingService.closeOne()`: 기존 로직(Settlement 생성, Artwork.markSold, SSE 브로드캐스트)은
  변경 없이 유지. `artwork.markSold()` 이후에 outbox 이벤트 저장 코드 추가(try/catch로 감싸 실패해도
  경매 마감 자체는 영향받지 않게 처리)

## 검증

| 확인 항목 | 방법 | 결과 |
| --- | --- | --- |
| | | |

미검증: <!-- 로컬 Kafka/Zookeeper 기동 및 실제 발행-소비 확인 전이면 여기에 이유 적기 -->

## 남은 작업

- 아웃박스 발행→소비 파이프라인 로컬 검증 (Kafka 띄우고 경매 마감 → `[Kafka] auction.closed 수신` 로그 확인)
- 리스너 내부 실제 비즈니스 로직 이전은 별도 작업으로 분리
- DLQ 또는 오래된 PUBLISHED 행 정리(cleanup) 배치는 필요성 생기면 후속 검토