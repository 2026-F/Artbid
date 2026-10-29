# HMAC 서명 검증 (작품 보증서)

- 작성일: 2026-10-29
- 관련 이슈 / PR: [회원·결제·정산] HMAC 서명 검증 (이슈 번호 미확인), PR 생성 전

## 목적

작품 보증서(감정서·인증서)의 위변조를 막는다. 서버가 보증서 내용에 서명해 발급하고, 누구나 진위를 확인할 수 있게 한다.
제약: `HmacUtil`에는 TODO만 있었고 사용처가 없었다. 작품에는 `certificateUrl`(보증서 파일 주소)만 있다.

## 핵심 요청

> [회원·결제·정산] HMAC 서명 검증

이슈 본문은 AI 작업 환경에서 GitHub 접근이 안 돼 확인하지 못했다.

요약: AI가 검증 대상(작품 보증서·감정서 / 결제 웹훅 요청 / 서명 유틸만)을 물었다.
사용자 선택: 작품 보증서·감정서. 근거는 `HmacUtil`의 TODO와 README의 "감정서와 인증서 HMAC 검증" 항목이다.

## 선택과 이유

| 검토한 방법 | 선택 | 이유 |
| --- | --- | --- |
| 검증 대상: 작품 보증서 | 채택 (사용자 선택, AI 추천) | 코드 TODO와 README 기준 |
| 결제 웹훅 서명 검증 | 제외 | 웹훅 수신 코드 자체가 없음 |
| 알고리즘: HMAC-SHA256, Base64 URL-safe | 채택 (AI 결정) | JDK 기본 제공. URL과 QR에 그대로 넣을 수 있음 |
| 서명 비교: MessageDigest.isEqual | 채택 (AI 결정) | 문자열 equals의 타이밍 차이로 서명을 추측하는 공격 방지 |
| 서명 대상: 발급 시점 작품 정보 스냅샷 | 채택 (AI 결정) | 작품 정보가 나중에 바뀌어도 발급된 보증서 내용은 유지. 바뀐 정보는 재발급 |
| 서명 문자열: 고정 순서 이름=값, 값 URL 인코딩, 형식 버전 | 채택 (AI 결정) | 같은 내용이면 같은 문자열. 줄바꿈 삽입으로 필드를 위장하는 조작 방지 |
| 검증 방식: 입력 서명과 저장 서명을 모두 다시 계산해 확인 | 채택 (AI 결정) | 사본 서명 위조와 DB 직접 변조를 모두 감지 |
| 키 관리: 환경변수, 32바이트 미만이면 시작 실패, keyVersion 저장 | 채택 (AI 결정) | JWT 시크릿과 같은 방식. 키 교체 대비 |
| 이전 키로 서명된 보증서 검증 | 보류 | 키 교체 정책 미정 |
| 재발급 시 이전 보증서 폐기(REVOKED) | 채택 (AI 결정) | 유효한 보증서는 작품당 하나. 동시 발급은 작품 행 잠금으로 방지 |
| 작품 행 잠금 쿼리 위치: 보증서 리포지토리 | 채택 (AI 결정) | 작품 패키지(다른 담당 영역)를 수정하지 않음 |
| 공개 응답에서 위탁자 회원 ID 제외 | 채택 (AI 결정) | 서명 대상에는 포함하되 노출하지 않음 |
| 검증 결과: 항상 200 + valid/result | 채택 (AI 결정) | 공개 확인 화면에서 결과별 안내가 쉬움 |

## 반영 내용

- `HmacUtil`: sign, verify 구현
- `certificate` 패키지: `CertificatePayload`(서명 문자열), `ArtworkCertificate`, `CertificateStatus`, `CertificateSigner`, 서비스, 컨트롤러, 예외 처리
- `POST /api/artworks/{id}/certificates`: 발급·재발급 (작품 위탁자, 관리자)
- `GET /api/artworks/{id}/certificate`: 현재 보증서 (공개)
- `GET /api/certificates/{serialNumber}/verify?signature=`: 진위 확인 (공개). VALID, REVOKED, SIGNATURE_MISMATCH, NOT_FOUND
- 설정: `app.certificate.hmac-secret`(`CERTIFICATE_HMAC_SECRET`), `app.certificate.key-version`(`CERTIFICATE_KEY_VERSION`, 기본 v1)
- README: 현재 구현에 보증서 추가, 남은 구현에서 HMAC TODO 항목 제거

## 검증

| 확인 항목 | 방법 | 결과 |
| --- | --- | --- |
| HMAC 값 정확성 | 알려진 HMAC-SHA256 예시 값(key="key")과 비교 | 일치 |
| 서명 문자열, 서명기, 서비스, 컨트롤러 단위 테스트 | `./gradlew build` (AI 작업 환경, Linux, Java 21) | 통과 |
| DB 직접 변조, 재발급, 동시 발급 | PostgreSQL 16 통합 테스트. 같은 작품에 5개 동시 발급 | 변조 감지, 이전 보증서 REVOKED, 유효 보증서 1개 |
| 전체 테스트 | PostgreSQL 16, Redis 7 실행 후 `./gradlew build` | 226개 중 실패 0, 건너뜀 9 (기존 조건부 테스트) |
| 실제 서버 흐름 | `bootRun` 후 로그인 토큰으로 10개 시나리오 | 발급, 공개 조회, 진위 확인 VALID, 서명 한 글자 변경 SIGNATURE_MISMATCH, DB에서 제작연도 변조 후 SIGNATURE_MISMATCH, 재발급 후 이전 보증서 REVOKED |
| 권한과 잘못된 입력 | 같은 서버 | 비로그인 발급 401, 다른 회원 발급 403, 서명 없이 검증 400, 없는 일련번호 NOT_FOUND |
| 키 노출 | 서버 로그에서 서명 키 검색 | 0건 |

미검증: Windows 로컬 실행, 프론트 연동(보증서 표시, QR).

## 남은 작업

- 키 교체 정책과 이전 키(keyVersion별) 검증 지원
- 프론트: 작품 상세의 "보증서 확인"을 발급된 보증서와 검증 링크(QR)로 연결
- 공개 검증 API 요청 횟수 제한 필요 여부
- 운영 환경 `CERTIFICATE_HMAC_SECRET` 설정 (배포 담당)
