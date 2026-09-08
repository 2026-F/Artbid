package com.artbid.auction.domain;

import com.artbid.common.exception.InvalidBidException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import java.time.LocalDateTime;
// 날짜+시간을 다루는 자바 표준 클래스. "2026-09-09T13:00:00" 이런 걸 담는 타입.

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
// assertThat, assertThatThrownBy — AssertJ라는 검증(assertion) 라이브러리의 함수들.

class AuctionAntiSnipingTest {

    // ===== 헬퍼 메서드: 테스트마다 반복되는 "기본 경매 하나 만들기"를 함수로 뽑아둠 =====
    // (같은 코드를 4번 복붙하는 대신, 한 군데서 관리 → 나중에 필드 하나 추가돼도 여기만 고치면 됨)
    private Auction createOngoingAuction(LocalDateTime auctionEndAt) {
        // 파라미터로 auctionEndAt(마감시각)만 받음
        // 마감까지 얼마나 남았는지만 다르게 주면 되니까, 그 값만 바꿀 수 있게 열어둠

        return Auction.builder()
                // .builder() — Lombok @Builder가 자동으로 만들어준 메서드.
                // Auction 클래스에 생성자 대신 이렇게 "필드이름(값)"을 체이닝해서
                // 객체를 만들 수 있게 해줌. new Auction(1L, 10000L, ...) 이렇게
                // 순서만 보고는 뭐가 뭔지 헷갈리는 생성자보다 훨씬 읽기 편함.
                .artworkId(1L)
                .startPrice(10_000L)
                .currentPrice(10_000L)
                .minBidUnit(1_000L)
                .previewStart(LocalDateTime.now().minusDays(1))
                // LocalDateTime.now() — 지금 이 순간의 시각을 구함
                // .minusDays(1) — 거기서 1일을 뺌 (즉 "어제")
                .previewEnd(LocalDateTime.now())
                .auctionEndAt(auctionEndAt) // 파라미터로 받은 값 그대로 사용
                .status(AuctionStatus.ONGOING)
                .build();
        // .build() — 지금까지 채운 값들로 진짜 Auction 객체를 최종 생성함
        // (builder()만 부르고 build()를 안 부르면 객체가 안 만들어짐, 이거 항상 마지막에 필요)
    }

    @Test
    @DisplayName("마감까지 30초 넘게 남았으면 입찰해도 마감시간이 연장되지 않는다")
    void 마감_여유있을때_입찰하면_연장안됨() {
        // ---------- given (준비 단계): 테스트할 상황을 세팅 ----------
        LocalDateTime now = LocalDateTime.now(); // now == 현재 시각
        LocalDateTime originalEndAt = now.plusMinutes(5);
        // .plusMinutes(5) — now에서 5분 뒤 시각을 구함. "마감까지 5분(300초) 남은 상황" 세팅.
        Auction auction = createOngoingAuction(originalEndAt);
        // 위에서 만든 헬퍼 메서드 호출해서 "5분 뒤 마감인 진행중 경매" 객체 하나 생성

        // ---------- when (실행 단계): 실제로 테스트하고 싶은 동작을 실행 ----------
        boolean extended = auction.applyBid(11_000L, now);
        // 11,000원으로 입찰 실행. applyBid()의 리턴값(연장됐는지 여부)을 변수에 저장

        // ---------- then (검증 단계): 결과가 예상대로인지 확인 ----------
        assertThat(extended).isFalse();
        // assertThat(extended) — "extended라는 값을 검증할 거야"라고 선언
        // .isFalse() — "그 값이 false여야 한다" 라고 조건을 검증.
        //   조건이 틀리면 이 줄에서 테스트가 즉시 실패(AssertionError)로 처리됨.
        //   (5분이나 남았으니 안티스나이핑 발동 안 해야 정상 → false가 맞아야 함)

        assertThat(auction.getAuctionEndAt()).isEqualTo(originalEndAt);
        // .isEqualTo(originalEndAt) — "이 값이 originalEndAt이랑 정확히 같아야 한다"
        // (연장이 안 됐으면 마감시간이 원래 그대로여야 하니까)

        assertThat(auction.getStatus()).isEqualTo(AuctionStatus.ONGOING);
        // 상태도 그대로 ONGOING이어야 함 (EXTENDED로 안 바뀌었어야 함)

        assertThat(auction.getCurrentPrice()).isEqualTo(11_000L);
        // 참고로 연장 여부랑 상관없이, 입찰이 유효했으면 현재가는 갱신됐어야 함 — 그것도 같이 확인
    }

