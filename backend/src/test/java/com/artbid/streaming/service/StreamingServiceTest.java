package com.artbid.streaming.service;

import com.artbid.artwork.domain.Artwork;
import com.artbid.artwork.domain.ArtworkCategory;
import com.artbid.artwork.repository.ArtworkRepository;
import com.artbid.auction.domain.Auction;
import com.artbid.auction.repository.AuctionRepository;
import com.artbid.infra.streaming.IvsStageClient;
import com.artbid.streaming.config.StreamReconnectProperties;
import com.artbid.streaming.domain.Livestream;
import com.artbid.streaming.domain.LivestreamStatus;
import com.artbid.streaming.exception.InvalidStreamTransitionException;
import com.artbid.streaming.exception.LivestreamAlreadyLiveException;
import com.artbid.streaming.exception.LivestreamNotFoundException;
import com.artbid.streaming.exception.StreamingAccessDeniedException;
import com.artbid.streaming.repository.LivestreamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class StreamingServiceTest {

	private LivestreamRepository livestreamRepository;
	private AuctionRepository auctionRepository;
	private ArtworkRepository artworkRepository;
	private IvsStageClient ivsStageClient;
	private StreamReconnectProperties reconnectProperties;
	private StreamingService streamingService;

	@BeforeEach
	void setup() {
		livestreamRepository = mock(LivestreamRepository.class);
		auctionRepository = mock(AuctionRepository.class);
		artworkRepository = mock(ArtworkRepository.class);
		ivsStageClient = mock(IvsStageClient.class);
		reconnectProperties = new StreamReconnectProperties();
		streamingService = new StreamingService(
				livestreamRepository, auctionRepository, artworkRepository, ivsStageClient, reconnectProperties);
		when(livestreamRepository.save(any(Livestream.class))).thenAnswer(invocation -> invocation.getArgument(0));
	}

	/** auctionId의 위탁자가 consignorId인 Auction/Artwork 조회 체인을 이어준다. */
	private void givenConsignor(Long auctionId, Long artworkId, Long consignorId) {
		Auction auction = Auction.builder().id(auctionId).artworkId(artworkId).build();
		Artwork artwork = Artwork.builder()
				.consignorId(consignorId).artistId(1L).title("작품")
				.category(ArtworkCategory.PAINTING).startPrice(1000L).build();
		when(auctionRepository.findById(auctionId)).thenReturn(Optional.of(auction));
		when(artworkRepository.findById(artworkId)).thenReturn(Optional.of(artwork));
	}

	private Livestream liveStream(Long auctionId, String stageArn) {
		Livestream livestream = Livestream.builder().auctionId(auctionId).build();
		livestream.activate(stageArn, LocalDateTime.now());
		return livestream;
	}

	@Test
	void 처음_시작하는_경매는_스테이지를_만들고_LIVE로_저장한다() {
		when(livestreamRepository.findByAuctionId(1L)).thenReturn(Optional.empty());
		when(ivsStageClient.createStage(1L)).thenReturn("arn:stage-1");

		Livestream result = streamingService.startStream(1L);

		assertThat(result.getAuctionId()).isEqualTo(1L);
		assertThat(result.getStageArn()).isEqualTo("arn:stage-1");
		assertThat(result.getStatus()).isEqualTo(LivestreamStatus.LIVE);
		assertThat(result.getStartedAt()).isNotNull();
		verify(livestreamRepository).save(result);
	}

	@Test
	void 이미_방송_중이면_스테이지를_새로_만들지_않고_막는다() {
		when(livestreamRepository.findByAuctionId(1L)).thenReturn(Optional.of(liveStream(1L, "arn:stage-1")));

		assertThatThrownBy(() -> streamingService.startStream(1L))
				.isInstanceOf(LivestreamAlreadyLiveException.class);
		verify(ivsStageClient, never()).createStage(anyLong());
	}

	@Test
	void 끝난_방송을_다시_시작하면_같은_행을_새_스테이지로_재사용한다() {
		Livestream ended = liveStream(1L, "arn:stage-1");
		ended.markEnded(LocalDateTime.now());
		when(livestreamRepository.findByAuctionId(1L)).thenReturn(Optional.of(ended));
		when(ivsStageClient.createStage(1L)).thenReturn("arn:stage-2");

		Livestream result = streamingService.startStream(1L);

		assertThat(result).isSameAs(ended);
		assertThat(result.getStageArn()).isEqualTo("arn:stage-2");
		assertThat(result.isLive()).isTrue();
	}

	@Test
	void 종료하면_스테이지를_삭제하고_ENDED로_바꾼다() {
		Livestream live = liveStream(1L, "arn:stage-1");
		when(livestreamRepository.findByAuctionId(1L)).thenReturn(Optional.of(live));

		streamingService.endStream(1L);

		verify(ivsStageClient).deleteStageIfExists("arn:stage-1");
		assertThat(live.getStatus()).isEqualTo(LivestreamStatus.ENDED);
		assertThat(live.getEndedAt()).isNotNull();
	}

	@Test
	void 이미_끝난_방송을_다시_종료해도_AWS를_호출하지_않는다() {
		Livestream ended = liveStream(1L, "arn:stage-1");
		ended.markEnded(LocalDateTime.now());
		when(livestreamRepository.findByAuctionId(1L)).thenReturn(Optional.of(ended));

		streamingService.endStream(1L);

		verifyNoInteractions(ivsStageClient);
	}

	@Test
	void 방송_정보가_없으면_NotFound_예외를_던진다() {
		when(livestreamRepository.findByAuctionId(99L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> streamingService.getStream(99L))
				.isInstanceOf(LivestreamNotFoundException.class);
	}

	@Test
	void 위탁자_본인이_연결_끊김을_보고하면_DISCONNECTED가_된다() {
		Livestream live = liveStream(1L, "arn:stage-1");
		when(livestreamRepository.findByAuctionId(1L)).thenReturn(Optional.of(live));
		givenConsignor(1L, 10L, 5L);

		Livestream result = streamingService.reportDisconnected(1L, 5L);

		assertThat(result.getStatus()).isEqualTo(LivestreamStatus.DISCONNECTED);
		verify(ivsStageClient, never()).deleteStageIfExists(any());
	}

	@Test
	void 위탁자가_아니면_연결_끊김_보고가_막힌다() {
		Livestream live = liveStream(1L, "arn:stage-1");
		when(livestreamRepository.findByAuctionId(1L)).thenReturn(Optional.of(live));
		givenConsignor(1L, 10L, 5L);

		assertThatThrownBy(() -> streamingService.reportDisconnected(1L, 999L))
				.isInstanceOf(StreamingAccessDeniedException.class);
		assertThat(live.getStatus()).isEqualTo(LivestreamStatus.LIVE);
	}

	@Test
	void LIVE가_아닐때_연결_끊김을_보고하면_409에_해당하는_예외() {
		Livestream ended = liveStream(1L, "arn:stage-1");
		ended.markEnded(LocalDateTime.now());
		when(livestreamRepository.findByAuctionId(1L)).thenReturn(Optional.of(ended));
		givenConsignor(1L, 10L, 5L);

		assertThatThrownBy(() -> streamingService.reportDisconnected(1L, 5L))
				.isInstanceOf(InvalidStreamTransitionException.class);
	}

	@Test
	void 위탁자_본인이_재연결을_보고하면_다시_LIVE가_된다() {
		Livestream live = liveStream(1L, "arn:stage-1");
		live.markDisconnected(LocalDateTime.now());
		when(livestreamRepository.findByAuctionId(1L)).thenReturn(Optional.of(live));
		givenConsignor(1L, 10L, 5L);

		Livestream result = streamingService.reconnect(1L, 5L);

		assertThat(result.getStatus()).isEqualTo(LivestreamStatus.LIVE);
		assertThat(result.getDisconnectedAt()).isNull();
	}

	@Test
	void 비로그인이면_연결_끊김_보고도_재연결_보고도_접근이_막힌다() {
		Livestream live = liveStream(1L, "arn:stage-1");
		when(livestreamRepository.findByAuctionId(1L)).thenReturn(Optional.of(live));
		givenConsignor(1L, 10L, 5L);

		assertThatThrownBy(() -> streamingService.reportDisconnected(1L, null))
				.isInstanceOf(StreamingAccessDeniedException.class);
	}

	@Test
	void timeout을_넘겨_끊긴_방송은_스테이지를_삭제하고_ENDED로_정리된다() {
		reconnectProperties.setTimeout(Duration.ofMinutes(2));
		Livestream abandoned = liveStream(1L, "arn:stage-1");
		abandoned.markDisconnected(LocalDateTime.now().minusMinutes(5));
		Livestream freshlyDisconnected = liveStream(2L, "arn:stage-2");
		freshlyDisconnected.markDisconnected(LocalDateTime.now());
		when(livestreamRepository.findAllByStatus(LivestreamStatus.DISCONNECTED))
				.thenReturn(List.of(abandoned, freshlyDisconnected));

		streamingService.endAbandonedDisconnectedStreams();

		assertThat(abandoned.getStatus()).isEqualTo(LivestreamStatus.ENDED);
		verify(ivsStageClient).deleteStageIfExists("arn:stage-1");
		assertThat(freshlyDisconnected.getStatus()).isEqualTo(LivestreamStatus.DISCONNECTED);
		verify(ivsStageClient, never()).deleteStageIfExists("arn:stage-2");
	}
}
