package com.artbid.settlement.service;

import com.artbid.artwork.domain.Artwork;
import com.artbid.artwork.domain.ArtworkCategory;
import com.artbid.artwork.repository.ArtworkRepository;
import com.artbid.auction.domain.Auction;
import com.artbid.auction.domain.AuctionStatus;
import com.artbid.auction.repository.AuctionRepository;
import com.artbid.payment.domain.Payment;
import com.artbid.payment.domain.PaymentMethod;
import com.artbid.payment.repository.PaymentRepository;
import com.artbid.settlement.domain.PaymentStatus;
import com.artbid.settlement.domain.SellerPayout;
import com.artbid.settlement.domain.SellerPayoutStatus;
import com.artbid.settlement.domain.Settlement;
import com.artbid.settlement.repository.SellerPayoutRepository;
import com.artbid.settlement.repository.SettlementRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

// 로컬 docker-compose의 postgres·redis가 떠 있어야 통과함 (BidServiceConcurrencyTest와 같은 조건, CI는 서비스 컨테이너 사용)
// 실제 DB에서 배치 조회 쿼리(JPQL not exists 등), 행 잠금, 유니크 제약까지 함께 확인한다.
@SpringBootTest
class SettlementBatchPostgresTest {

	@Autowired
	private SettlementBatchScheduler scheduler;
	@Autowired
	private ArtworkRepository artworkRepository;
	@Autowired
	private AuctionRepository auctionRepository;
	@Autowired
	private SettlementRepository settlementRepository;
	@Autowired
	private SellerPayoutRepository sellerPayoutRepository;
	@Autowired
	private PaymentRepository paymentRepository;

	private final LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);

	private Long auctionOf(Long consignorId) {
		Artwork artwork = artworkRepository.save(Artwork.builder()
				.consignorId(consignorId)
				.artistId(1L)
				.title("정산 배치 테스트")
				.category(ArtworkCategory.PAINTING)
				.startPrice(10_000L)
				.build());
		Auction auction = auctionRepository.save(Auction.builder()
				.artworkId(artwork.getId())
				.startPrice(10_000L)
				.currentPrice(100_000L)
				.minBidUnit(1_000L)
				.previewStart(now.minusDays(5))
				.previewEnd(now.minusDays(4))
				.auctionEndAt(now.minusDays(3))
				.status(AuctionStatus.CLOSED)
				.build());
		return auction.getId();
	}

	private Settlement settlement(Long auctionId, long finalPrice, LocalDateTime deadline) {
		return settlementRepository.save(Settlement.create(auctionId, 2L, finalPrice, finalPrice / 10, 30_000, deadline));
	}

	@Test
	void 미결제_만료와_지급_대기_생성을_실제_DB에서_처리한다() {
		// DB에 이전 실행 데이터가 남아 있어도 겹치지 않도록 매번 다른 위탁자 ID를 쓴다
		Long consignorId = ThreadLocalRandom.current().nextLong(1_000_000_000L, Long.MAX_VALUE / 2);
		Settlement overdue = settlement(auctionOf(consignorId), 100_000, now.minusHours(1));
		Settlement notYet = settlement(auctionOf(consignorId), 100_000, now.plusDays(1));
		Settlement waitingPg = settlement(auctionOf(consignorId), 100_000, now.minusHours(1));
		paymentRepository.save(Payment.request(waitingPg.getId(), waitingPg.getTotalAmount(), PaymentMethod.MOCK_PG, now.minusHours(2)));
		Settlement paid = settlement(auctionOf(consignorId), 200_000, now.plusDays(1));
		paid.markPaid();
		settlementRepository.save(paid);

		scheduler.runOnce(now);

		assertThat(settlementRepository.findById(overdue.getId()).orElseThrow().getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
		assertThat(settlementRepository.findById(overdue.getId()).orElseThrow().getExpiredAt()).isEqualTo(now);
		assertThat(settlementRepository.findById(notYet.getId()).orElseThrow().getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
		assertThat(settlementRepository.findById(waitingPg.getId()).orElseThrow().getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);

		List<SellerPayout> payouts = sellerPayoutRepository.findByConsignorIdOrderByIdDesc(consignorId);
		assertThat(payouts).hasSize(1);
		assertThat(payouts.get(0).getSettlementId()).isEqualTo(paid.getId());
		assertThat(payouts.get(0).getPayoutAmount()).isEqualTo(180_000L); // 기본 수수료율 10%
		assertThat(payouts.get(0).getStatus()).isEqualTo(SellerPayoutStatus.READY);

		// 다시 돌려도 중복으로 만들지 않는다
		scheduler.runOnce(now.plusMinutes(10));
		assertThat(sellerPayoutRepository.findByConsignorIdOrderByIdDesc(consignorId)).hasSize(1);
	}
}
