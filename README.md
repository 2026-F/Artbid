# ArtBid

## 서비스 소개

 작가나 소규모 갤러리가 작품을 위탁 등록하면, 며칠간의 사전 관람(프리뷰) 기간을 거쳐
정해진 시각에 실시간 온라인 경매가 열리는 서비스입니다. 

기존 경매 플랫폼에 더불어 여기에 쇼츠 스타일의 실시간 라이브 스트리밍과 
3D/AR 작품 감상 기능을 더해 웹앱 하나로 "보고·감상하고·입찰하는" 경험을 제공합니다.

핵심 장치는 **안티 스나이핑(연장 경매)**입니다. 마감 30초 전에 새 입찰이 들어오면 마감
시각을 자동으로 30초 연장해, 마지막 순간에만 몰리는 스나이핑을 막습니다. 이 규칙 자체가
"입찰 이벤트를 보고 상태를 어떻게 바꿀지 판단하는" 이벤트 기반 아키텍처를 자연스럽게
정당화하는 장치입니다.

기술적으로는 Redis + Lua로 동시 입찰을 원자적으로 판정하고, Kafka에는 확정된 입찰만
순서 보장된 로그로 쌓아 재생(replay)·감사가 가능하도록 설계했습니다. 라이브 스트리밍은
AWS IVS(RTMP 송출 → HLS 재생)를, 3D/AR 작품 뷰어는 glTF/GLB + `<model-viewer>`를 기반으로
합니다.


## 폴더 구조

```
artbid/
├── docker-compose.yml   # PostgreSQL, Redis, Kafka(+Zookeeper), MinIO 로컬 인프라
└── backend/             # Spring Boot (기능/도메인 기준 패키지 구조)
```

`backend`는 레이어(controller/service/...) 기준이 아니라 **도메인(기능) 기준**으로 나눴습니다.

```
com.artbid
├── auction/      경매·입찰 (핵심 도메인, Redis Lua / Kafka 연결 지점) — 트랙 1
├── streaming/    라이브 스트리밍 (AWS IVS 채널·상태 관리)         — 트랙 2
├── media/        작품 미디어(사진·동영상·3D 모델) 업로드/조회      — 트랙 3
├── artwork/      작품 위탁·조회
├── member/       회원                                              — 트랙 4
├── payment/      결제(모의 PG)                                     — 트랙 4
├── settlement/   낙찰·정산                                         — 트랙 4
├── common/       예외 처리, HMAC 등 도메인 공통
└── infra/        여러 도메인이 같이 쓰는 인프라 연동
    ├── kafka/        입찰 이벤트 프로듀서·컨슈머
    ├── redis/        Redis 설정 (Lua 스크립트 실행은 트랙 1이 채움)
    ├── realtime/     SSE/WebSocket, 다중 인스턴스 확장 시 Redis Pub/Sub 중계
    ├── storage/      S3 presigned URL 발급 (사진·동영상·3D 모델 업로드 공용) — 트랙 3
    ├── media/        AWS MediaConvert 동영상 트랜스코딩 연동          — 트랙 3
    └── streaming/    AWS IVS 채널 생성/조회 연동                     — 트랙 2
```

`infra/*`는 특정 도메인 소유가 아니라 여러 트랙이 공유하는 연동 코드이므로, 인터페이스를
바꿀 때는 사용하는 도메인 담당자와 먼저 맞추고 진행해주세요.

## 폴더별 역할 (담당 트랙)

| 폴더 | 역할 | 담당 트랙 |
| --- | --- | --- |
| `auction` | 경매·입찰 도메인, 동시성 제어, 안티 스나이핑 | 트랙 1 (경매 코어) |
| `infra/kafka`, `infra/redis` | 입찰 이벤트 로그, compare-and-set Lua | 트랙 1 (경매 코어) |
| `streaming` | 라이브 방송 시작/종료, 시청 URL 조회 API | 트랙 2 (라이브 스트리밍) |
| `infra/streaming` | AWS IVS 채널 생성·streamKey 발급 | 트랙 2 (라이브 스트리밍) |
| `media` | 작품 사진·동영상·3D 모델 업로드/목록 API | 트랙 3 (미디어·3D·AR) |
| `infra/storage`, `infra/media` | S3 presigned URL, MediaConvert 트랜스코딩 | 트랙 3 (미디어·3D·AR) |
| `artwork` | 작품 위탁·심사·조회 | 트랙 3 (미디어·3D·AR)과 겸임 가능 |
| `member` | 회원가입·인증(JWT) | 트랙 4 (회원·결제·정산) |
| `payment` | 모의 PG 결제 흐름 | 트랙 4 (회원·결제·정산) |
| `settlement` | 낙찰 후 정산 배치 | 트랙 4 (회원·결제·정산) |
| `common`, `infra/realtime` | 전 트랙 공통 유틸/실시간 인프라 | 전원 공유, 변경 시 사전 협의 |

