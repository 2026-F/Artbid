package com.artbid.auction.service;

import com.artbid.auction.domain.Auction;
import com.artbid.auction.domain.AuctionStatus;
import com.artbid.auction.dto.AuctionDetailResponse;
import com.artbid.auction.live.AuctionLiveChangedEvent;
import com.artbid.auction.live.AuctionLiveReason;
import com.artbid.auction.live.AuctionLiveState;
import com.artbid.auction.repository.AuctionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.artbid.artwork.repository.ArtworkRepository;
import com.artbid.artwork.domain.Artwork;
import com.artbid.artwork.domain.ArtworkStatus;
import com.artbid.common.exception.AuctionCreationConflictException;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuctionService {

	private final AuctionRepository auctionRepository;
	private final ArtworkRepository artworkRepository;
	private final ApplicationEventPublisher eventPublisher;

	public Auction getAuction(Long id) {
		return auctionRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("경매를 찾을 수 없습니다: " + id));
	}

	@Transactional
	public Auction createAuction(Long artworkId,
								 Long startPrice,
								 Long minBidUnit,
								 LocalDateTime previewStart,
								 LocalDateTime previewEnd,
								 LocalDateTime auctionEndAt) {
		Artwork artwork = artworkRepository.findById(artworkId)
				.orElseThrow(() -> new IllegalArgumentException("작품을 찾을 수 없습니다 :" + artworkId));
		// 심사 승인된(PREVIEW) 작품에만 경매를 연다. 마감되지 않은 경매가 이미 있으면 새로 열지 않는다.
		if (artwork.getStatus() != ArtworkStatus.PREVIEW) {
			throw new AuctionCreationConflictException("승인된(PREVIEW) 작품에만 경매를 열 수 있습니다. 현재 상태: " + artwork.getStatus());
		}
		boolean hasOpenAuction = auctionRepository.findByArtworkIdIn(List.of(artworkId)).stream()
				.anyMatch(existing -> existing.getStatus() != AuctionStatus.CLOSED);
		if (hasOpenAuction) {
			throw new AuctionCreationConflictException("이미 마감되지 않은 경매가 있는 작품입니다: " + artworkId);
		}

		Auction auction = Auction.create(artworkId, startPrice, minBidUnit, previewStart, previewEnd, auctionEndAt, LocalDateTime.now());


		return auctionRepository.save(auction);
	}

	public AuctionDetailResponse getAuctionDetail(Long auctionId){
		Auction auction = auctionRepository.findById(auctionId)
				.orElseThrow(() -> new IllegalArgumentException("경매를 찾을 수 없습니다: " + auctionId));

		Artwork artwork = artworkRepository.findById(auction.getArtworkId())
				.orElseThrow(() -> new IllegalArgumentException("작품을 찾을 수 없습니다 :" + auction.getArtworkId()));


		return new AuctionDetailResponse(
				auction.getId(),
				auction.getCurrentPrice(),
				auction.getStartPrice(),
				auction.getMinBidUnit(),
				auction.getPreviewStart(),
				auction.getPreviewEnd(),
				auction.getAuctionEndAt(),
				auction.getStatus(),
				artwork.getTitle(),
				artwork.getImageUrl()
		);
	}

	// 관리자가 경매 상태를 직접 바꾼다 (어드민 전용, AuctionController에서 권한 확인 후 호출).
	// 새 상태가 ONGOING이면, 연결된 작품도 "경매 중"으로 같이 맞춰준다.
	@Transactional
	public void updateStatus(Long auctionId, AuctionStatus status) {
		Auction auction = auctionRepository.findByIdForUpdate(auctionId)
				.orElseThrow(() -> new IllegalArgumentException("경매를 찾을 수 없습니다: " + auctionId));

		if (status == AuctionStatus.CLOSED) {
			throw new AuctionCreationConflictException("경매는 마감 시각 이후 자동으로 마감됩니다.");
		}

		// 마감된 경매를 되살리면 마감 시각이 지나 있어 곧바로 다시 마감되고 정산이 또 생긴다. 재오픈은 새 경매로 한다.
		AuctionStatus previousStatus = auction.getStatus();
		if (previousStatus == AuctionStatus.CLOSED) {
			throw new AuctionCreationConflictException("마감된 경매는 상태를 바꿀 수 없습니다. 다시 열기를 사용해주세요.");
		}

		auction.overrideStatus(status);

		if (status == AuctionStatus.ONGOING) {
			artworkRepository.findById(auction.getArtworkId()).ifPresent(Artwork::markInAuction);
		}

		if (previousStatus != status) {
			eventPublisher.publishEvent(new AuctionLiveChangedEvent(
					AuctionLiveState.changed(auction, LocalDateTime.now()), AuctionLiveReason.SNAPSHOT, false));
		}
	}

}
