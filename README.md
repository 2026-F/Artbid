# ArtBid — 프로젝트 스켈레톤

## 서비스 소개

신진 작가나 소규모 갤러리가 작품을 위탁 등록하면, 며칠간의 사전 관람(프리뷰) 기간을 거쳐
정해진 시각에 실시간 온라인 경매가 열리는 서비스입니다. 서울옥션 온라인 경매, Christie's
Live 같은 실제 경매 플랫폼의 라이브 입찰 경험을 모바일 친화적으로 재현하되, 대형 경매사가
잘 다루지 않는 신진 작가·중저가 작품대에 집중해 차별화합니다.

핵심 장치는 **안티 스나이핑(연장 경매)**입니다. 마감 30초 전에 새 입찰이 들어오면 마감
시각을 자동으로 30초 연장해, 마지막 순간에만 몰리는 스나이핑을 막습니다. 이 규칙 자체가
"입찰 이벤트를 보고 상태를 어떻게 바꿀지 판단하는" 이벤트 기반 아키텍처를 자연스럽게
정당화하는 장치입니다.

기술적으로는 Redis + Lua로 동시 입찰을 원자적으로 판정하고, Kafka에는 확정된 입찰만
순서 보장된 로그로 쌓아 재생(replay)·감사가 가능하도록 설계했습니다. 자세한 배경과
전체 아키텍처는 노션 "아트비드(ArtBid) 프로젝트 길잡이" 문서를 참고하세요.

## 저장소 구성

이 저장소(`2026-F/Artbid`)는 **백엔드 전용**입니다. 프론트엔드(Next.js)는 별도 저장소로
분리해서 관리합니다 — Vercel 배포, Node/Gradle 툴체인 분리, 프론트/백엔드 각자 독립적인
빌드·배포 주기를 위해서입니다. 두 저장소 간 API 계약은 노션 API 명세 문서를 기준으로
맞춥니다.

## 폴더 구조

```
artbid/
├── docker-compose.yml   # MySQL, Redis, Kafka(+Zookeeper), MinIO 로컬 인프라
└── backend/             # Spring Boot (기능/도메인 기준 패키지 구조)
```

`backend`는 레이어(controller/service/...) 기준이 아니라 **도메인(기능) 기준**으로 먼저 나눴습니다.

```
com.artbid
├── artwork/      작품 위탁·조회
├── auction/      경매·입찰 (핵심 도메인, Redis Lua / Kafka 연결 지점)
├── settlement/   낙찰·정산
├── payment/      결제(모의 PG)
├── member/       회원
├── common/       예외 처리, HMAC 등 도메인 공통
└── infra/        kafka / redis / realtime(SSE) — 여러 도메인이 같이 쓰는 인프라 설정
```

각 도메인 패키지 안에서는 다시 controller/service/repository/domain으로 나눠서,
"입찰 기능을 고치고 싶다" → `auction` 패키지 하나만 보면 되도록 구성했습니다.

## 아직 안 채운 부분 (TODO로 표시해둠)

- `infra/redis/BidLuaExecutor` — compare-and-set + 안티 스나이핑 연장 Lua 스크립트
- `infra/kafka/BidEventConsumer` — 받은 이벤트를 MySQL에 영구 기록
- `infra/realtime` — 다중 인스턴스 확장 시 Redis Pub/Sub 중계 추가
- `payment` — 결제 실패 시 보상 트랜잭션(Saga) 흐름
- `common/util/HmacUtil` — 감정서·인증서 서명 검증

## 로컬 실행

1. 인프라 먼저 띄우기
   ```
   docker-compose up -d mysql redis zookeeper kafka minio
   ```
2. `backend/` 폴더를 IntelliJ로 열면 Gradle 프로젝트로 인식됩니다 (Gradle Wrapper는
   IntelliJ가 열 때 자동 생성해주거나, `gradle wrapper` 명령으로 직접 생성하면 됩니다).
3. `ArtBidApplication` 실행 → `localhost:8080`

## 다음 단계

- 로드맵 1단계(기획/설계)는 노션 문서에 정리되어 있습니다.
- 2단계(인프라 셋업)의 "Spring Boot 프로젝트 뼈대 구성"이 이 커밋으로 완료된 상태입니다.
- 3단계부터는 TODO로 표시된 자리에 실제 로직을 채우면 됩니다.
