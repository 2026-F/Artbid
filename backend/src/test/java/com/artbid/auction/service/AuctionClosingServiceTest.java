package com.artbid.auction.service;

import com.artbid.auction.domain.Auction;
import com.artbid.auction.domain.AuctionStatus;
import com.artbid.auction.domain.Bid;
import com.artbid.auction.event.AuctionClosedMessage;
import com.artbid.auction.repository.AuctionRepository;
import com.artbid.auction.repository.BidRepository;
import com.artbid.infra.outbox.OutboxEvent;
import com.artbid.infra.outbox.OutboxEventRepository;
import com.artbid.infra.realtime.AuctionSseRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Limit;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuctionClosingServiceTest {

	@Mock
	private AuctionRepository auctionRepository;
	@Mock
	private BidRepository bidRepository;
	@Mock
	private AuctionSseRegistry sseRegistry;
	@Mock
	private OutboxEventRepository outboxEventRepository;

	// 실제 ObjectMapper를 쓴다. LocalDateTime(closedAt)을 직렬화하려면 날짜·시간 모듈이 필요해서 findAndAddModules()로 등록한다.
	// (스프링이 만드는 ObjectMapper에는 이미 등록돼 있다)
	private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();

	private AuctionClosingService auctionClosingService;

	private static final Long AUCTION_ID = 1L;
	private static final Long ARTWORK_ID = 100L;
	private static final Long BIDDER_ID = 10L;

	@BeforeEach
	void setUp() {
		auctionClosingService = new AuctionClosingService(
				auctionRepository, bidRepository, sseRegistry, outboxEventRepository, objectMapper);
	}

	private Auction auctionWithCurrentPrice(Long currentPrice, LocalDateTime auctionEndAt) {
		return Auction.builder()
				.id(AUCTION_ID)
				.artworkId(ARTWORK_ID)
				.startPrice(10_000L)
				.currentPrice(currentPrice)
				.minBidUnit(1_000L)
				.previewStart(LocalDateTime.now().minusDays(1))
				.previewEnd(LocalDateTime.now().minusHours(1))
				.auctionEndAt(auctionEndAt)
				.status(AuctionStatus.ONGOING)
				.build();
	}

	@Test
	void 낙찰자가_있으면_정산에_필요한_값을_담아_마감_이벤트를_outbox에_기록한다() throws Exception {
		LocalDateTime now = LocalDateTime.now();
		Auction auction = auctionWithCurrentPrice(15_000L, now.minusMinutes(1));
		Bid winningBid = new Bid(AUCTION_ID, BIDDER_ID, 15_000L, now.minusMinutes(2));

		when(auctionRepository.findByIdForUpdate(AUCTION_ID)).thenReturn(Optional.of(auction));
		when(bidRepository.findTopBid(eq(AUCTION_ID), any(Limit.class))).thenReturn(Optional.of(winningBid));

		auctionClosingService.closeOne(AUCTION_ID, now);

		assertThat(auction.getStatus()).isEqualTo(AuctionStatus.CLOSED);

		ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
		verify(outboxEventRepository).save(captor.capture());
		OutboxEvent event = captor.getValue();
		assertThat(event.getTopic()).isEqualTo(AuctionClosedMessage.TOPIC);
		assertThat(event.getMessageKey()).isEqualTo(String.valueOf(AUCTION_ID));

		// 소비자가 읽을 그대로 다시 읽어서 값이 맞는지 확인한다
		AuctionClosedMessage message = objectMapper.readValue(event.getPayload(), AuctionClosedMessage.class);
		assertThat(message.auctionId()).isEqualTo(AUCTION_ID);
		assertThat(message.bidderId()).isEqualTo(BIDDER_ID);
		assertThat(message.artworkId()).isEqualTo(ARTWORK_ID);
		assertThat(message.finalPrice()).isEqualTo(15_000L);
		assertThat(message.closedAt()).isEqualTo(now); // 결제 기한의 기준 시각

		verify(sseRegistry).broadcastAfterCommit(eq(AUCTION_ID), any(AuctionClosingService.AuctionClosedEvent.class));
	}

	@Test
	void 입찰이_없으면_경매만_마감되고_이벤트는_남기지_않는다() {
		LocalDateTime now = LocalDateTime.now();
		Auction auction = auctionWithCurrentPrice(10_000L, now.minusMinutes(1));

		when(auctionRepository.findByIdForUpdate(AUCTION_ID)).thenReturn(Optional.of(auction));
		when(bidRepository.findTopBid(eq(AUCTION_ID), any(Limit.class))).thenReturn(Optional.empty());

		auctionClosingService.closeOne(AUCTION_ID, now);

		assertThat(auction.getStatus()).isEqualTo(AuctionStatus.CLOSED);
		verify(outboxEventRepository, never()).save(any());
		verify(sseRegistry).broadcastAfterCommit(eq(AUCTION_ID), any(AuctionClosingService.AuctionClosedEvent.class));
	}

	@Test
	void 이미_마감된_경매는_그대로_리턴하고_아무_처리도_하지_않는다() {
		LocalDateTime now = LocalDateTime.now();
		Auction auction = auctionWithCurrentPrice(10_000L, now.minusMinutes(1));
		ReflectionTestUtils.setField(auction, "status", AuctionStatus.CLOSED);

		when(auctionRepository.findByIdForUpdate(AUCTION_ID)).thenReturn(Optional.of(auction));

		auctionClosingService.closeOne(AUCTION_ID, now);

		verify(bidRepository, never()).findTopBid(any(), any());
		verify(outboxEventRepository, never()).save(any());
		verify(sseRegistry, never()).broadcastAfterCommit(any(), any());
	}

	@Test
	void 경매를_찾을수없으면_예외를_던진다() {
		when(auctionRepository.findByIdForUpdate(AUCTION_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> auctionClosingService.closeOne(AUCTION_ID, LocalDateTime.now()))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
