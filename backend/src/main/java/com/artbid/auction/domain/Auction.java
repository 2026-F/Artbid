package com.artbid.auction.domain;

import com.artbid.common.exception.InvalidBidException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE) // Builder 전용, 외부에서 직접 new로 필드 다 채우는 걸 막음
@Builder
public class Auction {

	// 마감 30초 이내 입찰이면 마감을 30초 연장한다 (안티 스나이핑)
	private static final long ANTI_SNIPING_WINDOW_SECONDS = 30;
	private static final long ANTI_SNIPING_EXTEND_SECONDS = 30;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private Long artworkId;
	private Long startPrice;   // 시작가 (경매 생성 시 고정)
	private Long currentPrice; // 현재가 (입찰마다 갱신되는 값 — 이번 PoC의 핵심 필드)
	private Long minBidUnit;   // 최소 입찰 단위 (예: 1000원 단위로만 올릴 수 있음)

	private LocalDateTime previewStart;
	private LocalDateTime previewEnd;
	private LocalDateTime auctionEndAt;

	@Enumerated(EnumType.STRING)
	private AuctionStatus status;

	/**
	 * 입찰을 검증하고 반영한다.
	 * 반드시 이 경매 행이 SELECT ... FOR UPDATE로 잠긴 트랜잭션 안에서 호출되어야 한다.
	 * (BidService.submitBid 참고)
	 *
	 * @return 마감 시각이 연장되었으면 true
	 */
	public boolean applyBid(Long price, LocalDateTime now) {
		validateBid(price, now);

		this.currentPrice = price;

		boolean extended = false;
		long secondsUntilEnd = Duration.between(now, this.auctionEndAt).getSeconds();
		if (secondsUntilEnd <= ANTI_SNIPING_WINDOW_SECONDS) {
			this.auctionEndAt = this.auctionEndAt.plusSeconds(ANTI_SNIPING_EXTEND_SECONDS);
			this.status = AuctionStatus.EXTENDED;
			extended = true;
		}
		return extended;
	}

	private void validateBid(Long price, LocalDateTime now) {
		if (status != AuctionStatus.ONGOING && status != AuctionStatus.EXTENDED) {
			throw new InvalidBidException("진행 중인 경매가 아닙니다. 현재 상태: " + status);
		}
		if (now.isAfter(this.auctionEndAt)) {
			throw new InvalidBidException("이미 마감된 경매입니다.");
		}
		long minValidPrice = this.currentPrice + this.minBidUnit;
		if (price < minValidPrice) {
			throw new InvalidBidException(
					"입찰가가 너무 낮습니다. 현재가 " + currentPrice + "원, 최소 " + minValidPrice + "원 이상 입력하세요.");
		}
	}
}