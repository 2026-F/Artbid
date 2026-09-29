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
	private static final long ADD_WINDOW_SECONDS = 30;
	private static final long ADD_EXTEND_SECONDS = 30;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private Long artworkId;
	private Long startPrice;   // 시작가
	private Long currentPrice; // 현재가 (입찰마다 갱신되는 값 — 이번 PoC의 핵심 필드)
	private Long minBidUnit;   // 최소 입찰 단위 (예: 1000원 단위로만 올릴 수 있음)

	private LocalDateTime previewStart;
	private LocalDateTime previewEnd;
	private LocalDateTime auctionEndAt;

	@Enumerated(EnumType.STRING)
	private AuctionStatus status;

	public static Auction create(
		Long artworkId,
		Long startPrice,
		Long minBidUnit,
		LocalDateTime previewStart,
		LocalDateTime previewEnd,
		LocalDateTime auctionEndAt,
		LocalDateTime now){

		validateCreate(artworkId, startPrice, minBidUnit, previewStart, previewEnd, auctionEndAt);

		return Auction.builder()
				.artworkId(artworkId)
				.startPrice(startPrice)
				.minBidUnit(minBidUnit)
				.previewStart(previewStart)
				.previewEnd(previewEnd)
				.auctionEndAt(auctionEndAt)
				.currentPrice(startPrice)
				.status(startStatus(previewStart, now))
				.build();
	}

private static void validateCreate(Long artworkId,
								  Long startPrice,
								  Long minBidUnit,
								  LocalDateTime previewStart,
								  LocalDateTime previewEnd,
								  LocalDateTime auctionEndAt){
		if(artworkId == null){
			throw new IllegalArgumentException("artworkId는 필수입니다.");
		}
		if (startPrice == null || startPrice <= 0) {
			throw new IllegalArgumentException("시작가는 0보다 커야 합니다.");
		}
		if (minBidUnit == null || minBidUnit <= 0) {
			throw new IllegalArgumentException("최소 입찰 단위는 0보다 커야 합니다.");
		}
		if (previewStart == null || previewEnd == null || auctionEndAt == null) {
			throw new IllegalArgumentException("프리뷰/마감 시각은 모두 필수입니다.");
		}
		// 프리뷰 시작 < 프리뷰 종료(=경매 시작) <= 경매 마감 순서를 강제한다.
		if (!previewStart.isBefore(previewEnd)) {
			throw new IllegalArgumentException("프리뷰 시작 시각은 프리뷰 종료 시각보다 빨라야 합니다.");
		}
		if (auctionEndAt.isBefore(previewEnd)) {
			throw new IllegalArgumentException("경매 마감 시각은 프리뷰 종료 시각보다 빠를 수 없습니다.");
		}
    }

	private static AuctionStatus startStatus(LocalDateTime previewStart, LocalDateTime now){
		return now.isBefore(previewStart) ? AuctionStatus.SCHEDULED : AuctionStatus.PREVIEW;
	}

	public boolean applyBid(Long price, LocalDateTime now) {
		validateBid(price, now);

		this.currentPrice = price;

		boolean extended = false;
		long secondsUntilEnd = Duration.between(now, this.auctionEndAt).getSeconds();
		if (secondsUntilEnd <= ADD_WINDOW_SECONDS) {
			this.auctionEndAt = this.auctionEndAt.plusSeconds(ADD_EXTEND_SECONDS);
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