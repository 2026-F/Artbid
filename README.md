# ArtBid Backend

## 서비스 소개

ArtBid: 신진작가와 초보 컬렉터를 위한 미술품 경매 웹앱

작가나 소규모 갤러리가 작품을 위탁 등록하면 사전 관람(프리뷰)을 거쳐 정해진 시각에 경매가 열린다.
라이브 방송과 3D/AR 작품 미리보기로 구매 판단을 돕고, 마감 직전 입찰에는 마감 시간을 연장하는 안티 스나이핑 규칙을 적용한다.
작가와 위탁자는 별도 개념이며, 갤러리가 여러 작가의 작품을 위탁할 수 있다.
이 저장소는 작품, 경매, 입찰, 방송, 회원, 결제와 정산의 백엔드를 담당한다.

[프론트엔드 저장소](https://github.com/2026-F/frontend)와 API를 통해 연결한다.

## 문서 활용 기준

- README: 서비스 목적, 구조, 실행 방법과 팀 협업 규칙
- [CLAUDE.md](CLAUDE.md): AI의 작업 절차와 주의사항. Codex는 [AGENTS.md](AGENTS.md)에서 연결
- [작업 기록](docs/prompts/TEMPLATE.md): 개별 작업의 요청, 선택 이유와 검증 결과

‘현재 구현’은 확인 시점의 상태이며 향후 설계를 제한하는 규칙이 아니다.
작업 전 관련 코드와 최신 요구사항을 확인하고, 문서와 차이가 있으면 원인을 파악해 필요한 문서를 갱신한다.
코드가 요구사항과 다를 때는 버그 가능성도 검토하며, 요구사항을 코드에 맞춰 임의로 바꾸지 않는다.

## 현재 구현

문서 기준: 2026-10-26, develop. 구현되어 있다는 설명은 운영 환경 검증 완료를 의미하지 않는다.

| 영역 | 구현과 확인할 범위 |
| --- | --- |
| 경매와 입찰 | PostgreSQL 행 락으로 입찰을 처리하고 커밋 후 SSE로 전파한다. 입찰 취소와 조회, 마감 후 낙찰 및 정산 생성이 구현되어 있다. |
| 안티 스나이핑 | 남은 시간을 초 단위로 계산해 30초 이하인 유효 입찰이면 기존 마감 시각에 30초를 더한다. 경계 조건은 [Auction](backend/src/main/java/com/artbid/auction/domain/Auction.java)과 테스트를 확인한다. |
| 작가와 작품 | 작가 API, 작품 등록과 검색, 상세 조회, 수정 및 심사 API가 있다. 권한 처리는 엔드포인트별로 확인해야 한다. |
| 라이브 방송 | AWS IVS Real-Time 스테이지 생성, 참여 토큰, QR, 연결 끊김과 재연결 상태 처리를 구현했다. 실제 AWS 통합 검증은 관련 PR의 미검증 항목을 확인한다. |
| 미디어 | S3 호환 저장소의 presigned URL 업로드와 MediaConvert 처리 상태 관리가 있다. 로컬 미디어 저장소는 MinIO이다. |
| 회원 | 회원가입, JWT 로그인, 회원 조회와 수정이 있다. 로그아웃 API는 204 응답을 반환하며 해당 엔드포인트에서 토큰을 무효화하지 않는다. 권한 검증 범위는 기능별 확인이 필요하다. |
| 결제 | Mock PG와 Toss 결제 승인 연동 코드가 있다. 결과 불명 상태의 복구는 후속 범위다. |
| 정산 | 정산 배치가 결제 기한이 지난 미결제 정산을 만료(FAILED)하고, 결제 완료 정산의 위탁자 지급 대기를 만든다. 내 정산, 내 지급 내역 조회 API가 있다. 판매 수수료율은 임시 설정값이고 실제 송금은 하지 않는다. |

### 예정된 변경과 미정 사항

MSA 전환 시 다음 세 서비스로 분리할 예정이다.

| 서비스 구분 | 포함 영역 |
| --- | --- |
| 1 | 경매/입찰 + 회원/작품 |
| 2 | 결제/정산 |
| 3 | 라이브 스트리밍 |

서비스 분리에 따라 레포 구성과 코드 경로도 변경될 수 있다. 저장소 분리 방식, 데이터 소유권, 서비스 간 통신과 인증 방식은 별도 설계 대상이다.
아래 폴더 구조와 실행 명령은 현재 구성 기준이다. 구조 변경 시 README, CLAUDE.md, AGENTS.md의 참조 경로, CI와 실행 안내를 함께 갱신하고 기존 작업 기록의 연결을 유지한다.

- 경매와 입찰에 Kafka 도입 예정. 구체적인 역할과 적용 방식은 후속 설계에서 결정한다.
- Redis 사용 여부와 역할은 미정이다. 현재 코드나 초기 Redis/Lua 설계만으로 유지 또는 제거를 확정하지 않는다.
- 현재 입찰 경로는 PostgreSQL 행 락과 커밋 후 SSE이다. Redis와 Kafka 관련 코드가 있다는 사실만으로 이벤트 저장이나 다중 인스턴스 전파가 완성된 것은 아니다.

### 남은 구현과 확인 사항

- 감정서와 인증서 HMAC 검증: [HmacUtil](backend/src/main/java/com/artbid/common/util/HmacUtil.java)에 TODO만 존재
- 다중 인스턴스 전파, 결제 결과 불명 상태 복구, 위탁자 실제 송금과 지급 시점: 후속 범위와 진행 상태 확인 필요

미구현 항목을 곧바로 확정된 개발 일정으로 해석하지 않는다. 우선순위와 완료 조건은 [관련 이슈](https://github.com/2026-F/Artbid/issues) 및 팀 합의로 관리한다.

## 기술 스택

| 구분 | 기술 | 용도와 적용 상태 |
| --- | --- | --- |
| 언어 | Java 21 | 컴파일, 실행, 테스트의 toolchain 기준 |
| 프레임워크 | Spring Boot 3.3.4 | REST API와 애플리케이션 구성 |
| 데이터 접근 | Spring Data JPA | 도메인 저장과 조회, 입찰 행 락 처리 |
| 데이터베이스 | PostgreSQL 16 | 회원, 작품, 경매, 결제 등 데이터 저장 |
| Redis 연동 | Redis 7, Spring Data Redis | 현재 의존성과 로컬 인프라 포함. 향후 사용 여부와 역할은 미정 |
| 이벤트 처리 | Kafka, Spring Kafka | 경매와 입찰에 도입 예정. 관련 코드와 확장 인프라 존재 |
| 실시간 알림 | SSE | 입찰 커밋 후 경매 상태 전파 |
| 인증 | JWT, JJWT, Spring Security Crypto | 토큰 처리와 비밀번호 해싱 |
| 라이브 방송 | AWS IVS Real-Time | 스테이지와 참여 토큰 관리 |
| 파일 저장 | S3 호환 저장소, MinIO | Presigned URL 업로드. 로컬 저장소는 MinIO |
| 영상 처리 | AWS MediaConvert | 트랜스코딩 요청과 상태 관리. 기본 설정은 비활성화 |
| 결제 연동 | Mock PG, Toss | 모의 결제와 Toss 승인 연동 코드 |
| 빌드와 테스트 | Gradle Wrapper, Spring Boot Test | 빌드 및 단위 테스트와 통합 테스트 |
| 실행과 CI | Docker Compose, GitHub Actions | 로컬 인프라 구성과 빌드 검증 |

버전과 의존성은 [build.gradle](backend/build.gradle), 로컬 인프라 이미지는 [docker-compose.yml](docker-compose.yml)을 기준으로 확인한다.

## 폴더 구조

```text
Artbid/
├─ backend/                 # Gradle 프로젝트
│  └─ src/main/java/com/artbid/
│     ├─ artist/            # 작가
│     ├─ artwork/           # 작품
│     ├─ auction/           # 경매와 입찰
│     ├─ streaming/         # 라이브 방송
│     ├─ media/             # 작품 미디어
│     ├─ member/            # 회원과 인증
│     ├─ payment/           # 결제
│     ├─ settlement/        # 낙찰 후 정산
│     ├─ common/            # 공통 설정과 예외
│     └─ infra/             # 외부 서비스와 실시간 연동
├─ docs/                    # 설계와 의사결정 기록
└─ docker-compose.yml       # 로컬 인프라 및 백엔드 이미지 실행 구성
```

현재 코드는 도메인 기준으로 구성한다. 아래 표는 기존 작업 영역 구분이며, 예정된 MSA 서비스 경계와는 다르다.

| 담당 영역 | 관련 패키지 |
| --- | --- |
| 경매 코어 | `auction`, `infra/kafka`, `infra/redis` |
| 라이브 스트리밍 | `streaming`, `infra/streaming` |
| 작품과 미디어 | `artist`, `artwork`, `media`, `infra/storage`, `infra/media` |
| 회원, 결제, 정산 | `member`, `payment`, `settlement` |
| 공통 영역 | `common`, `infra/realtime` 및 여러 영역에서 사용하는 연동 코드 |

공통 코드의 인터페이스를 바꾸기 전에는 사용하는 영역과 영향을 확인하고 관련 팀원과 조율한다.
구체적인 담당자는 팀의 역할 분담 문서를 따른다.

## 로컬 실행

명령어는 macOS 터미널과 Windows Git Bash 기준이다.

Docker와 Compose, Gradle Wrapper를 실행할 JDK를 준비한다. 컴파일과 실행은 Java 21 toolchain을 사용한다.

저장소 루트에서 기본 인프라를 실행한다.

```sh
docker compose up -d postgres redis
```

미디어 기능을 확인할 때 MinIO를 추가한다.

```sh
docker compose --profile media up -d minio
```

현재 Compose에서 Kafka와 Zookeeper는 `expansion` 프로필에 있다. Kafka 연동 작업 시 실행한다.

```sh
docker compose --profile expansion up -d zookeeper kafka
```

Kafka 도입 후에는 실제 실행 경로에 맞춰 기본 인프라와 검증 안내도 갱신한다.
Compose의 `backend` 서비스는 배포 이미지를 사용하므로, 현재 소스 변경을 확인할 때는 아래처럼 Gradle로 실행한다.

```sh
cd backend
./gradlew bootRun
```

기본 주소는 `http://localhost:8080`이다. `./gradlew` 실행 권한 오류가 나면 `backend/`에서 `chmod +x gradlew`로 실행 권한을 부여한다.
이 설명은 기본 인프라 설정 기준이며 포트를 변경했다면 애플리케이션 설정도 맞춰야 한다.

- JWT 설정은 `JWT_SECRET`, Toss 설정은 `TOSS_SECRET_KEY` 환경변수를 사용한다. 실제 키를 문서나 저장소에 넣지 않는다.
- AWS 기능은 각자의 로컬 자격 증명 또는 실행 환경의 IAM 역할과 서비스 설정이 필요하다.
- 로컬 MinIO는 `media.storage.create-bucket-if-missing=true`일 때 초기화 코드가 버킷 생성과 공개 읽기 정책 적용을 시도한다. 코드가 MinIO 여부를 검사해 제한하지 않으므로 AWS 연결 전 이 설정을 끄고 엔드포인트, 자격 증명, 버킷 정책을 확인한다. 운영 버킷의 공개 범위는 별도로 설정한다.
- MediaConvert 활성화 여부, 버킷, 템플릿 등은 `backend/src/main/resources/application.yml`의 해당 설정을 확인한다.
- 기존 `application-local.yml`에는 결제 키 형태의 직접 입력값이 있어 담당자 확인이 필요하다. 유효성은 확인하지 않았으며, 실제 자격 증명이라면 교체와 환경변수 전환을 별도 처리해야 한다.

## 검증

`backend/`에서 실행한다.

```sh
./gradlew test
./gradlew build
```

`build`에는 테스트가 포함된다. 필요에 따라 둘 중 적절한 명령을 사용한다.
일부 테스트는 PostgreSQL과 Redis가 필요하다. CI는 해당 서비스를 준비하고 Java 21에서 `./gradlew build`를 실행한다.

결제 PostgreSQL 통합 테스트는 기본적으로 비활성화된다. 이를 포함할 때는 별도 테스트 DB 연결을 확인하고 활성화한다.

```sh
PAYMENT_POSTGRES_TEST=true ./gradlew test --tests 'com.artbid.payment.*' --tests 'com.artbid.settlement.*'
```

`PAYMENT_POSTGRES_TEST=true`는 해당 명령에만 적용한다.

연결 변수와 독립 테스트 스키마 설명은 [결제 흐름 기록](docs/payment-flow.md)을 참고하고 현재 테스트 코드와 대조한다.
단위 테스트 통과만으로 실제 Toss 결제, AWS 방송, 배포 환경 연동을 검증했다고 보고하지 않는다.

## 설계 기록

- [결제 상태 설계](docs/payment-state-design.md): 초기 설계와 당시 검증 범위
- [결제 흐름](docs/payment-flow.md): 초기 Mock PG 구현과 테스트 방법
- [프롬프트 기록 양식](docs/prompts/TEMPLATE.md): 앞으로의 AI 활용 의사결정 기록

## 협업 규칙

### 브랜치

- `main`: 배포 가능한 상태만 유지. 직접 커밋 금지. PR로만 병합.
- `develop`: 트랙별 기능을 모으는 통합 브랜치. 여기서 `feature/{기능}/{작업}` 또는 `fix/{작업}` 브랜치를 만든다. 문서 작업은 `docs/{작업}`을 사용한다.
- 작업 브랜치에서 `develop`으로 PR을 보내고, 통합 확인 후 `main`에 반영한다.
- `main`과 `develop`에 force push하지 않는다.
- 셀프 merge는 하지 않는다(팀장이 확인 후 머지).

### 커밋 컨벤션

Conventional Commits 형식을 사용한다.
`type: 내용` (한글 가능)
PR 제목 작성 시에도 참고한다.

- feat: 새 기능 추가
- fix: 버그 수정
- refactor: 동작 변화 없는 코드 개선
- test: 테스트 코드 추가/수정
- docs: 문서(README, 주석) 변경
- chore: 빌드 설정, 의존성 등 잡일
- style: 포맷팅 등 코드 스타일 변경(로직 변화 없음)

### 변경 공유

- API나 엔티티 필드 등 인터페이스가 바뀌면 관련 명세와 ERD도 갱신한다.
- 상대 저장소 변경이 필요하면 해당 이슈나 PR을 연결하고 선행 작업 및 연동 검증 여부를 적는다. 번호가 정해지지 않았으면 미생성으로 명시하고 생성 후 연결한다.
- PR 병합과 기능 검증 완료를 구분한다. 이슈 완료 조건에 남은 작업이 있으면 후속 작업을 명시한다.

## AI 협업 기록

팀 공통 지침은 [CLAUDE.md](CLAUDE.md)에 있다. Codex는 [AGENTS.md](AGENTS.md)를 통해 같은 지침을 읽는다.

설계 대안 선택, API 계약 변경, 원인 분석이 필요한 오류 해결, 보안 또는 외부 연동 방식 변경은 [기록 양식](docs/prompts/TEMPLATE.md)을 사용해 `docs/prompts/YYYY-MM-DD-주제.md`에 남긴다.
단순 오탈자나 색상 변경은 별도 기록 없이 PR의 히스토리 항목에 '해당 없음'과 이유를 적으면 된다.

1. 중요한 결정이 생기면 AI가 기록 시작 날짜와 구체적인 주제로 파일을 만든다. 파일명은 `YYYY-MM-DD-주제.md` 형식으로 작성한다.
2. 같은 작업에서 진행 중인 후속 요청과 결정은 기존 파일에 추가한다. 날짜가 바뀌면 날짜를 구분하고, 이전 판단은 덮어쓰지 않는다. PR 생성 후에는 해당 PR을 기준으로 기록 범위를 구분한다.
3. 작업 완료 시 AI가 실제 검증 결과와 미확인 사항을 정리한다.
4. 담당자가 핵심 요청, 선택 이유, 수정 내용, 검증 결과를 확인한다.
5. PR의 **프롬프트 히스토리** 항목에 생성하거나 갱신한 기록 파일을 연결한다.
6. PR 병합 후 이어지는 작업은 새 파일에 기록하고 이전 기록과 PR을 연결한다.

병합된 기록은 보존하되, 오탈자와 사실 오류는 정정 이유를 남기고 수정할 수 있다. 민감정보는 발견 즉시 제거하며 원문을 정정 이력에 남기지 않는다.

기록은 대화 전체를 복사하는 대신 핵심 요청, AI 제안, 채택하거나 제외한 이유, 직접 수정하거나 재요청한 내용, 검증 결과를 담는다.
확인되지 않은 사람의 판단이나 과거 프롬프트는 만들어 쓰지 않는다. 비밀정보와 개인정보는 원문에서 제거한다.

두 저장소에 걸친 결정은 주된 결정을 내린 저장소에 상세 기록을 두고 상대 PR에서 연결한다.
PR에는 실제로 실행한 검사와 미검증 사항을 구분하고, 보안 관련 변경이면 타인 접근 거절 등 필요한 실패 사례도 확인한다.