## 협업 규칙

### 브랜치 전략

- `main` : 배포 가능한 상태만 유지. 직접 커밋 금지, PR로만 병합.
- `develop` : 트랙별 기능을 모으는 통합 브랜치. 스프린트 데모 전 여기서 통합 테스트.
- `feature/{트랙}/{작업 내용}` : 실제 작업 브랜치. 예) `feature/auction/bid-lua-script`,
  `feature/streaming/ivs-channel-client`, `feature/media/presigned-url`,
  `feature/member/jwt-filter`
- `fix/{작업 내용}` : 버그 수정 브랜치.

작업 순서: `develop`에서 `feature/...` 분기 → 작업 → PR로 `develop`에 병합 → 스프린트
마지막에 `develop` → `main` 병합.

### 커밋 컨벤션

Conventional Commits 형식을 사용합니다: `type: 내용 (한글 가능)`

- `feat` : 새 기능 추가
- `fix` : 버그 수정
- `refactor` : 동작 변화 없는 코드 개선
- `test` : 테스트 코드 추가/수정
- `docs` : 문서(README, 주석) 변경
- `chore` : 빌드 설정, 의존성 등 잡일
- `style` : 포맷팅 등 코드 스타일 변경(로직 변화 없음)

예) `feat: Redis Lua 기반 입찰 compare-and-set 구현`, `fix: 안티 스나이핑 연장 시간 오차 수정`

### PR 규칙

- PR 제목도 커밋 컨벤션과 동일한 형식을 따릅니다.
- PR 본문에는 변경 내용, 관련 이슈/노션 문서 링크, 테스트 방법을 간단히 적습니다.
- 최대한 팀장이 pr을 확인한 후 병합합니다. 셀프 머지는 지양합니다.
- 자신의 담당 트랙 폴더 외의 코드(특히 `infra/`, `common/`)를 건드리는 PR은 해당 폴더를
  주로 쓰는 트랙 담당자를 리뷰어로 지정합니다.
- `main`, `develop`에는 강제 푸시(force push)를 하지 않습니다.

### 기타

- 작업 시작 전 노션 "PoC 기능 명세서" / "역할 분담" 문서에서 자신의 항목 상태를 업데이트합니다.
- 인터페이스가 바뀌는 변경(API 명세, 엔티티 필드 등)은 노션 API 명세/ERD 문서를 함께 갱신합니다.

## 아직 안 채운 부분 (TODO로 표시해둠)

- `infra/redis/BidLuaExecutor` — compare-and-set + 안티 스나이핑 연장 Lua 스크립트
- `infra/kafka/BidEventConsumer` — 받은 이벤트를 PostgreSQL에 영구 기록
- `infra/realtime` — 다중 인스턴스 확장 시 Redis Pub/Sub 중계 추가
- `infra/storage/S3PresignedUrlProvider` — AWS SDK S3Presigner 연동
- `infra/media/MediaConvertClient` — MediaConvert 트랜스코딩 job 요청
- `infra/streaming/IvsChannelClient` — AWS IVS 채널 생성/조회, streamKey 발급
- `media/service/MediaService` — presigned URL 발급, 업로드 완료 콜백 처리
- `streaming/service/StreamingService` — IVS 채널 생성 결과를 Livestream 엔티티에 반영
- `payment` — 결제 실패 시 보상 트랜잭션(Saga) 흐름
- `common/util/HmacUtil` — 감정서·인증서 서명 검증

## 로컬 실행

1. 인프라 먼저 띄우기
   ```
   docker-compose up -d postgres redis zookeeper kafka minio
   ```
2. `backend/` 폴더를 IntelliJ로 열면 Gradle 프로젝트로 인식됩니다 (Gradle Wrapper는
   IntelliJ가 열 때 자동 생성해주거나, `gradle wrapper` 명령으로 직접 생성하면 됩니다).
3. AWS 연동(S3/MediaConvert/IVS) 기능을 개발할 때는 로컬 AWS 자격 증명(`~/.aws/credentials`)
   또는 환경 변수가 필요합니다. 계정/크레딧 준비 전까지는 해당 기능 테스트는 보류하고
   TODO 스텁 상태로 두면 됩니다.
4. `ArtBidApplication` 실행 → `localhost:8080`

## 다음 단계

- 로드맵 1단계(기획/설계)는 노션 문서에 정리되어 있습니다.
- 2단계(인프라 셋업)의 "Spring Boot 프로젝트 뼈대 구성"이 이 커밋으로 완료된 상태입니다.
  (경매/스트리밍/미디어/회원 4개 트랙 패키지, 관련 infra 패키지 포함)
- 3단계부터는 트랙별로 TODO로 표시된 자리에 실제 로직을 채우면 됩니다. 진행 상황은
  노션 "PoC 기능 명세서" 데이터베이스에서 관리합니다.
