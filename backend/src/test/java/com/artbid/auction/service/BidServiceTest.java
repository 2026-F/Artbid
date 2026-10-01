package com.artbid.auction.service;

import com.artbid.auction.domain.Auction;
import com.artbid.auction.domain.AuctionStatus;
import com.artbid.auction.domain.Bid;
import com.artbid.auction.repository.AuctionRepository;
import com.artbid.auction.repository.BidRepository;
import com.artbid.common.exception.BidTemporarilyUnavailableException;
import com.artbid.common.exception.InvalidBidException;
import com.artbid.infra.realtime.AuctionSseRegistry;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.domain.Limit;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
class BidServiceTest {

	@Mock
	private AuctionRepository auctionRepository;
	@Mock
	private BidRepository bidRepository;
	@Mock
	private AuctionSseRegistry sseRegistry;
	@Mock
	private EntityManager entityManager;

	private BidService bidService;

	private static final Long AUCTION_ID = 1L;
	private static final Long BIDDER_ID = 10L;

	@BeforeEach
	void setUp() {
		bidService = new BidService(auctionRepository, bidRepository, sseRegistry);
		ReflectionTestUtils.setField(bidService, "entityManager", entityManager);

		// lockAuctionOrThrow()가 cancelBid/submitBid 진입 시 항상 먼저 실행하는
		// "SET LOCAL lock_timeout" 네이티브 쿼리 체인을 목으로 대체한다.
		Query mockQuery = mock(Query.class);
		when(entityManager.createNativeQuery(anyString())).thenReturn(mockQuery);
	}

	private Auction auctionWithCurrentPrice(Long currentPrice) {
		return Auction.builder()
				.id(AUCTION_ID)
				.artworkId(100L)
				.startPrice(10_000L)
				.currentPrice(currentPrice)
				.minBidUnit(1_000L)
				.previewStart(LocalDateTime.now().minusDays(1))
				.previewEnd(LocalDateTime.now().minusHours(1))
				.auctionEndAt(LocalDateTime.now().plusMinutes(10))
				.status(AuctionStatus.ONGOING)
				.build();
	}

	@Test
	void 취소하면_남은_입찰_중_최고가로_현재가가_되돌아간다() {
		Auction auction = auctionWithCurrentPrice(15_000L);
		Bid latestBid = new Bid(AUCTION_ID, BIDDER_ID, 15_000L, LocalDateTime.now());
		Bid remainingTopBid = new Bid(AUCTION_ID, 20L, 12_000L, LocalDateTime.now().minusMinutes(1));

		when(auctionRepository.findByIdForUpdate(AUCTION_ID)).thenReturn(Optional.of(auction));
		when(bidRepository.findMyLatestBid(eq(AUCTION_ID), eq(BIDDER_ID), any(Limit.class)))
				.thenReturn(Optional.of(latestBid));
		when(bidRepository.findTopBid(eq(AUCTION_ID), any(Limit.class)))
				.thenReturn(Optional.of(remainingTopBid));

		BidService.BidResult result = bidService.cancelBid(AUCTION_ID, BIDDER_ID);

		assertThat(result.currentPrice()).isEqualTo(12_000L);
		assertThat(latestBid.isCanceled()).isTrue();
		assertThat(latestBid.getCancelAt()).isNotNull();
	}

	@Test
	void 남은_입찰이_없으면_시작가로_되돌아간다() {
		Auction auction = auctionWithCurrentPrice(15_000L);
		Bid latestBid = new Bid(AUCTION_ID, BIDDER_ID, 15_000L, LocalDateTime.now());

		when(auctionRepository.findByIdForUpdate(AUCTION_ID)).thenReturn(Optional.of(auction));
		when(bidRepository.findMyLatestBid(eq(AUCTION_ID), eq(BIDDER_ID), any(Limit.class)))
				.thenReturn(Optional.of(latestBid));
		when(bidRepository.findTopBid(eq(AUCTION_ID), any(Limit.class)))
				.thenReturn(Optional.empty());

		BidService.BidResult result = bidService.cancelBid(AUCTION_ID, BIDDER_ID);

		assertThat(result.currentPrice()).isEqualTo(auction.getStartPrice());
	}

	@Test
	void 취소할입찰이없으면_IllegalArgumentException을_던진다() {
		Auction auction = auctionWithCurrentPrice(15_000L);
		when(auctionRepository.findByIdForUpdate(AUCTION_ID)).thenReturn(Optional.of(auction));
		when(bidRepository.findMyLatestBid(eq(AUCTION_ID), eq(BIDDER_ID), any(Limit.class)))
				.thenReturn(Optional.empty());

		assertThatThrownBy(() -> bidService.cancelBid(AUCTION_ID, BIDDER_ID))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void 최근_입찰이_현재가와_다르면_이미_덮어써진_것이므로_InvalidBidException을_던진다() {
		Auction auction = auctionWithCurrentPrice(20_000L); // 다른 입찰이 이미 더 높은 가격으로 갱신한 상태
		Bid staleBid = new Bid(AUCTION_ID, BIDDER_ID, 15_000L, LocalDateTime.now().minusMinutes(2));

		when(auctionRepository.findByIdForUpdate(AUCTION_ID)).thenReturn(Optional.of(auction));
		when(bidRepository.findMyLatestBid(eq(AUCTION_ID), eq(BIDDER_ID), any(Limit.class)))
				.thenReturn(Optional.of(staleBid));

		assertThatThrownBy(() -> bidService.cancelBid(AUCTION_ID, BIDDER_ID))
				.isInstanceOf(InvalidBidException.class);
		assertThat(staleBid.isCanceled()).isFalse(); // 취소 처리 전에 예외가 터져야 함
	}

	@Test
	void 경매_락_획득에_실패하면_Exception을_던진다() {
		when(auctionRepository.findByIdForUpdate(AUCTION_ID))
				.thenThrow(new PessimisticLockingFailureException("lock timeout"));

		assertThatThrownBy(() -> bidService.cancelBid(AUCTION_ID, BIDDER_ID))
				.isInstanceOf(BidTemporarilyUnavailableException.class);
	}

	@Test
	void 경매를_찾을수없으면_예외를_던진다() {
		when(auctionRepository.findByIdForUpdate(AUCTION_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> bidService.cancelBid(AUCTION_ID, BIDDER_ID))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void 입찰내역조회시_취소여부시각을_포함해_매핑한다() {
		LocalDateTime now = LocalDateTime.now();
		Bid activeBid = new Bid(AUCTION_ID, BIDDER_ID, 15_000L, now);
		Bid canceledBid = new Bid(AUCTION_ID, 20L, 12_000L, now.minusMinutes(1));
		canceledBid.cancel(now);

		when(bidRepository.findHistory(AUCTION_ID)).thenReturn(List.of(activeBid, canceledBid));

		List<BidService.BidResponse> responses = bidService.getBids(AUCTION_ID);

		assertThat(responses).hasSize(2);
		assertThat(responses.get(0).canceled()).isFalse();
		assertThat(responses.get(0).canceledAt()).isNull();
		assertThat(responses.get(1).canceled()).isTrue();
		assertThat(responses.get(1).canceledAt()).isEqualTo(now);
	}
}
