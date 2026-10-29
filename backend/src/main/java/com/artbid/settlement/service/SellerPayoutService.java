package com.artbid.settlement.service;

import com.artbid.artwork.domain.Artwork;
import com.artbid.artwork.repository.ArtworkRepository;
import com.artbid.auction.domain.Auction;
import com.artbid.auction.repository.AuctionRepository;
import com.artbid.settlement.domain.PaymentStatus;
import com.artbid.settlement.domain.SellerPayout;
import com.artbid.settlement.domain.Settlement;
import com.artbid.settlement.repository.SellerPayoutRepository;
import com.artbid.settlement.repository.SettlementRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 정산 배치 2단계: 낙찰자 결제가 끝난 정산마다 위탁자 지급 대기(READY)를 만든다.
 * 실제 송금은 하지 않는다. 지급 시점(배송 확인 후 등)과 송금 연동은 정책 미정이다.
 * 같은 정산에 두 번 만들지 않도록 존재 여부를 확인하고, 동시에 돌아도 DB 유니크 제약(settlementId)으로 막는다.
 */
@Service
public class SellerPayoutService {

	private final SettlementRepository settlementRepository;
	private final SellerPayoutRepository sellerPayoutRepository;
	private final AuctionRepository auctionRepository;
	private final ArtworkRepository artworkRepository;
	private final BigDecimal commissionRate;

	public SellerPayoutService(SettlementRepository settlementRepository,
			SellerPayoutRepository sellerPayoutRepository,
			AuctionRepository auctionRepository,
			ArtworkRepository artworkRepository,
			// 판매 수수료율은 아직 정책 미정이라 설정값으로 둔다. 임시 기본값 10%.
			@Value("${app.settlement.seller-commission-rate:0.10}") BigDecimal commissionRate) {
		if (commissionRate == null || commissionRate.signum() < 0 || commissionRate.compareTo(BigDecimal.ONE) > 0) {
			throw new IllegalArgumentException("app.settlement.seller-commission-rate는 0 이상 1 이하여야 합니다: " + commissionRate);
		}
		this.settlementRepository = settlementRepository;
		this.sellerPayoutRepository = sellerPayoutRepository;
		this.auctionRepository = auctionRepository;
		this.artworkRepository = artworkRepository;
		this.commissionRate = commissionRate;
	}

	public List<Long> findTargetIds() {
		return settlementRepository.findIdsByStatusWithoutPayout(PaymentStatus.PAID);
	}

	/** 지급 대기를 만들었으면 true. 결제 완료가 아니거나 이미 있으면 false. */
	@Transactional
	public boolean createReady(Long settlementId, LocalDateTime now) {
		Settlement settlement = settlementRepository.findById(settlementId).orElse(null);
		if (settlement == null || settlement.getPaymentStatus() != PaymentStatus.PAID) {
			return false;
		}
		if (sellerPayoutRepository.existsBySettlementId(settlementId)) {
			return false;
		}
		Auction auction = auctionRepository.findById(settlement.getAuctionId())
				.orElseThrow(() -> new IllegalStateException("정산의 경매를 찾을 수 없습니다: " + settlement.getAuctionId()));
		Artwork artwork = artworkRepository.findById(auction.getArtworkId())
				.orElseThrow(() -> new IllegalStateException("경매의 작품을 찾을 수 없습니다: " + auction.getArtworkId()));

		sellerPayoutRepository.save(SellerPayout.ready(
				settlement, artwork.getId(), artwork.getConsignorId(), commissionRate, now));
		return true;
	}
}
