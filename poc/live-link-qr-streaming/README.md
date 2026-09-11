# 외부 라이브 연동 검증 PoC (트랙 2 - 라이브 스트리밍)

`backend/`와는 별도로 독립 실행되는 Spring Boot 앱입니다. 팀 컨벤션(Gradle, `com.artbid`
그룹, 도메인 기준 패키지, Lombok)에 맞춰 구성했습니다.

## 검증 목적
- 위탁자가 외부 플랫폼에서 라이브 방송을 송출한다고 가정 (이 PoC는 유튜브로 대체 검증)
- 시청자가 **고정 링크 또는 QR코드**로 입찰 페이지에 정상 진입하는지 확인
- 입찰 페이지에서 **실시간 호가**가 반영되는지 확인 (WebSocket 브로드캐스트)
- RTMP/HLS는 직접 구현하지 않고 외부 플랫폼에 위임

> **실 서비스와의 차이**: README에 명시된 실제 아키텍처는 라이브 스트리밍을 AWS
> IVS(RTMP 송출 → HLS 재생)로 처리합니다. 이 PoC는 스트리밍 소스를 유튜브 iframe으로
> 대체해서 "고정 링크/QR 진입 → 실시간 호가 반영" 흐름만 검증한 것이며, 영상 소스
> 자체(IVS 채널 생성, streamKey 발급 등)는 검증 범위가 아닙니다. 실제로 IVS로 붙일 때는
> `streaming.controller.StreamingPageController`가 넘기는 영상 URL을 유튜브 embed 대신
> IVS의 HLS 재생 URL(예: `<video>` + hls.js)로 바꾸면 됩니다.

## 패키지 구조 (backend 컨벤션에 맞춤)
```
com.artbid
├── LiveLinkQrPocApplication.java
├── config/                      앱 설정 (AppProperties, TaskScheduler 빈)
├── infra/realtime/              WebSocket(STOMP) 설정 — backend의 infra/realtime 자리
├── streaming/controller/        고정 링크 페이지, QR코드 생성, 데모 목록
└── auction/
    ├── domain/                  BidItem, BidUpdateMessage
    ├── service/                 BidService (메모리 상태 + 브로드캐스트)
    └── controller/               수동 입찰/시뮬레이션 제어 API
```
(영속성/동시성 제어가 필요 없어 `repository` 패키지는 생성하지 않았습니다.)

## 실행 방법 (IntelliJ)
1. `poc/live-link-qr-streaming` 폴더를 IntelliJ에서 **Open** (Gradle 프로젝트로 인식,
   Gradle Wrapper는 backend와 동일하게 IntelliJ가 열 때 자동 생성됩니다)
2. `LiveLinkQrPocApplication` 실행
3. 브라우저에서 `http://localhost:8080` 접속 → 데모 아이템 목록/QR 확인

## 검증 시나리오
1. 유튜브에 테스트용 라이브 또는 영상을 올리고 `application.yml`의
   `app.demo-items.item1.youtube-video-id` 값을 그 영상의 video ID로 교체
   (`https://www.youtube.com/watch?v=XXXXXXXXXXX` 에서 `XXXXXXXXXXX` 부분)
2. 서버 실행 후 `http://localhost:8080` → item1의 QR코드/링크 확인
3. **고정 링크 진입 확인**: 링크 클릭 또는 QR 스캔으로 `/bid/item1` 페이지 정상 진입 확인
   - 폰으로 QR 스캔 테스트 시 `app.public-base-url`을 PC의 LAN IP로 설정
     (예: `http://192.168.0.10:8080`, `ipconfig`/`ip a`로 확인)
4. **영상 재생 확인**: 입찰 페이지에 유튜브 영상이 임베드되어 재생되는지 확인
5. **실시간 호가 반영 확인**:
   - 페이지 진입 시 자동으로 3초 간격 시뮬레이션이 시작되어 호가가 계속 오름
   - "테스트용 입찰하기" 버튼으로 즉시 호가를 올리고, 다른 브라우저 탭/기기에서 같은
     링크를 열어 **동시에** 갱신되는지 확인 (WebSocket 브로드캐스트 다중 클라이언트 전파 검증)

## 한계 (PoC 범위)
- 실제 경매 로직(입찰 유효성, 낙찰, 안티 스나이핑, 사용자 인증)은 없음
- 위탁자 송출 자체는 검증 범위 밖 (유튜브에 직접 업로드/라이브 송출해서 확인)
- 여러 서버 인스턴스로 확장 시 STOMP 브로커를 외부 브로커(RabbitMQ 등)로 교체 필요
  (`backend`의 infra/realtime에 예정된 Redis Pub/Sub 중계와 같은 방향)
