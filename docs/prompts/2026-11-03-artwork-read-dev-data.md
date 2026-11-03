# 작품 조회 계약과 로컬 준비 도구

- 작성일: 2026-11-03
- 관련 이슈: [BE #98](https://github.com/2026-F/Artbid/issues/98), [BE #99](https://github.com/2026-F/Artbid/issues/99), [FE #52](https://github.com/2026-F/frontend/issues/52)
- 브랜치: `fix/artwork-query-and-dev-data`. 작업 브랜치에서 구현하고 배포 전 검증을 진행했다.

## 목적과 핵심 요청

요약: 사용자가 승인한 1~4단계 중 로컬 개발 데이터 준비와 작품 조회 계약 보완을 구현한다. DB 스키마, 인증/권한/거래 처리 및 FE #53은 변경하지 않는다. 기존 미커밋 파일은 보존한다.

> 커밋·푸시·PR 생성·병합·배포는 수행하지 않는다.

## 선택과 이유

| 방법 | 선택 | 이유 |
| --- | --- | --- |
| 실제 API를 통한 개발 데이터 준비 | 채택 | 실제 등록/승인/경매 생성 로직으로 반복 시연한다. 역할 부여만 SQL로 제한한다. |
| 최신 연결 경매 = 최대 경매 ID | 채택 | 기존 목록 규칙을 상세에도 적용한다. 상태 기반 추측이나 ID 동일성 가정을 없앤다. |
| PHOTO 정렬 후 첫 유효 URL | 채택 | 목록과 상세에서 같은 대표 이미지가 나오도록 한다. 없으면 기존 imageUrl을 사용한다. |
| 미디어 일괄 조회 | 채택 | 목록의 각 작품마다 조회하는 N+1을 피한다. |
| 전체 DB 초기화 | 제외 | 다른 데이터 보존이 사용자 요구다. 기록한 ID와 실제 식별 정보를 함께 확인한다. |
| 거래 관계까지 자동 삭제 | 제외 | 후속 입찰/정산 시나리오의 증거를 없애지 않도록 정리를 중단한다. |

## 반영 내용과 코드 리뷰 순서

1. `backend/src/main/java/com/artbid/artwork/dto/ArtworkDetailResponse.java`: nullable auctionId를 응답에 추가했다. 기존 필드는 유지했다.
2. `backend/src/main/java/com/artbid/artwork/service/ArtworkService.java`: 목록의 경매/작가/미디어 일괄 조회, 상세의 최대 경매 ID 선택을 확인한다.
3. `backend/src/main/java/com/artbid/artwork/dto/ArtworkImages.java`와 `ArtworkPageResponse.java`, `ArtworkSummaryResponse.java`: 대표 이미지 선택과 DTO 조립을 확인한다.
4. `backend/src/main/java/com/artbid/media/repository/ArtworkMediaRepository.java`: 작품 ID 집합으로 미디어를 한 번 조회한다.
5. `backend/src/test/java/com/artbid/artwork/service/ArtworkReadContractTest.java`: 작품 ID 10과 경매 ID 25, 사진/동영상/빈 URL, fallback, null 경매, 페이지 정보를 확인한다. 컨트롤러 테스트 생성자도 새 필드에 맞췄다.
6. `scripts/dev-data.mjs`, `package.json`, `.gitignore`: API 준비, manifest, 생성 의도 기록, 정리 보호장치를 확인한다. [실행 안내](../local-dev-data.md)를 먼저 읽는다.

## 검증

| 항목 | 방법 | 결과 |
| --- | --- | --- |
| 최초 전체 빌드/테스트 | `./gradlew.bat --console=plain build` | 성공. 단, 기존 DB 통합 테스트가 로컬 데이터를 남겼다. 아래 기록 참조. |
| 최종 조회 계약 테스트/빌드 | `./gradlew.bat --console=plain test --tests '*ArtworkReadContractTest' --tests '*ArtworkControllerTest' bootJar` | 성공 |
| 스크립트 문법 | `node --check scripts/dev-data.mjs` | 성공 |
| 중복 실행 | 같은 비밀번호로 seed 두 번, manifest 비교 | 동일 ID 유지 |
| 새 경매/마감 임박 | 실제 로컬 API | 작품 18/19와 경매 13/14 생성. 마감 후 CLOSED 확인 |
| 실제 목록/상세 | size=2로 전체 페이지 조회 | 모든 작품 접근, auctionId/imageUrl 일치 |
| 정리 범위 | 외부 관계가 있는 첫 실행, 독립된 새 실행 | 첫 실행 거절, 새 실행 성공. 이전 작품 13건 보존 |

환경: Java 21, 로컬 Compose PostgreSQL/Redis. 외부 결제, 미디어 업로드, 실제 배포 환경은 검증하지 않았다. PHOTO 우선순위와 N+1 방지는 저장소 mock 기반 서비스 테스트로 검증했고 실제 업로드 서버로 검증한 것은 아니다.

전체 테스트와 시연 API를 동시에 사용하면 고정 ID를 쓰는 기존 테스트가 시연 작품에 경매/입찰을 붙일 수 있다. 발견 후 변경 관련 테스트로 검증 범위를 분리했다. 잔여 데이터와 보존 manifest의 정확한 상태는 [실행 안내](../local-dev-data.md#이번-검증에서-보존한-기록)에 남겼다.

## 남은 작업

- 배포된 BE와 FE가 함께 새 계약을 사용하는지 배포 이후 확인한다. FE #52는 열어 둔다.
- 계정 소유권/권한 등 BE #94~96과 로그인 입찰/이력/SSE 등 FE #53은 후속 작업이다.
- 기존 전체 통합 테스트의 DB 격리와 종료 후 데이터 정리는 별도 개선 대상이다.
