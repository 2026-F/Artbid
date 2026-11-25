package com.artbid.liveshow.domain;

import com.artbid.liveshow.exception.LiveShowConflictException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 라이브 안에서 작품 한 점의 차례. 경매 하나는 한 라이브에만 들어갈 수 있다(auction_id 유일).
 * 라이브가 끝날 때 열지 않은 차례는 지워서, 그 경매를 다른 라이브에 다시 넣을 수 있게 한다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "live_show_lot",
		uniqueConstraints = @UniqueConstraint(name = "uk_live_show_lot_auction_id", columnNames = "auctionId"),
		indexes = @Index(name = "idx_live_show_lot_show_id", columnList = "showId, lotNo"))
public class LiveShowLot {

	public static final int MIN_DURATION_MINUTES = 1;
	public static final int MAX_DURATION_MINUTES = 60;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long showId;

	@Column(nullable = false)
	private Long auctionId;

	/** 1부터 시작하는 진행 순서 */
	@Column(nullable = false)
	private int lotNo;

	/** 진행자가 이 차례를 연 뒤 마감까지 걸리는 시간 */
	@Column(nullable = false)
	private int durationMinutes;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private LotStatus status;

	private LocalDateTime openedAt;

	public static LiveShowLot of(Long showId, Long auctionId, int lotNo, Integer durationMinutes) {
		if (showId == null || auctionId == null) {
			throw new IllegalArgumentException("라이브와 경매는 필수입니다.");
		}
		if (durationMinutes == null || durationMinutes < MIN_DURATION_MINUTES || durationMinutes > MAX_DURATION_MINUTES) {
			throw new IllegalArgumentException(
					"작품별 진행 시간은 " + MIN_DURATION_MINUTES + "분 이상 " + MAX_DURATION_MINUTES + "분 이하여야 합니다.");
		}
		LiveShowLot lot = new LiveShowLot();
		lot.showId = showId;
		lot.auctionId = auctionId;
		lot.lotNo = lotNo;
		lot.durationMinutes = durationMinutes;
		lot.status = LotStatus.WAITING;
		return lot;
	}

	public void open(LocalDateTime now) {
		if (status != LotStatus.WAITING) {
			throw new LiveShowConflictException("대기 중인 작품만 열 수 있습니다. 현재 상태: " + status);
		}
		this.status = LotStatus.OPEN;
		this.openedAt = now;
	}

	public void finish() {
		if (status == LotStatus.OPEN) {
			this.status = LotStatus.DONE;
		}
	}
}
