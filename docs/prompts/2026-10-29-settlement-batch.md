# 정산 내역 조회와 정산 배치 처리

- 작성일: 2026-10-29
- 관련 이슈 / PR: [회원·결제·정산] 정산 내역 조회, 정산 배치 처리 (이슈 번호 미확인), PR 생성 전

## 목적

낙찰자와 위탁자가 자기 정산 내역을 조회하고, 결제 기한이 지난 미결제 정산과 위탁자 지급 대상을 배치로 처리한다.
제약: 판매 수수료율과 위탁자 지급 시점은 정책 미정. 기존 정산 조회 API는 인증 없이 엔티티를 그대로 반환했다.

## 핵심 요청

> [회원·결제·정산] 정산 내역 조회, 정산 배치 처리

이슈 본문은 AI 작업 환경에서 GitHub 접근이 안 돼 확인하지 못했다. 배치 범위와 수수료율은 아래 질문으로 정했다.

요약: AI가 배치 범위(미결제 만료 / 위탁자 지급액 계산 / 둘 다)와 판매 수수료율(10% / 20% / 미정이면 설정값)을 물었다.
사용자 선택: 배치 범위 "둘 다", 수수료율 "아직 모름 (설정값)".

## 선택과 이유

| 검토한 방법 | 선택 | 이유 |
| --- | --- | --- |
| 배치: 미결제 만료 + 위탁자 지급 대기 생성 | 채택 (사용자 선택, AI 추천) | |
| 판매 수수료율: 설정값 `app.settlement.seller-commission-rate`, 임시 0.10 | 채택 (사용자 선택) | 정책 미정 |
| 만료 상태: 새 enum 값 EXPIRED 추가 | 제외 (AI 결정) | 기존 DB의 payment_status에 Hibernate가 만든 CHECK 제약(PENDING, PAID, FAILED)이 있어 새 값 저장 실패. 로컬 DB에서 제약 확인 |
| 만료 상태: 기존 FAILED + expiredAt 컬럼 | 채택 (AI 결정) | FAILED는 사용되지 않던 값. nullable 컬럼 추가는 ddl-auto update로 반영됨 |
| 결과 불명 결제(REQUESTED)가 있는 정산 만료 | 제외 (AI 결정) | CLAUDE.md 기준. PG 승인 여부를 모르는 상태를 실패로 확정하지 않음. 다음 배치에서 재판단 |
| 위탁자 지급액: 낙찰가 - 낙찰가 x 수수료율 (원 단위 반올림) | 채택 (AI 결정) | 구매 수수료와 배송비는 낙찰자가 낸 플랫폼 몫과 배송비라 제외. 적용한 수수료율을 지급 행에 저장 |
| 지급 상태 enum에 PAID 미리 정의 | 채택 (AI 결정) | 위와 같은 CHECK 제약 문제 예방. PAID로 바꾸는 기능은 없음 |
| 실제 송금 | 제외 | 송금 연동과 지급 시점 정책 미정 |
| 중복 지급 방지: 존재 확인 + settlementId 유니크 제약 | 채택 (AI 결정) | 서버 여러 대에서 배치가 동시에 돌아도 한 건만 생성 |
| 기존 조회 API 권한 확인과 DTO 응답 | 채택 (AI 결정) | 같은 정산 조회 범위. 낙찰자, 해당 작품 위탁자, 관리자만. 프론트는 이 API를 쓰지 않음(develop 기준 확인) |
| 목록 페이지네이션 | 보류 | 회원당 정산 수가 적다고 보고 단순 목록으로 둠. 미확인 |

## 반영 내용

- `Settlement.expire(now)`, `expiredAt` 추가
- `SellerPayout`(위탁자 지급 대기), `SellerPayoutStatus`(READY, PAID), 리포지토리
- `SettlementExpiryService`, `SellerPayoutService`, `SettlementBatchScheduler` (기본 10분, `app.settlement.batch.cron`, `enabled`로 끌 수 있음)
- API: `GET /api/settlements/me?status=`, `GET /api/settlements/payouts/me`
- 기존 `GET /api/settlements/{id}`, `GET /api/settlements?auctionId=`: 로그인 필요(401), 당사자 아니면 403, 없으면 404(JSON 오류 응답). 응답 필드는 기존과 같고 expiredAt 추가
- README, payment-flow.md 갱신

## 검증

| 확인 항목 | 방법 | 결과 |
| --- | --- | --- |
| 도메인, 서비스, 스케줄러, 컨트롤러 단위 테스트 | `./gradlew build` (AI 작업 환경, Linux, Java 21) | 통과 |
| 배치 쿼리, 행 잠금, 유니크 제약 | PostgreSQL 16 통합 테스트(`SettlementBatchPostgresTest`), 재실행 포함 | 통과 |
| 전체 테스트 | PostgreSQL 16, Redis 7 실행 후 `./gradlew build` | 223개 중 실패 0, 건너뜀 9 (기존 조건부 테스트) |
| 실제 서버 흐름 | `bootRun`(배치 5초 주기) 후 회원가입, 로그인 토큰으로 API 호출 | 기한 지난 미결제 FAILED 처리, 결제 완료 건 지급 대기 생성(500,000원 → 450,000원), 위탁자 지급 내역 조회 |
| 권한과 잘못된 입력 | 같은 서버 | 관계없는 회원 403, 비로그인 401, 잘못된 status 400 |
| 기존 DB 반영 | 기존 테이블이 있는 DB에서 서버 시작 | expired_at 컬럼, seller_payout 테이블과 유니크 제약 생성 확인 |

미검증: Windows 로컬 실행, 운영 DB 데이터 상태, 프론트 연동(프론트에 정산 화면 없음).

## 남은 작업

- 판매 수수료율과 위탁자 지급 시점(결제 직후 / 배송 확인 후) 정책 확정
- 실제 송금 연동과 지급 완료(PAID) 처리
- REQUESTED가 해소되지 않는 결제의 복구 (payment-flow.md 남은 작업)
- 만료된 정산의 후속 처리(재경매, 차순위 낙찰 등) 정책
