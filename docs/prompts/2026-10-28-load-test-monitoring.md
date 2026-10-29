# 부하테스트 모니터링 구성

- 작성일: 2026-10-28
- 관련 이슈 / PR: 미정

## 목적

입찰 API의 동시 처리 한계를 측정하고 병목 위치를 확인할 수 있는 모니터링 환경을 만든다.
현재 입찰은 PostgreSQL 행 락과 3초 lock_timeout으로 처리하므로, 동시 입찰이 늘 때 락 대기와 커넥션 풀 고갈이 어디서 시작되는지가 핵심이다.

## 핵심 요청

요약: 부하테스트 모니터링 기능을 만든다.

## 선택과 이유

| 검토한 방법 | 선택 | 이유 |
| --- | --- | --- |
| k6 + Prometheus + Grafana, 서버 지표는 Actuator와 Micrometer | 채택 (AI 제안) | Spring Boot 기본 지표로 HTTP, JVM, HikariCP를 별도 코드 없이 수집 가능. k6 결과를 같은 Prometheus에 넣어 부하와 서버 상태를 한 시간축에서 비교 |
| 관리자 페이지 안에 지표 화면 직접 구현 | 제외 (AI 제안) | 부하테스트 목적에는 기존 도구로 충분. 사용자 선택 이유는 미확인 |
| 지표를 서비스 포트(8080)에 노출 | 제외 | Spring Security가 없어 배포 서버에서 지표가 공개됨 |
| 관리 포트 8081 분리, compose에서 publish하지 않음 | 채택 | 인증 없이도 외부 노출을 막을 수 있음 |
| 테스트 경매를 API로 생성 | 제외 | PREVIEW에서 ONGOING으로 바꾸는 로직이 없어 입찰 가능한 경매를 만들 수 없음. SQL로 직접 생성 |

## 반영 내용

- `backend/build.gradle`: actuator, micrometer-registry-prometheus 추가
- `application.yml`: 관리 포트 8081, health/info/prometheus 노출, http.server.requests 히스토그램
- `application-loadtest.yml`: show-sql 끔, 톰캣 스레드 지표 켬
- `docker-compose.yml`: monitoring 프로필로 prometheus, grafana 추가. Grafana는 프론트 3000과 겹치지 않게 3001
- `monitoring/`: Prometheus 수집 설정, Grafana 데이터소스와 대시보드 자동 등록
- `loadtest/`: 시드 SQL, 입찰 경합 k6 스크립트, 실행 안내

## 검증

### 2026-10-29

작업 환경에 Docker와 WSL이 없어 컨테이너·런타임 검증은 못 했다. 정적 검증만 진행했다.

| 확인 항목 | 방법 | 결과 |
| --- | --- | --- |
| 빌드 | `./gradlew build` | 성공 (compileJava, bootJar 포함) |
| 기존 테스트 | `./gradlew build`의 테스트 185건 | 182건 통과. 3건(`ArtBidApplicationTests`, `BidServiceConcurrencyTest` 2건) 실패 — 전부 `localhost:5432 Connection refused`. Postgres 미기동 때문이며 이번 변경과 무관 |
| management 설정 | `application.yml` 코드 리뷰 | 관리 포트 8081, `health,info,prometheus`만 노출. 의도대로 작성됨 |
| docker-compose 문법 | 코드 리뷰 (docker 없어 `config` 명령 실행은 못 함) | monitoring 프로필, 8081 미publish, host.docker.internal 연결 확인. 문법상 문제 없음 |
| Prometheus 타깃 | `monitoring/prometheus/prometheus.yml` 리뷰 | `host.docker.internal:8081`, `backend:8081` 대상 설정 적절 |
| 입찰 API 경로/상태코드 | `BidController`, `GlobalExceptionHandler` 코드 대조 | `/api/auctions/{auctionId}/bids` 경로, 404/409/503 매핑이 대시보드·k6 스크립트 가정과 일치 |
| `k6_http_req_duration_p95{name="bid"}` 등 k6 메트릭 이름 | k6 experimental-prometheus-rw 네이밍 규칙과 `bid-contention.js`의 `tags: { name: 'bid' }`, `K6_PROMETHEUS_RW_TREND_STATS` 대조 | 네이밍 규칙상 일치할 것으로 판단. **실제 remote-write 결과로는 미확인** |
| seed.sql 컬럼명 | `Auction` 엔티티 필드와 Hibernate 기본 네이밍(camelCase→snake_case) 대조 | 테이블명 `auction`, 컬럼명 전부 일치 |
| 실제 기동·수집·대시보드 표시 | `docker compose up`, `bootRun`, Grafana 패널 확인 | **미실행** — 이 작업 환경에 Docker/WSL 없음. Docker 있는 환경(팀원 PC, CI)에서 실행 필요 |
| k6 부하테스트 | `loadtest/bid-contention.js` 실행 | **미실행** — 같은 이유 |

