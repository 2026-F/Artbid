package com.artbid.streaming.service;

import com.artbid.infra.streaming.IvsStageClient;
import com.artbid.streaming.domain.Livestream;
import com.artbid.streaming.domain.LivestreamStatus;
import com.artbid.streaming.exception.LivestreamAlreadyLiveException;
import com.artbid.streaming.exception.LivestreamNotFoundException;
import com.artbid.streaming.repository.LivestreamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class StreamingServiceTest {

	private LivestreamRepository livestreamRepository;
	private IvsStageClient ivsStageClient;
	private StreamingService streamingService;

	@BeforeEach
	void setup() {
		livestreamRepository = mock(LivestreamRepository.class);
		ivsStageClient = mock(IvsStageClient.class);
		streamingService = new StreamingService(livestreamRepository, ivsStageClient);
		when(livestreamRepository.save(any(Livestream.class))).thenAnswer(invocation -> invocation.getArgument(0));
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
}
