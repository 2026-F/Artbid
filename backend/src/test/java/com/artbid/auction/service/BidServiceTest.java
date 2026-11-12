package com.artbid.auction.service;

import com.artbid.auction.domain.Auction;
import com.artbid.auction.domain.AuctionStatus;
import com.artbid.auction.domain.Bid;
import com.artbid.auction.live.AuctionLiveChangedEvent;
import com.artbid.auction.live.AuctionLiveReason;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.domain.Limit;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import static org.mockito.Mockito.lenient;

	@ExtendWith(MockitoExtension.class)
	class BidServiceTest{

	@Mock
	private AuctionRepository auctionRepository;
	@Mock
	private BidRepository bidRepository;
	@Mock
	private AuctionSseRegistry sseRegistry;
	@Mock
	private EntityManager entityManager;
	@Mock
	private ApplicationEventPublisher eventPublisher;
	@Mock
	private AuctionPriceRedisService priceRedisService;
	@Mock
	private PlatformTransactionManager transactionManager;

	private BidService bidService;

	private static final Long AUCTION_ID = 1L;
	private static final Long BIDDER_ID = 10L;

	@BeforeEach
	void setUp() {
		bidService = new BidService(auctionRepository, bidRepository, sseRegistry, eventPublisher, priceRedisService, transactionManager);
		ReflectionTestUtils.setField(bidService, "entityManager", entityManager);

		// lockAuctionOrThrow()가 cancelBid/submitBid 진입 시 항상 먼저 실행하는
		// "SET LOCAL lock_timeout" 네이티브 쿼리 체인을 목으로 대체한다.
		// getBids()는 이 경로를 안 타므로 lenient()로 strict-stub 검사 예외 처리.
		Query mockQuery = mock(Query.class);
		lenient().when(entityManager.createNativeQuery(anyString())).thenReturn(mockQuery);
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

	@Test
	void 입찰을_취소하면_되돌린_현재가로_이벤트를_발행한다() {
		Auction auction = auctionWithCurrentPrice(15_000L);
		Bid latestBid = new Bid(AUCTION_ID, BIDDER_ID, 15_000L, LocalDateTime.now());
		when(auctionRepository.findByIdForUpdate(AUCTION_ID)).thenReturn(Optional.of(auction));
		when(bidRepository.findMyLatestBid(eq(AUCTION_ID), eq(BIDDER_ID), any(Limit.class)))
				.thenReturn(Optional.of(latestBid));
		when(bidRepository.findTopBid(eq(AUCTION_ID), any(Limit.class)))
				.thenReturn(Optional.of(new Bid(AUCTION_ID, 20L, 12_000L, LocalDateTime.now().minusMinutes(1))));

		bidService.cancelBid(AUCTION_ID, BIDDER_ID);

		ArgumentCaptor<AuctionLiveChangedEvent> captor = ArgumentCaptor.forClass(AuctionLiveChangedEvent.class);
		verify(eventPublisher).publishEvent(captor.capture());
		assertThat(captor.getValue().reason()).isEqualTo(AuctionLiveReason.BID_CANCELED);
		assertThat(captor.getValue().state().currentPrice()).isEqualTo(12_000L);
	}

	// cancelBid가 성공하는 공통 상황: 내 최근 입찰(15,000)이 현재가이고, 취소하면 12,000으로 되돌아간다.
	private void stubCancelableAuction() {
		Auction auction = auctionWithCurrentPrice(15_000L);
		Bid latestBid = new Bid(AUCTION_ID, BIDDER_ID, 15_000L, LocalDateTime.now());
		Bid remainingTopBid = new Bid(AUCTION_ID, 20L, 12_000L, LocalDateTime.now().minusMinutes(1));

		when(auctionRepository.findByIdForUpdate(AUCTION_ID)).thenReturn(Optional.of(auction));
		when(bidRepository.findMyLatestBid(eq(AUCTION_ID), eq(BIDDER_ID), any(Limit.class)))
				.thenReturn(Optional.of(latestBid));
		when(bidRepository.findTopBid(eq(AUCTION_ID), any(Limit.class)))
				.thenReturn(Optional.of(remainingTopBid));
	}

	@Test
	void 입찰을_취소하면_커밋된_뒤에_Redis_현재가_캐시를_지운다() {
		stubCancelableAuction();

		// 실제 트랜잭션처럼 동기화를 켜서, afterCommit 콜백이 등록되는지 확인한다.
		TransactionSynchronizationManager.initSynchronization();
		try {
			bidService.cancelBid(AUCTION_ID, BIDDER_ID);

			// 아직 커밋 전이므로 캐시를 지우면 안 된다 (지우면 취소 전 가격으로 다시 채워질 수 있음)
			verify(priceRedisService, never()).invalidate(any());

			// 커밋됐다고 가정하고 등록된 afterCommit 콜백을 실행한다.
			TransactionSynchronizationManager.getSynchronizations()
					.forEach(TransactionSynchronization::afterCommit);

			verify(priceRedisService).invalidate(AUCTION_ID);
		} finally {
			TransactionSynchronizationManager.clearSynchronization();
		}
	}

	@Test
	void 트랜잭션_밖에서_취소하면_Redis_현재가_캐시를_바로_지운다() {
		stubCancelableAuction();

		bidService.cancelBid(AUCTION_ID, BIDDER_ID);

		verify(priceRedisService).invalidate(AUCTION_ID);
	}

	@Test
	void Redis_캐시_삭제가_실패해도_취소_결과는_그대로_돌려준다() {
		stubCancelableAuction();
		doThrow(new RuntimeException("Redis 연결 실패 시뮬레이션"))
				.when(priceRedisService).invalidate(AUCTION_ID);

		BidService.BidResult result = bidService.cancelBid(AUCTION_ID, BIDDER_ID);

		assertThat(result.currentPrice()).isEqualTo(12_000L);
	}

	@Test
	void 덮어써진_입찰이라_취소에_실패하면_Redis_현재가_캐시를_건드리지_않는다() {
		Auction auction = auctionWithCurrentPrice(20_000L); // 다른 입찰이 이미 더 높은 가격으로 갱신한 상태
		Bid latestBid = new Bid(AUCTION_ID, BIDDER_ID, 15_000L, LocalDateTime.now());

		when(auctionRepository.findByIdForUpdate(AUCTION_ID)).thenReturn(Optional.of(auction));
		when(bidRepository.findMyLatestBid(eq(AUCTION_ID), eq(BIDDER_ID), any(Limit.class)))
				.thenReturn(Optional.of(latestBid));

		assertThatThrownBy(() -> bidService.cancelBid(AUCTION_ID, BIDDER_ID))
				.isInstanceOf(InvalidBidException.class);

		verify(priceRedisService, never()).invalidate(any());
	}

	// ---------------------------------------------------------------------
	// submitBid: 판정은 Redis(AuctionPriceRedisService.tryBid)가, DB는 이긴 입찰만
	// ---------------------------------------------------------------------

	private static final LocalDateTime END_AT = LocalDateTime.now().plusMinutes(10).withNano(0);

	// Redis 판정 결과를 만든다. 최소 단위는 1,000원으로 고정.
	private AuctionPriceRedisService.BidDecision decision(
			AuctionPriceRedisService.Outcome outcome, long currentPrice, AuctionStatus status,
			LocalDateTime endAt, boolean extended) {
		return new AuctionPriceRedisService.BidDecision(outcome, currentPrice, 1_000L, status, endAt, extended);
	}

	private AuctionPriceRedisService.BidDecision win(long newPrice) {
		return decision(AuctionPriceRedisService.Outcome.WIN, newPrice, AuctionStatus.ONGOING, END_AT, false);
	}

	private void stubRedis(Long price, AuctionPriceRedisService.BidDecision... results) {
		AuctionPriceRedisService.BidDecision first = results[0];
		AuctionPriceRedisService.BidDecision[] rest = java.util.Arrays.copyOfRange(results, 1, results.length);
		when(priceRedisService.tryBid(eq(AUCTION_ID), eq(price), any(LocalDateTime.class))).thenReturn(first, rest);
	}

	@Test
	void 이긴_입찰은_DB를_읽지_않고_조건부_UPDATE와_INSERT만_하고_이벤트를_발행한다() {
		stubRedis(16_000L, win(16_000L));
		when(auctionRepository.updateIfHigherPrice(AUCTION_ID, 16_000L, END_AT, AuctionStatus.ONGOING))
				.thenReturn(1);

		BidService.BidResult result = bidService.submitBid(AUCTION_ID, BIDDER_ID, 16_000L);

		assertThat(result.currentPrice()).isEqualTo(16_000L);
		assertThat(result.auctionEndAt()).isEqualTo(END_AT);
		assertThat(result.extended()).isFalse();

		// 캐시가 있으면 경매 조회(findById)는 한 번도 하지 않는다
		verify(auctionRepository, never()).findById(any());
		verify(priceRedisService, never()).prime(any());

		ArgumentCaptor<Bid> savedBid = ArgumentCaptor.forClass(Bid.class);
		verify(bidRepository).save(savedBid.capture());
		assertThat(savedBid.getValue().getPrice()).isEqualTo(16_000L);
		assertThat(savedBid.getValue().getBidderId()).isEqualTo(BIDDER_ID);

		ArgumentCaptor<AuctionLiveChangedEvent> captor = ArgumentCaptor.forClass(AuctionLiveChangedEvent.class);
		verify(eventPublisher).publishEvent(captor.capture());
		AuctionLiveChangedEvent event = captor.getValue();
		assertThat(event.reason()).isEqualTo(AuctionLiveReason.BID);
		assertThat(event.state().auctionId()).isEqualTo(AUCTION_ID);
		assertThat(event.state().currentPrice()).isEqualTo(16_000L);
		assertThat(event.state().minBidUnit()).isEqualTo(1_000L);
		assertThat(event.state().auctionEndAt()).isEqualTo(END_AT);
		assertThat(event.state().status()).isEqualTo(AuctionStatus.ONGOING);
		assertThat(event.state().version()).isPositive();
		assertThat(event.extended()).isFalse();
	}

	@Test
	void 마감_직전_입찰로_연장되면_Redis가_계산한_마감과_EXTENDED를_그대로_DB에_반영한다() {
		LocalDateTime extendedEnd = END_AT.plusSeconds(30);
		stubRedis(16_000L, decision(AuctionPriceRedisService.Outcome.WIN, 16_000L, AuctionStatus.EXTENDED, extendedEnd, true));
		when(auctionRepository.updateIfHigherPrice(AUCTION_ID, 16_000L, extendedEnd, AuctionStatus.EXTENDED))
				.thenReturn(1);

		BidService.BidResult result = bidService.submitBid(AUCTION_ID, BIDDER_ID, 16_000L);

		assertThat(result.extended()).isTrue();
		assertThat(result.auctionEndAt()).isEqualTo(extendedEnd);

		ArgumentCaptor<AuctionLiveChangedEvent> captor = ArgumentCaptor.forClass(AuctionLiveChangedEvent.class);
		verify(eventPublisher).publishEvent(captor.capture());
		assertThat(captor.getValue().extended()).isTrue();
		assertThat(captor.getValue().state().status()).isEqualTo(AuctionStatus.EXTENDED);
	}

	@Test
	void 가격이_낮은_입찰은_DB를_전혀_거치지_않고_거절된다() {
		stubRedis(15_500L, decision(AuctionPriceRedisService.Outcome.TOO_LOW, 15_000L, AuctionStatus.ONGOING, END_AT, false));

		assertThatThrownBy(() -> bidService.submitBid(AUCTION_ID, BIDDER_ID, 15_500L))
				.isInstanceOf(InvalidBidException.class)
				.hasMessage("입찰가가 너무 낮습니다. 현재가 15000원, 최소 16000원 이상 입력하세요.");

		// 핵심: 지는 입찰은 경매 조회도, 트랜잭션도, 이벤트도 없다 -> DB 커넥션을 빌리지 않는다
		verifyNoInteractions(auctionRepository, bidRepository, transactionManager, eventPublisher, sseRegistry);
	}

	@Test
	void 진행_중이_아닌_경매의_입찰은_DB를_거치지_않고_거절된다() {
		stubRedis(16_000L, decision(AuctionPriceRedisService.Outcome.NOT_BIDDABLE, 15_000L, AuctionStatus.CLOSED, END_AT, false));

		assertThatThrownBy(() -> bidService.submitBid(AUCTION_ID, BIDDER_ID, 16_000L))
				.isInstanceOf(InvalidBidException.class)
				.hasMessage("진행 중인 경매가 아닙니다. 현재 상태: CLOSED");

		verifyNoInteractions(auctionRepository, bidRepository, transactionManager, eventPublisher, sseRegistry);
	}

	@Test
	void 마감_시각이_지난_경매의_입찰은_DB를_거치지_않고_거절된다() {
		stubRedis(16_000L, decision(AuctionPriceRedisService.Outcome.ENDED, 15_000L, AuctionStatus.ONGOING, END_AT, false));

		assertThatThrownBy(() -> bidService.submitBid(AUCTION_ID, BIDDER_ID, 16_000L))
				.isInstanceOf(InvalidBidException.class)
				.hasMessage("이미 마감된 경매입니다.");

		verifyNoInteractions(auctionRepository, bidRepository, transactionManager, eventPublisher, sseRegistry);
	}

	@Test
	void 캐시가_비어있으면_DB에서_한번_읽어_채운_뒤_다시_판정한다() {
		Auction auction = auctionWithCurrentPrice(15_000L);
		stubRedis(16_000L, AuctionPriceRedisService.BidDecision.cacheMiss(), win(16_000L));
		when(auctionRepository.findById(AUCTION_ID)).thenReturn(Optional.of(auction));
		when(auctionRepository.updateIfHigherPrice(AUCTION_ID, 16_000L, END_AT, AuctionStatus.ONGOING))
				.thenReturn(1);

		BidService.BidResult result = bidService.submitBid(AUCTION_ID, BIDDER_ID, 16_000L);

		assertThat(result.currentPrice()).isEqualTo(16_000L);
		// 읽기 -> 채우기 -> 다시 판정 순서
		InOrder order = inOrder(auctionRepository, priceRedisService);
		order.verify(priceRedisService).tryBid(eq(AUCTION_ID), eq(16_000L), any(LocalDateTime.class));
		order.verify(auctionRepository).findById(AUCTION_ID);
		order.verify(priceRedisService).prime(auction);
		order.verify(priceRedisService).tryBid(eq(AUCTION_ID), eq(16_000L), any(LocalDateTime.class));
		verify(auctionRepository, times(1)).findById(AUCTION_ID);
	}

	@Test
	void 캐시를_채운_뒤에도_비어있으면_반복하지_않고_503을_던진다() {
		Auction auction = auctionWithCurrentPrice(15_000L);
		stubRedis(16_000L, AuctionPriceRedisService.BidDecision.cacheMiss(), AuctionPriceRedisService.BidDecision.cacheMiss());
		when(auctionRepository.findById(AUCTION_ID)).thenReturn(Optional.of(auction));

		assertThatThrownBy(() -> bidService.submitBid(AUCTION_ID, BIDDER_ID, 16_000L))
				.isInstanceOf(BidTemporarilyUnavailableException.class);

		verify(priceRedisService, times(2)).tryBid(eq(AUCTION_ID), eq(16_000L), any(LocalDateTime.class));
		verifyNoInteractions(transactionManager, bidRepository, eventPublisher);
	}

	@Test
	void 캐시가_비었는데_경매가_없으면_캐시를_채우지_않고_예외를_던진다() {
		stubRedis(16_000L, AuctionPriceRedisService.BidDecision.cacheMiss());
		when(auctionRepository.findById(AUCTION_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> bidService.submitBid(AUCTION_ID, BIDDER_ID, 16_000L))
				.isInstanceOf(IllegalArgumentException.class);

		verify(priceRedisService, never()).prime(any());
		verifyNoInteractions(transactionManager);
	}

	@Test
	void Redis에서는_이겼지만_DB반영이_0건이면_503을_던지고_캐시는_지우지_않는다() {
		// 더 높은 입찰이 먼저 커밋됐거나(커밋 순서 역전), 관리자 상태 변경·마감이 끼어든 경우.
		// 전자는 Redis가 이미 맞는 값이고, 후자는 AuctionBidCacheInvalidator가 커밋 후 지운다.
		stubRedis(16_000L, win(16_000L));
		when(auctionRepository.updateIfHigherPrice(AUCTION_ID, 16_000L, END_AT, AuctionStatus.ONGOING))
				.thenReturn(0);

		assertThatThrownBy(() -> bidService.submitBid(AUCTION_ID, BIDDER_ID, 16_000L))
				.isInstanceOf(BidTemporarilyUnavailableException.class);

		verify(priceRedisService, never()).invalidate(any());
		verify(bidRepository, never()).save(any());
		verify(eventPublisher, never()).publishEvent(any(Object.class));
	}

	@Test
	void Redis에서_이긴_뒤_DB반영중_예외가나면_캐시를_지우고_원래예외를_그대로던진다() {
		stubRedis(16_000L, win(16_000L));
		when(auctionRepository.updateIfHigherPrice(AUCTION_ID, 16_000L, END_AT, AuctionStatus.ONGOING))
				.thenThrow(new RuntimeException("DB 에러 시뮬레이션"));

		assertThatThrownBy(() -> bidService.submitBid(AUCTION_ID, BIDDER_ID, 16_000L))
				.isInstanceOf(RuntimeException.class)
				.hasMessage("DB 에러 시뮬레이션"); // 원래 예외가 가려지지 않고 그대로 전파돼야 한다

		verify(priceRedisService).invalidate(AUCTION_ID);
		verify(eventPublisher, never()).publishEvent(any(Object.class));
	}

	@Test
	void 캐시_삭제까지_실패해도_원래_DB_예외를_그대로_던진다() {
		stubRedis(16_000L, win(16_000L));
		when(auctionRepository.updateIfHigherPrice(AUCTION_ID, 16_000L, END_AT, AuctionStatus.ONGOING))
				.thenThrow(new RuntimeException("DB 에러 시뮬레이션"));
		doThrow(new RuntimeException("Redis 연결 실패 시뮬레이션")).when(priceRedisService).invalidate(AUCTION_ID);

		assertThatThrownBy(() -> bidService.submitBid(AUCTION_ID, BIDDER_ID, 16_000L))
				.isInstanceOf(RuntimeException.class)
				.hasMessage("DB 에러 시뮬레이션");
	}

	@Test
	void DB_반영_중_행_락을_3초_안에_못_얻으면_Redis_캐시를_지우고_503으로_바꿔_던진다() {
		stubRedis(16_000L, win(16_000L));
		// 다른 트랜잭션이 행을 잡고 있어서 lock_timeout(3초)이 난 상황
		when(auctionRepository.updateIfHigherPrice(AUCTION_ID, 16_000L, END_AT, AuctionStatus.ONGOING))
				.thenThrow(new PessimisticLockingFailureException("lock timeout 시뮬레이션"));

		assertThatThrownBy(() -> bidService.submitBid(AUCTION_ID, BIDDER_ID, 16_000L))
				.isInstanceOf(BidTemporarilyUnavailableException.class);

		// UPDATE 전에 같은 트랜잭션에서 3초 제한을 걸었는지
		InOrder order = inOrder(entityManager, auctionRepository);
		order.verify(entityManager).createNativeQuery("SET LOCAL lock_timeout = '3000ms'");
		order.verify(auctionRepository).updateIfHigherPrice(AUCTION_ID, 16_000L, END_AT, AuctionStatus.ONGOING);
		// Redis는 이겼다고 기록했는데 DB엔 못 들어갔으니 캐시를 지워야 한다
		verify(priceRedisService).invalidate(AUCTION_ID);
		verify(eventPublisher, never()).publishEvent(any(Object.class));
	}
}
