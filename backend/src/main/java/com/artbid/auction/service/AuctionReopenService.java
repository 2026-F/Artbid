package com.artbid.auction.service;

import com.artbid.artwork.domain.Artwork;
import com.artbid.artwork.repository.ArtworkRepository;
import com.artbid.auction.domain.Auction;
import com.artbid.auction.domain.AuctionStatus;
import com.artbid.auction.repository.AuctionRepository;
import com.artbid.common.exception.AuctionCreationConflictException;
import com.artbid.common.exception.InvalidAuctionRequestException;
import com.artbid.payment.domain.PaymentStatus;
import com.artbid.payment.repository.PaymentRepository;
import com.artbid.settlement.domain.Settlement;
import com.artbid.settlement.repository.SettlementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 마감된 경매의 작품을 같은 조건의 새 경매로 다시 연다.
 * 기존 경매를 되살리지 않고 새 경매를 만든다. 이전 입찰과 정산을 이력으로 남기고, 다시 마감돼도 정산이 겹치지 않는다.
 */
@Service
@RequiredArgsConstructor
public class AuctionReopenService {

	private static final int MAX_DURATION_MINUTES = 60 * 24 * 30;

	private final AuctionRepository auctionRepository;
	private final ArtworkRepository artworkRepository;
	private final SettlementRepository settlementRepository;
	private final PaymentRepository paymentRepository;

	@Transactional
	public Auction reopen(Long closedAuctionId, Integer durationMinutes) {
		if (durationMinutes == null || durationMinutes < 1 || durationMinutes > MAX_DURATION_MINUTES) {
			throw new InvalidAuctionRequestException("진행 시간은 1분 이상 " + MAX_DURATION_MINUTES + "분 이하로 입력해주세요.");
		}

		// 같은 경매를 동시에 다시 열어도 새 경매가 하나만 생기도록 행을 잠근다.
		Auction closed = auctionRepository.findByIdForUpdate(closedAuctionId)
				.orElseThrow(() -> new IllegalArgumentException("경매를 찾을 수 없습니다: " + closedAuctionId));
		if (closed.getStatus() != AuctionStatus.CLOSED) {
			throw new AuctionCreationConflictException("마감된 경매만 다시 열 수 있습니다. 현재 상태: " + closed.getStatus());
		}
		requireLatestAuction(closed);
		cancelUnpaidSettlements(closedAuctionId);

		Artwork artwork = artworkRepository.findById(closed.getArtworkId())
				.orElseThrow(() -> new IllegalArgumentException("작품을 찾을 수 없습니다: " + closed.getArtworkId()));
		artwork.reopenForAuction();

		LocalDateTime now = LocalDateTime.now();
		Auction reopened = Auction.create(closed.getArtworkId(), closed.getStartPrice(), closed.getMinBidUnit(),
				now.minusMinutes(1), now, now.plusMinutes(durationMinutes), now);
		reopened.overrideStatus(AuctionStatus.ONGOING);
		return auctionRepository.save(reopened);
	}

	private void requireLatestAuction(Auction closed) {
		boolean superseded = auctionRepository.findByArtworkIdIn(List.of(closed.getArtworkId())).stream()
				.anyMatch(other -> other.getStatus() != AuctionStatus.CLOSED || other.getId() > closed.getId());
		if (superseded) {
			throw new AuctionCreationConflictException("이 작품의 최신 경매가 아닙니다: " + closed.getId());
		}
	}

	// 결제가 끝났거나 결과를 모르는 결제가 있으면 PG에서 실제로 승인됐을 수 있으므로 다시 열지 않는다.
	private void cancelUnpaidSettlements(Long auctionId) {
		List<Settlement> settlements = settlementRepository.findAllByAuctionIdForUpdate(auctionId);
		for (Settlement settlement : settlements) {
			if (paymentRepository.existsBySettlementIdAndStatusIn(settlement.getId(),
					List.of(PaymentStatus.REQUESTED, PaymentStatus.PAID))
					|| settlement.getPaymentStatus() == com.artbid.settlement.domain.PaymentStatus.PAID) {
				throw new AuctionCreationConflictException("결제가 완료됐거나 진행 중인 경매는 다시 열 수 없습니다: " + auctionId);
			}
		}
		settlements.forEach(Settlement::cancelForReopen);
	}
}
