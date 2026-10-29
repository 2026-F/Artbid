package com.artbid.shipping.service;

import com.artbid.artwork.domain.Artwork;
import com.artbid.artwork.domain.ArtworkCategory;
import com.artbid.artwork.repository.ArtworkRepository;
import com.artbid.auction.domain.Auction;
import com.artbid.auction.domain.AuctionStatus;
import com.artbid.auction.repository.AuctionRepository;
import com.artbid.member.domain.MemberRole;
import com.artbid.settlement.domain.Settlement;
import com.artbid.settlement.repository.SettlementRepository;
import com.artbid.shipping.domain.Shipment;
import com.artbid.shipping.domain.ShipmentStatus;
import com.artbid.shipping.domain.ShippingAddress;
import com.artbid.shipping.exception.ShipmentException;
import com.artbid.shipping.repository.ShipmentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 로컬 docker-compose의 postgres·redis가 떠 있어야 통과함 (BidServiceConcurrencyTest와 같은 조건, CI는 서비스 컨테이너 사용)
// 실제 DB에서 배송지 입력 → 결제 → 운송장 등록 흐름과 동시 입력 시 중복 방지를 확인한다.
@SpringBootTest
class ShipmentFlowPostgresTest {

	@Autowired
	ShipmentService shipmentService;
	@Autowired
	ArtworkRepository artworkRepository;
	@Autowired
	AuctionRepository auctionRepository;
	@Autowired
	SettlementRepository settlementRepository;
	@Autowired
	ShipmentRepository shipmentRepository;
	@Autowired
	TransactionTemplate transactionTemplate;

	// DB에 이전 실행 데이터가 남아 있어도 겹치지 않도록 매번 다른 회원 ID를 쓴다
	final long winnerId = ThreadLocalRandom.current().nextLong(1_000_000_000L, Long.MAX_VALUE / 4);
	final long consignorId = winnerId + 1;

	Settlement settlement() {
		LocalDateTime now = LocalDateTime.now();
		Artwork artwork = artworkRepository.save(Artwork.builder()
				.consignorId(consignorId).artistId(1L).title("배송 테스트").category(ArtworkCategory.PAINTING).startPrice(10_000L)
				.build());
		Auction auction = auctionRepository.save(Auction.builder()
				.artworkId(artwork.getId()).startPrice(10_000L).currentPrice(100_000L).minBidUnit(1_000L)
				.previewStart(now.minusDays(5)).previewEnd(now.minusDays(4)).auctionEndAt(now.minusDays(3))
				.status(AuctionStatus.CLOSED).build());
		return settlementRepository.save(Settlement.create(auction.getId(), winnerId, 100_000, 10_000, 30_000, now.plusDays(3)));
	}

	ShippingAddress address(String detail) {
		return new ShippingAddress("김수령", "010-1234-5678", "04524", "서울 중구 세종대로 110", detail, null);
	}

	@Test
	void 배송지_입력부터_운송장_등록까지() {
		Long settlementId = settlement().getId();

		shipmentService.saveAddress(settlementId, winnerId, address("3층"));
		assertThat(shipmentService.get(settlementId, consignorId, MemberRole.CONSIGNOR).getAddressDetail()).isEqualTo("3층");

		// 결제 전에는 발송할 수 없다
		assertThatThrownBy(() -> shipmentService.registerTracking(settlementId, consignorId, MemberRole.CONSIGNOR, "CJ대한통운", "123456789012"))
				.isInstanceOf(ShipmentException.class);

		transactionTemplate.executeWithoutResult(status ->
				settlementRepository.findById(settlementId).orElseThrow().markPaid());
		shipmentService.registerTracking(settlementId, consignorId, MemberRole.CONSIGNOR, "CJ대한통운", "123456789012");

		Shipment saved = shipmentRepository.findBySettlementId(settlementId).orElseThrow();
		assertThat(saved.getStatus()).isEqualTo(ShipmentStatus.SHIPPED);
		assertThat(saved.getTrackingNumber()).isEqualTo("123456789012");

		// 발송 후 배송지 수정은 막힌다
		assertThatThrownBy(() -> shipmentService.saveAddress(settlementId, winnerId, address("5층")))
				.isInstanceOf(ShipmentException.class);
		assertThat(shipmentRepository.findBySettlementId(settlementId).orElseThrow().getAddressDetail()).isEqualTo("3층");
	}

	@Test
	void 같은_정산에_동시에_배송지를_입력해도_하나만_생긴다() throws InterruptedException {
		Long settlementId = settlement().getId();
		int threads = 10;
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		CountDownLatch start = new CountDownLatch(1);
		CountDownLatch done = new CountDownLatch(threads);
		for (int i = 0; i < threads; i++) {
			String detail = i + "층";
			pool.submit(() -> {
				try {
					start.await();
					shipmentService.saveAddress(settlementId, winnerId, address(detail));
				} catch (Exception ignored) {
				} finally {
					done.countDown();
				}
			});
		}
		start.countDown();
		assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
		pool.shutdown();

		long count = shipmentRepository.findAll().stream().filter(s -> s.getSettlementId().equals(settlementId)).count();
		assertThat(count).isEqualTo(1);
	}
}
