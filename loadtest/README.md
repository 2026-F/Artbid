# 부하테스트

입찰 API에 동시 요청을 몰아 PostgreSQL 행 락 기반 입찰 처리의 한계를 측정한다.
k6로 부하를 주고, Prometheus가 백엔드 지표와 k6 결과를 모으고, Grafana에서 같이 본다.

```
k6 ──remote write──▶ Prometheus ◀──scrape(8081)── Spring Boot
                         │
                      Grafana(3001)
```

## 준비

- k6 설치: Windows `winget install k6 --source winget`, macOS `brew install k6`
- 저장소 루트에서 인프라 실행

```bash
docker compose up -d postgres redis
docker compose --profile monitoring up -d
```

## 실행 순서

1. 백엔드를 loadtest 프로필로 실행한다. SQL 로그 출력이 꺼지고 톰캣 스레드 지표가 켜진다.

`docker compose --profile monitoring up -d`로 띄운 `backend`(배포 이미지) 컨테이너가 8080을 먼저 쓰고 있으면 로컬 bootRun이 포트 충돌로 못 뜬다. 로컬 코드로 테스트하려면 그 컨테이너부터 내린다.

```bash
docker compose stop backend

cd backend
./gradlew bootRun --args='--spring.profiles.active=loadtest'
```

2. 지표가 나오는지 확인한다. 관리 포트는 8081이다.

```bash
curl -s localhost:8081/actuator/prometheus | grep hikaricp_connections_max
```

Prometheus(http://localhost:9090) Status > Targets에서 `host.docker.internal:8081`이 UP인지 본다.
`backend:8081`은 compose로 백엔드를 띄웠을 때만 UP이라 DOWN이어도 된다.

3. 테스트용 경매를 만든다. 출력된 id를 기억한다.

```bash
docker compose exec -T postgres psql -U artbid -d artbid < loadtest/seed.sql
```

4. k6를 실행한다.

```bash
K6_PROMETHEUS_RW_SERVER_URL=http://localhost:9090/api/v1/write \
K6_PROMETHEUS_RW_TREND_STATS="p(95),p(99),avg,max" \
k6 run -o experimental-prometheus-rw -e AUCTION_ID=<id> -e PEAK_VUS=100 loadtest/bid-contention.js
```

5. Grafana(http://localhost:3001, 초기 계정 admin/admin)의 ArtBid 폴더에서 "ArtBid 부하테스트" 대시보드를 연다.

## 볼 것

| 지표 | 의미 |
| --- | --- |
| 503 비율 | 행 락 대기 3초 초과. 처음 0보다 커지는 VU 수가 현재 구조의 한계 |
| HikariCP pending | 커넥션 풀(기본 10개)이 모자라 요청이 대기 중 |
| HikariCP usage | 트랜잭션이 커넥션을 쥐고 있는 시간. 락 대기가 길어지면 같이 늘어남 |
| 입찰 p95, p99 | 사용자 체감 응답시간 |
| 409 비율 | 가격 경합 거절. 정상 동작이며 경합 강도를 보여줌 |

PEAK_VUS를 50, 100, 200, 400으로 올려가며 반복하고, 각 단계의 503 비율과 p95를 기록한다.
같은 경매에 계속 입찰하면 현재가가 올라갈 뿐 다른 영향은 없으므로 경매는 재사용해도 된다.

## 주의

- 입찰 API는 현재 인증 없이 요청 본문의 bidderId를 그대로 쓴다. 테스트에서는 VU 번호를 bidderId로 넣는다.
- 관리 포트 8081은 외부에 열지 않는다. 배포 서버 compose에도 publish하지 않는다.
- 운영(EC2) 서버에는 부하를 주지 않는다. 로컬에서만 실행한다.
