package com.artbid.auction.live;

import com.artbid.auction.domain.Auction;
import com.artbid.auction.domain.AuctionStatus;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 캐시에 저장하는 경매의 실시간 상태.
 * 잔여시간은 보는 시점마다 달라지므로 저장하지 않고 마감 시각만 저장한다. 잔여시간은 {@link #toView}에서 계산한다.
 *
 * @param version 상태가 바뀐 시각(epoch 마이크로초). 입찰과 취소는 경매 행 락 안에서 시각을 잡으므로
 *                같은 경매 안에서는 커밋 순서와 같다. 커밋 후 처리가 늦게 도착해도 더 오래된 상태가
 *                최신 상태를 덮어쓰지 않도록 비교하는 데 쓴다. DB에서 채운 스냅샷은 0이다.
 */
public record AuctionLiveState(
		Long auctionId,
		Long currentPrice,
		Long minBidUnit,
		LocalDateTime auctionEndAt,
		AuctionStatus status,
		long version) {

	public static final long SNAPSHOT_VERSION = 0L;

	/** 입찰, 취소, 마감처럼 상태가 바뀐 직후의 값. changedAt은 변경을 확정한 시각이다. */
	public static AuctionLiveState changed(Auction auction, LocalDateTime changedAt) {
		return new AuctionLiveState(
				auction.getId(),
				auction.getCurrentPrice(),
				auction.getMinBidUnit(),
				auction.getAuctionEndAt(),
				auction.getStatus(),
				toVersion(changedAt));
	}

	/** 캐시가 비어 있어 DB에서 읽어 온 값. 변경 이벤트보다 항상 우선순위가 낮다. */
	public static AuctionLiveState snapshot(Auction auction) {
		return new AuctionLiveState(
				auction.getId(),
				auction.getCurrentPrice(),
				auction.getMinBidUnit(),
				auction.getAuctionEndAt(),
				auction.getStatus(),
				SNAPSHOT_VERSION);
	}

	/** 마감까지 남은 초. 마감됐거나 시각이 지났으면 0. */
	public long remainingSeconds(LocalDateTime now) {
		if (status == AuctionStatus.CLOSED || auctionEndAt == null) {
			return 0L;
		}
		long millis = Duration.between(now, auctionEndAt).toMillis();
		return millis <= 0 ? 0L : millis / 1000;
	}

	public AuctionLiveView toView(LocalDateTime now, AuctionLiveReason reason, boolean extended) {
		return new AuctionLiveView(
				auctionId,
				currentPrice,
				minBidUnit,
				currentPrice + minBidUnit,
				auctionEndAt,
				status,
				remainingSeconds(now),
				now,
				version,
				reason,
				extended);
	}

	static long toVersion(LocalDateTime time) {
		Instant instant = time.atZone(ZoneId.systemDefault()).toInstant();
		return instant.getEpochSecond() * 1_000_000L + instant.getNano() / 1_000L;
	}
}
