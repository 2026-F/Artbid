package com.artbid.liveshow.service;

import com.artbid.artwork.domain.Artwork;
import com.artbid.artwork.domain.ArtworkCategory;
import com.artbid.artwork.repository.ArtworkRepository;
import com.artbid.auction.domain.Auction;
import com.artbid.auction.domain.AuctionStatus;
import com.artbid.auction.live.AuctionLiveChangedEvent;
import com.artbid.auction.repository.AuctionRepository;
import com.artbid.infra.streaming.IvsStageClient;
import com.artbid.liveshow.domain.LiveShow;
import com.artbid.liveshow.domain.LiveShowLot;
import com.artbid.liveshow.domain.LiveShowStatus;
import com.artbid.liveshow.domain.LotStatus;
import com.artbid.liveshow.dto.LiveShowDtos.CreateRequest;
import com.artbid.liveshow.dto.LiveShowDtos.DetailResponse;
import com.artbid.liveshow.dto.LiveShowDtos.LotRequest;
import com.artbid.liveshow.exception.LiveShowAccessDeniedException;
import com.artbid.liveshow.exception.LiveShowConflictException;
import com.artbid.liveshow.repository.LiveShowLotRepository;
import com.artbid.liveshow.repository.LiveShowRepository;
import com.artbid.member.domain.MemberRole;
import com.artbid.member.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionSystemException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class LiveShowServiceTest {

	private static final Long HOST_ID = 100L;
	private static final Long OTHER_ID = 200L;
	private static final Long SHOW_ID = 1L;

	private LiveShowRepository showRepository;
	private LiveShowLotRepository lotRepository;
	private AuctionRepository auctionRepository;
	private ArtworkRepository artworkRepository;
	private IvsStageClient ivsStageClient;
	private ApplicationEventPublisher eventPublisher;
	private PlatformTransactionManager transactionManager;
	private LiveShowService service;

	private final Map<Long, Auction> auctions = new java.util.HashMap<>();
	private final Map<Long, Artwork> artworks = new java.util.HashMap<>();

	@BeforeEach
	void setUp() {
		showRepository = mock(LiveShowRepository.class);
		lotRepository = mock(LiveShowLotRepository.class);
		auctionRepository = mock(AuctionRepository.class);
		artworkRepository = mock(ArtworkRepository.class);
		MemberRepository memberRepository = mock(MemberRepository.class);
		ivsStageClient = mock(IvsStageClient.class);
		eventPublisher = mock(ApplicationEventPublisher.class);
		transactionManager = mock(PlatformTransactionManager.class);
		service = new LiveShowService(showRepository, lotRepository, auctionRepository, artworkRepository,
				memberRepository, ivsStageClient, eventPublisher, transactionManager);

		when(auctionRepository.findAllById(anyCollection())).thenAnswer(invocation -> select(auctions, invocation.getArgument(0)));
		when(artworkRepository.findAllById(anyCollection())).thenAnswer(invocation -> select(artworks, invocation.getArgument(0)));
		when(auctionRepository.findById(any())).thenAnswer(invocation -> Optional.ofNullable(auctions.get(invocation.<Long>getArgument(0))));
		when(auctionRepository.findByIdForUpdate(any())).thenAnswer(invocation -> Optional.ofNullable(auctions.get(invocation.<Long>getArgument(0))));
		when(artworkRepository.findById(any())).thenAnswer(invocation -> Optional.ofNullable(artworks.get(invocation.<Long>getArgument(0))));
		when(memberRepository.findAllById(anyCollection())).thenReturn(List.of());
		when(showRepository.save(any(LiveShow.class))).thenAnswer(invocation -> {
			LiveShow show = invocation.getArgument(0);
			ReflectionTestUtils.setField(show, "id", SHOW_ID);
			return show;
		});
		when(lotRepository.saveAllAndFlush(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
	}

	private static <T> List<T> select(Map<Long, T> source, Collection<Long> ids) {
		List<T> result = new ArrayList<>();
		ids.forEach(id -> {
			if (source.containsKey(id)) {
				result.add(source.get(id));
			}
		});
		return result;
	}

	/** 위탁자 consignorId의 작품과 시작 전(PREVIEW) 경매를 만든다. */
	private Auction auction(Long auctionId, Long consignorId) {
		Long artworkId = auctionId * 10;
		Artwork artwork = Artwork.builder().consignorId(consignorId).artistId(2L).title("작품" + auctionId)
				.category(ArtworkCategory.PAINTING).startPrice(100_000L).build();
		ReflectionTestUtils.setField(artwork, "id", artworkId);
		artworks.put(artworkId, artwork);

		LocalDateTime now = LocalDateTime.now();
		Auction auction = Auction.create(artworkId, 100_000L, 10_000L, now.minusHours(1), now.plusDays(1),
				now.plusDays(2), now);
		ReflectionTestUtils.setField(auction, "id", auctionId);
		auctions.put(auctionId, auction);
		return auction;
	}

	private CreateRequest request(Long... auctionIds) {
		List<LotRequest> lots = java.util.Arrays.stream(auctionIds).map(id -> new LotRequest(id, 5)).toList();
		return new CreateRequest("올해의작가특별전", null, null, lots);
	}

	private LiveShow liveShow(LiveShowStatus status) {
		LiveShow show = LiveShow.create(HOST_ID, "올해의작가특별전", null, null, LocalDateTime.now());
		ReflectionTestUtils.setField(show, "id", SHOW_ID);
		if (status != LiveShowStatus.SCHEDULED) {
			show.start("arn:stage", LocalDateTime.now());
		}
		when(showRepository.findById(SHOW_ID)).thenReturn(Optional.of(show));
		when(showRepository.findByIdForUpdate(SHOW_ID)).thenReturn(Optional.of(show));
		return show;
	}

	private List<LiveShowLot> lots(Long... auctionIds) {
		List<LiveShowLot> lots = new ArrayList<>();
		for (int i = 0; i < auctionIds.length; i++) {
			LiveShowLot lot = LiveShowLot.of(SHOW_ID, auctionIds[i], i + 1, 5);
			ReflectionTestUtils.setField(lot, "id", (long) (i + 1));
			lots.add(lot);
		}
		when(lotRepository.findByShowIdOrderByLotNo(SHOW_ID)).thenReturn(lots);
		return lots;
	}

	// ---- 편성 ----

	@Test
	void 위탁자가_자기_작품의_시작_전_경매로_라이브를_편성한다() {
		auction(1L, HOST_ID);
		auction(2L, HOST_ID);

		DetailResponse response = service.create(HOST_ID, MemberRole.CONSIGNOR, request(1L, 2L));

		assertThat(response.status()).isEqualTo(LiveShowStatus.SCHEDULED);
		assertThat(response.lots()).extracting(lot -> lot.auctionId()).containsExactly(1L, 2L);
		assertThat(response.lots()).extracting(lot -> lot.status()).containsOnly(LotStatus.WAITING);
	}

	@Test
	void 위탁자가_아니면_편성할_수_없다() {
		auction(1L, HOST_ID);

		assertThatThrownBy(() -> service.create(HOST_ID, MemberRole.BIDDER, request(1L)))
				.isInstanceOf(LiveShowAccessDeniedException.class);
		assertThatThrownBy(() -> service.create(HOST_ID, null, request(1L)))
				.isInstanceOf(LiveShowAccessDeniedException.class);
		verify(showRepository, never()).save(any());
	}

	@Test
	void 남의_작품_경매는_편성할_수_없다() {
		auction(1L, HOST_ID);
		auction(2L, OTHER_ID);

		assertThatThrownBy(() -> service.create(HOST_ID, MemberRole.CONSIGNOR, request(1L, 2L)))
				.isInstanceOf(LiveShowAccessDeniedException.class);
		verify(showRepository, never()).save(any());
	}

	@Test
	void 이미_시작한_경매는_편성할_수_없다() {
		auction(1L, HOST_ID).overrideStatus(AuctionStatus.ONGOING);

		assertThatThrownBy(() -> service.create(HOST_ID, MemberRole.CONSIGNOR, request(1L)))
				.isInstanceOf(LiveShowConflictException.class);
	}

	@Test
	void 다른_라이브에_들어_있는_경매는_편성할_수_없다() {
		auction(1L, HOST_ID);
		when(lotRepository.existsByAuctionIdIn(anyCollection())).thenReturn(true);

		assertThatThrownBy(() -> service.create(HOST_ID, MemberRole.CONSIGNOR, request(1L)))
				.isInstanceOf(LiveShowConflictException.class);
		verify(showRepository, never()).save(any());
	}

	@Test
	void 잘못된_편성_입력은_거절한다() {
		auction(1L, HOST_ID);

		assertThatThrownBy(() -> service.create(HOST_ID, MemberRole.CONSIGNOR, request()))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> service.create(HOST_ID, MemberRole.CONSIGNOR, request(1L, 1L)))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> service.create(HOST_ID, MemberRole.CONSIGNOR,
				new CreateRequest("제목", null, null, List.of(new LotRequest(1L, 61)))))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> service.create(HOST_ID, MemberRole.CONSIGNOR,
				new CreateRequest(" ", null, null, List.of(new LotRequest(1L, 5)))))
				.isInstanceOf(IllegalArgumentException.class);
	}

	// ---- 시작, 다음 작품, 종료 ----

	@Test
	void 진행자가_아니면_시작하거나_넘기거나_끝낼_수_없다() {
		liveShow(LiveShowStatus.LIVE);

		assertThatThrownBy(() -> service.start(SHOW_ID, OTHER_ID)).isInstanceOf(LiveShowAccessDeniedException.class);
		assertThatThrownBy(() -> service.openNextLot(SHOW_ID, OTHER_ID)).isInstanceOf(LiveShowAccessDeniedException.class);
		assertThatThrownBy(() -> service.end(SHOW_ID, OTHER_ID)).isInstanceOf(LiveShowAccessDeniedException.class);
		verifyNoInteractions(ivsStageClient);
	}

	@Test
	void 진행자가_시작하면_스테이지를_만들고_작품은_아직_열지_않는다() {
		LiveShow show = liveShow(LiveShowStatus.SCHEDULED);
		auction(1L, HOST_ID);
		lots(1L);
		when(ivsStageClient.createShowStage(SHOW_ID)).thenReturn("arn:stage");

		service.start(SHOW_ID, HOST_ID);

		assertThat(show.getStatus()).isEqualTo(LiveShowStatus.LIVE);
		assertThat(auctions.get(1L).getStatus()).isEqualTo(AuctionStatus.PREVIEW);
	}

	@Test
	void 다음_작품을_열면_그_경매가_지금부터_진행_시간_동안_입찰을_받는다() {
		LiveShow show = liveShow(LiveShowStatus.LIVE);
		auction(1L, HOST_ID);
		auction(2L, HOST_ID);
		List<LiveShowLot> lots = lots(1L, 2L);
		LocalDateTime before = LocalDateTime.now();

		service.openNextLot(SHOW_ID, HOST_ID);

		Auction opened = auctions.get(1L);
		assertThat(opened.getStatus()).isEqualTo(AuctionStatus.ONGOING);
		assertThat(opened.getAuctionEndAt()).isAfterOrEqualTo(before.plusMinutes(5));
		assertThat(auctions.get(2L).getStatus()).isEqualTo(AuctionStatus.PREVIEW);
		assertThat(lots.get(0).getStatus()).isEqualTo(LotStatus.OPEN);
		assertThat(show.getCurrentLotId()).isEqualTo(lots.get(0).getId());
		verify(eventPublisher).publishEvent(any(AuctionLiveChangedEvent.class));
	}

	@Test
	void 현재_작품이_마감되기_전에는_다음_작품을_열_수_없다() {
		liveShow(LiveShowStatus.LIVE);
		auction(1L, HOST_ID);
		auction(2L, HOST_ID);
		lots(1L, 2L);
		service.openNextLot(SHOW_ID, HOST_ID);

		assertThatThrownBy(() -> service.openNextLot(SHOW_ID, HOST_ID)).isInstanceOf(LiveShowConflictException.class);
		assertThat(auctions.get(2L).getStatus()).isEqualTo(AuctionStatus.PREVIEW);
	}

	@Test
	void 현재_작품이_마감되면_다음_작품으로_넘어간다() {
		LiveShow show = liveShow(LiveShowStatus.LIVE);
		auction(1L, HOST_ID);
		auction(2L, HOST_ID);
		List<LiveShowLot> lots = lots(1L, 2L);
		service.openNextLot(SHOW_ID, HOST_ID);
		auctions.get(1L).overrideStatus(AuctionStatus.CLOSED);

		service.openNextLot(SHOW_ID, HOST_ID);

		assertThat(lots.get(0).getStatus()).isEqualTo(LotStatus.DONE);
		assertThat(lots.get(1).getStatus()).isEqualTo(LotStatus.OPEN);
		assertThat(auctions.get(2L).getStatus()).isEqualTo(AuctionStatus.ONGOING);
		assertThat(show.getCurrentLotId()).isEqualTo(lots.get(1).getId());
	}

	@Test
	void 남은_작품이_없거나_방송_중이_아니면_열_수_없다() {
		liveShow(LiveShowStatus.LIVE);
		auction(1L, HOST_ID);
		lots(1L);
		service.openNextLot(SHOW_ID, HOST_ID);
		auctions.get(1L).overrideStatus(AuctionStatus.CLOSED);
		assertThatThrownBy(() -> service.openNextLot(SHOW_ID, HOST_ID)).isInstanceOf(LiveShowConflictException.class);

		liveShow(LiveShowStatus.SCHEDULED);
		assertThatThrownBy(() -> service.openNextLot(SHOW_ID, HOST_ID)).isInstanceOf(LiveShowConflictException.class);
	}

	@Test
	void 종료하면_스테이지를_지우고_열지_않은_작품은_편성에서_뺀다() {
		LiveShow show = liveShow(LiveShowStatus.LIVE);
		auction(1L, HOST_ID);
		auction(2L, HOST_ID);
		List<LiveShowLot> lots = lots(1L, 2L);
		service.openNextLot(SHOW_ID, HOST_ID);

		DetailResponse response = service.end(SHOW_ID, HOST_ID);

		assertThat(show.getStatus()).isEqualTo(LiveShowStatus.ENDED);
		verify(ivsStageClient).deleteStageIfExists("arn:stage");
		verify(lotRepository).deleteAll(List.of(lots.get(1)));
		assertThat(response.lots()).extracting(lot -> lot.auctionId()).containsExactly(1L);
		// 입찰 중이던 작품은 원래 마감 시각까지 계속 받는다.
		assertThat(auctions.get(1L).getStatus()).isEqualTo(AuctionStatus.ONGOING);
		assertThat(auctions.get(2L).getStatus()).isEqualTo(AuctionStatus.PREVIEW);
	}

	@Test
	void 끝난_라이브는_다시_시작할_수_없다() {
		LiveShow show = liveShow(LiveShowStatus.LIVE);
		lots();
		service.end(SHOW_ID, HOST_ID);

		assertThatThrownBy(() -> service.start(SHOW_ID, HOST_ID)).isInstanceOf(LiveShowConflictException.class);
		assertThat(show.getStatus()).isEqualTo(LiveShowStatus.ENDED);
	}

	// ---- IVS 호출과 트랜잭션 ----

	@Test
	void 시작은_스테이지를_트랜잭션_밖에서_만든_뒤_짧은_트랜잭션으로_반영한다() {
		liveShow(LiveShowStatus.SCHEDULED);
		lots();
		when(ivsStageClient.createShowStage(SHOW_ID)).thenReturn("arn:stage");

		service.start(SHOW_ID, HOST_ID);

		InOrder order = inOrder(ivsStageClient, transactionManager, showRepository);
		order.verify(ivsStageClient).createShowStage(SHOW_ID);
		order.verify(transactionManager).getTransaction(any());
		order.verify(showRepository).findByIdForUpdate(SHOW_ID);
		order.verify(transactionManager).commit(any());
		verify(ivsStageClient, never()).deleteStageIfExists(any());
	}

	@Test
	void 스테이지를_만드는_사이_다른_요청이_먼저_시작하면_만든_스테이지를_지운다() {
		LiveShow show = liveShow(LiveShowStatus.SCHEDULED);
		lots();
		when(ivsStageClient.createShowStage(SHOW_ID)).thenAnswer(invocation -> {
			show.start("arn:other", LocalDateTime.now());
			return "arn:mine";
		});

		assertThatThrownBy(() -> service.start(SHOW_ID, HOST_ID)).isInstanceOf(LiveShowConflictException.class);

		verify(ivsStageClient).deleteStageIfExists("arn:mine");
		verify(ivsStageClient, never()).deleteStageIfExists("arn:other");
		verify(transactionManager).rollback(any());
	}

	@Test
	void 시작_커밋이_실패하면_만든_스테이지를_지운다() {
		liveShow(LiveShowStatus.SCHEDULED);
		lots();
		when(ivsStageClient.createShowStage(SHOW_ID)).thenReturn("arn:stage");
		doThrow(new TransactionSystemException("commit failed")).when(transactionManager).commit(any());

		assertThatThrownBy(() -> service.start(SHOW_ID, HOST_ID)).isInstanceOf(TransactionSystemException.class);

		verify(ivsStageClient).deleteStageIfExists("arn:stage");
	}

	@Test
	void 시작_전이_아니면_스테이지를_만들지_않는다() {
		liveShow(LiveShowStatus.LIVE);

		assertThatThrownBy(() -> service.start(SHOW_ID, HOST_ID)).isInstanceOf(LiveShowConflictException.class);

		verifyNoInteractions(ivsStageClient, transactionManager);
	}

	@Test
	void 종료는_커밋한_뒤에_스테이지를_지운다() {
		liveShow(LiveShowStatus.LIVE);
		lots();

		service.end(SHOW_ID, HOST_ID);

		InOrder order = inOrder(transactionManager, ivsStageClient);
		order.verify(transactionManager).commit(any());
		order.verify(ivsStageClient).deleteStageIfExists("arn:stage");
	}

	@Test
	void 종료_커밋이_실패하면_스테이지를_지우지_않는다() {
		liveShow(LiveShowStatus.LIVE);
		lots();
		doThrow(new TransactionSystemException("commit failed")).when(transactionManager).commit(any());

		assertThatThrownBy(() -> service.end(SHOW_ID, HOST_ID)).isInstanceOf(TransactionSystemException.class);

		verifyNoInteractions(ivsStageClient);
	}

	@Test
	void 스테이지_삭제가_실패해도_종료_결과를_돌려준다() {
		LiveShow show = liveShow(LiveShowStatus.LIVE);
		lots();
		doThrow(new RuntimeException("aws down")).when(ivsStageClient).deleteStageIfExists("arn:stage");

		DetailResponse response = service.end(SHOW_ID, HOST_ID);

		assertThat(response.status()).isEqualTo(LiveShowStatus.ENDED);
		assertThat(show.getStatus()).isEqualTo(LiveShowStatus.ENDED);
	}
}