미검증 항목(실제 기동, 메트릭 수집, 대시보드 표시, k6 결과)은 Docker가 있는 환경에서 HANDOFF-monitoring.md의 "다음에 할 일" 1번 순서대로 재확인이 필요하다.

### 2026-10-29 (같은 날, Docker 설치 후 실제 실행 검증)

이 PC에 Docker Desktop을 새로 설치(WSL2 활성화 포함)하고 위 미실행 항목을 실제로 돌려서 확인했다.

| 확인 항목 | 방법 | 결과 |
| --- | --- | --- |
| postgres, redis, prometheus, grafana 컨테이너 기동 | `docker compose up -d postgres redis` + `docker compose --profile monitoring up -d` | 전부 정상 기동. 단, 이 PC는 Windows Hyper-V 동적 포트 예약(9089-9188)이 9090과 겹쳐서 Prometheus 호스트 포트를 커밋하지 않는 로컬 전용 `docker-compose.override.yml`로 19090으로 바꿔서 띄움 |
| backend 컨테이너와 로컬 bootRun 포트 충돌 | `docker compose --profile monitoring up -d` 실행 시 `backend` 서비스(배포 이미지)도 같이 떠서 8080 포트를 선점 | 로컬 bootRun으로 직접 입찰 API를 띄우려면 `docker compose stop backend` 필요. HANDOFF 문서에 이 순서가 빠져 있어서 추가 필요 |
| actuator/prometheus 응답 | `curl -s localhost:8081/actuator/prometheus \| grep hikaricp_connections_max` | 정상 응답 (`hikaricp_connections_max{...} 10.0`) |
| Prometheus Targets | `http://localhost:19090/api/v1/targets` | `host.docker.internal:8081` UP, `backend:8081`은 컨테이너를 멈췄으니 DOWN (README 설명과 일치) |
| seed.sql 실행 | `docker compose exec -T postgres psql -U artbid -d artbid < loadtest/seed.sql` | 정상, auction id=1 생성 |
| k6 실행 | `k6 run -o experimental-prometheus-rw -e AUCTION_ID=1 -e PEAK_VUS=20 loadtest/bid-contention.js` | 정상 종료. PEAK_VUS=20에서는 503/5xx 0건, `http_req_duration{name:bid}` p95=11.92ms로 threshold 통과 |
| k6 메트릭 이름 (`k6_http_req_duration_p95{name="bid"}`, `k6_vus`, `k6_http_reqs_total{name="bid"}`) | Prometheus에 직접 쿼리 | 전부 실제 데이터로 존재. 대시보드 쿼리와 라벨까지 정확히 일치 |
| 서버 지표 (`http_server_requests_seconds_count{uri="/api/auctions/{auctionId}/bids"}`, `hikaricp_connections_active`, `tomcat_threads_busy_threads`) | Prometheus에 직접 쿼리 | 전부 존재. loadtest 프로필의 톰캣 지표도 정상 노출 |
| Grafana 대시보드 프로비저닝 | `GET /api/search?query=ArtBid` | "ArtBid" 폴더 아래 "ArtBid 부하테스트"(uid `artbid-loadtest`) 정상 등록 |
| Grafana → Prometheus 데이터소스 경로 | `GET /api/datasources/proxy/uid/prometheus/api/v1/query`로 대시보드와 동일한 쿼리 실행 | 실제 패널이 쓰는 것과 동일한 경로로 데이터 정상 반환 |

**남은 것:** PEAK_VUS를 50/100/200/400으로 올려가며 503이 처음 나오는 지점과 p95를 기록하는 건(HANDOFF 6번) 아직 안 함 — PEAK_VUS=20에서는 아직 한계에 도달하지 않음.

## 남은 작업

- 입찰 결과별 카운터, 안티 스나이핑 연장 횟수, SSE 연결 수 같은 커스텀 지표 추가 검토
- 입찰 API가 요청 본문의 bidderId를 그대로 쓰는 문제는 경매 담당자에게 공유
- 결과 수치를 바탕으로 Redis 또는 Kafka 도입 필요성 판단
