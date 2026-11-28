package com.artbid.liveshow.dto;

import com.artbid.auction.domain.AuctionStatus;
import com.artbid.liveshow.domain.LiveShowStatus;
import com.artbid.liveshow.domain.LotStatus;

import java.time.LocalDateTime;
import java.util.List;

/** 라이브 편성 API 요청과 응답. stageArn은 AWS 계정 정보가 들어 있어 어떤 응답에도 넣지 않는다. */
public final class LiveShowDtos {

	private LiveShowDtos() {
	}

	/** POST /api/live-shows. lots 순서가 진행 순서다. */
	public record CreateRequest(String title, String description, LocalDateTime scheduledAt, List<LotRequest> lots) {
	}

	public record LotRequest(Long auctionId, Integer durationMinutes) {
	}

	/** 작품 한 점의 차례. 현재가와 마감 시각은 경매 값이고, 실시간 변화는 기존 경매 SSE로 받는다. */
	public record LotResponse(
			Long lotId,
			int lotNo,
			LotStatus status,
			int durationMinutes,
			LocalDateTime openedAt,
			Long auctionId,
			AuctionStatus auctionStatus,
			Long currentPrice,
			Long minBidUnit,
			LocalDateTime auctionEndAt,
			Long artworkId,
			String artworkTitle,
			String artworkImageUrl,
			/** 작가가 없으면 null */
			String artistName,
			Integer productionYear) {
	}

	/**
	 * SSE show-state 이벤트. 구독 직후와 시작, 다음 작품 열기, 종료 때 보낸다.
	 * currentLot은 지금 진행 중이거나 마지막으로 진행한 작품이고, 아직 하나도 열지 않았으면 null이다.
	 * 현재가와 남은 시간의 실시간 변화는 currentLot.auctionId의 경매 SSE로 받는다.
	 */
	public record ShowStateResponse(
			Long showId,
			LiveShowStatus status,
			Long currentLotId,
			LotResponse currentLot) {
	}

	/** GET /api/live-shows/{id}. 작품별 탭에 쓰는 전체 차례 목록을 담는다. */
	public record DetailResponse(
			Long id,
			Long hostId,
			String hostNickname,
			String title,
			String description,
			LiveShowStatus status,
			LocalDateTime scheduledAt,
			LocalDateTime startedAt,
			LocalDateTime endedAt,
			Long currentLotId,
			List<LotResponse> lots) {
	}

	/** GET /api/live-shows 목록 항목. 로비와 숏폼 넘기기에 쓴다. */
	public record SummaryResponse(
			Long id,
			Long hostId,
			String hostNickname,
			String title,
			LiveShowStatus status,
			LocalDateTime scheduledAt,
			LocalDateTime startedAt,
			int lotCount,
			LotResponse currentLot) {
	}
}
