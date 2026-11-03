# 로컬 개발 데이터 준비

관련 이슈: [BE #98](https://github.com/2026-F/Artbid/issues/98), [BE #99](https://github.com/2026-F/Artbid/issues/99), [FE #52](https://github.com/2026-F/frontend/issues/52).

## 실행

Node.js 20 이상, Docker Compose의 PostgreSQL, localhost:8080 백엔드가 필요하다. 프론트를 localhost:3000에서 실행하면 개발용 작품 이미지도 표시된다. 저장소 루트에서 실행한다. 비밀번호는 직접 정하고 동일한 준비 데이터의 재실행에는 같은 값을 사용한다.

```bash
docker compose up -d postgres redis
read -s -p '로컬 테스트 비밀번호: ' ARTBID_TEST_PASSWORD
export ARTBID_TEST_PASSWORD
npm run dev:seed
npm run dev:auction:new
npm run dev:auction:closing
npm run dev:cleanup
unset ARTBID_TEST_PASSWORD
```

PowerShell에서는 비밀번호를 명령 이력에 남기지 않도록 다음과 같이 입력한다.

```powershell
$env:ARTBID_TEST_PASSWORD = [System.Net.NetworkCredential]::new('', (Read-Host '로컬 테스트 비밀번호' -AsSecureString)).Password
npm run dev:seed
# 검증이 끝나면
npm run dev:cleanup
Remove-Item Env:ARTBID_TEST_PASSWORD
```

| 명령 | 동작 |
| --- | --- |
| `dev:seed` | 관리자, 판매자, 입찰자 A/B와 작가, 경매 없는 작품, 이미지 없는 작품, 시작 전/진행 경매를 준비한다. 기록이 있으면 재사용한다. |
| `dev:auction:new` | 기존 경매를 되살리지 않고 새 작품과 24시간 경매를 만든다. 호출할 때마다 새 경매가 생긴다. |
| `dev:auction:closing` | 새 작품과 약 2분 뒤 마감하는 경매를 만든다. |
| `dev:cleanup` | 기록한 계정/작가/작품/경매만 트랜잭션으로 정리한다. 비밀번호는 필요 없다. |

생성 계정 이메일과 ID는 출력 및 `.local-dev/data.json`에 기록한다. 비밀번호와 토큰은 기록하지 않는다. `.local-dev/`는 Git에서 제외한다. 기본 API 주소는 `http://localhost:8080`이며 `ARTBID_LOCAL_API`에는 동일 포트의 localhost 또는 127.0.0.1만 허용한다. Docker 원격 컨텍스트도 거절한다. DB는 이 저장소 Compose의 `artbid`를 사용한다. 다른 DB를 바라보는 백엔드에서는 실행하지 않는다.

## 구현과 안전장치

가입, 로그인, 작가/작품 등록, 승인, 경매 생성과 진행 상태 변경은 실제 API를 호출한다. 초기 역할 부여만 로컬 DB에서 ID와 이메일을 함께 확인한 후 수행한다. 준비된 작품은 `[DEV 실행ID]` 제목으로 구분한다. 작품/경매 ID가 우연히 같으면 추가 작품을 생성해 서로 다른 ID의 사례를 만든다.

`seed`는 만료된 경매를 다시 열지 않는다. 다음 시연에는 `auction:new`를 사용한다. 시간은 기존 백엔드의 `LocalDateTime`에 맞추므로 도구와 JVM의 로컬 시간대를 같게 설정한다.

정리 시 기록 밖의 경매나 미디어, 입찰, 정산, 방송, 알림, 판매자 프로필, 보증서 등이 연결되어 있으면 삭제하지 않는다. 거래 증거를 임의로 지우지 않기 위한 제한이다. DB 전체 초기화나 시퀀스 초기화는 수행하지 않는다. 기존 통합 테스트 일부는 DB에 데이터를 남기고 고정 작품/입찰자 ID를 사용하므로, 시연 데이터가 있는 DB에서 전체 테스트를 동시에 실행하지 않는다. 변경 관련 단위 테스트부터 실행한다.

## 실패 복구

- 명령 실행 중 종료되면 `.local-dev/data.lock`이 남을 수 있다. 실행 중인 명령이 없는지 확인한 뒤 잠금 파일만 제거한다.
- 생성 요청 전에 `pending`을 저장한다. 응답을 받지 못했으면 서버에 생성됐을 수 있으므로 자동 재생성을 중단한다.
- `pending`이 있으면 API와 로컬 DB를 조회해 실행 ID, 계정 이메일, 작품 제목과 연결 관계로 결과를 확인한다. 생성됐다면 해당 식별자를 manifest의 올바른 항목에 반영하고, 생성되지 않았음이 확실한 경우에만 pending을 null로 바꾼다. 결과를 확인하지 않은 채 manifest를 삭제하지 않는다.
- 정리가 거절되면 원래 manifest를 유지하고 관련 데이터를 검토한다. 정리 성공 시 `cleaned-실행ID.json`으로 기록을 보존한다.

## 이번 검증에서 보존한 기록

첫 준비 데이터와 기존 전체 통합 테스트가 같은 DB에서 겹쳤다. 정리 도구가 외부 관계를 감지해 삭제를 거절했다. 첫 manifest는 `.local-dev/preserved-test-overlap.json`에 보존했다. 이번 동시성 테스트가 만든 경매 3/4, 입찰 35건과 해당 정산 1건만 별도 식별 조건과 결제/배송/지급 부재를 확인해 정리했다. 다른 통합 테스트 결과와 첫 준비 작품 13건은 보존했다.

이후 별도 실행 ID로 준비한 작품 14~19와 경매 11~14를 검증하고 `dev:cleanup`으로 정리했다. 해당 작품은 0건, 이전 작품은 13건으로 확인했다. 다음 `dev:seed`는 새 비밀번호와 새 실행 ID로 시작할 수 있다.

추가 관리자/판매자 UI 회귀 검증에는 작품 20~23과 경매 15/16을 사용했다. 정리 시 기존 보증서 테스트 작품 5~7이 이번에 생성된 작가 ID 3을 이미 참조하고 있어 중단됐다. 원본 manifest를 `.local-dev/preserved-shared-artist.json`에 복사하고, 공유된 작가 3만 삭제 대상에서 제외한 뒤 정리를 완료했다. 검증 작품/계정은 0건, 이전 작품 13건과 작가 3은 보존됐다. 임시 로그인 파일은 사용 즉시 제거했다.