    @Test
    @DisplayName("마감 30초 이내에 입찰하면 마감시간이 30초 연장되고 상태가 EXTENDED로 바뀐다")
    void 마감_임박했을때_입찰하면_연장됨() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime originalEndAt = now.plusSeconds(10);
        // .plusSeconds(10) — 10초 뒤. "마감 임박(스나이핑 타이밍)" 상황 세팅.
        Auction auction = createOngoingAuction(originalEndAt);

        boolean extended = auction.applyBid(11_000L, now);

        assertThat(extended).isTrue();
        // .isTrue() — 값이 true여야 통과 (10초 남았으니 안티스나이핑 발동해야 정상)

        assertThat(auction.getAuctionEndAt()).isEqualTo(originalEndAt.plusSeconds(30));
        // 연장됐으면 "원래 마감시간 + 30초"가 새 마감시간이어야 함.
        // (여기서 originalEndAt.plusSeconds(30)처럼 "기대값도 계산해서" 비교하는 게 포인트 —
        //  하드코딩된 절대시각을 비교하면 테스트 실행 시각에 따라 매번 값이 달라져서
        //  비교가 불가능하니까, 항상 "원래값 대비 상대적으로 얼마나 변했는지"로 검증함)

        assertThat(auction.getStatus()).isEqualTo(AuctionStatus.EXTENDED);
    }

    @Test
    @DisplayName("마감까지 정확히 30초 남은 경계값에서도 연장이 발생한다")
    void 경계값_30초_정확히_남았을때도_연장됨() {
        // "경계값 테스트"란: 조건문이 <, <=, >, >= 중 뭘 쓰냐에 따라 동작이 달라지는
        // "딱 그 경계 지점"을 일부러 테스트하는 것. 실무에서 버그가 제일 잘 숨는 지점이라
        // 따로 테스트 케이스를 만들어두는 게 좋은 습관임.
        // Auction.java의 조건이 "secondsUntilEnd <= 30"이라서, 정확히 30초 남은 경우도
        // "이내"에 포함되어 연장돼야 하는 게 맞는 동작 — 이걸 명시적으로 검증하는 테스트.

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime originalEndAt = now.plusSeconds(30);
        Auction auction = createOngoingAuction(originalEndAt);

        boolean extended = auction.applyBid(11_000L, now);

        assertThat(extended).isTrue();
        assertThat(auction.getAuctionEndAt()).isEqualTo(originalEndAt.plusSeconds(30));
    }

    @Test
    @DisplayName("이미 마감시간이 지난 경매에 입찰하면 예외가 발생한다")
    void 마감후_입찰하면_예외발생() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime originalEndAt = now.minusMinutes(1);
        // .minusMinutes(1) — 1분 전. "이미 마감시간이 지나버린" 상황 세팅.
        Auction auction = createOngoingAuction(originalEndAt);

        assertThatThrownBy(() -> auction.applyBid(11_000L, now))
                // assertThatThrownBy(...) — "이 코드를 실행하면 예외가 던져질 거다"를 검증할 때 씀.
                // 즉시 실행하는 게 아니라, assertThatThrownBy 내부에서 try-catch로감싸서 실행하면서 진짜 예외가 나오는지 지켜보는 구조

                .isInstanceOf(InvalidBidException.class)
                // .isInstanceOf(...) — "던져진 예외가 정확히 이 타입(InvalidBidException)이어야 한다"

                .hasMessageContaining("이미 마감된 경매입니다");
    }
}