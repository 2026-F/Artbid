package com.artbid.streaming.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class LivestreamTest {

	private static final LocalDateTime T1 = LocalDateTime.of(2026, 10, 10, 20, 0);
	private static final LocalDateTime T2 = T1.plusHours(1);

	@Test
	void 활성화하면_스테이지와_시작_시각이_기록되고_LIVE가_된다() {
		Livestream livestream = Livestream.builder().auctionId(1L).build();

		livestream.activate("arn:stage-1", T1);

		assertThat(livestream.getStageArn()).isEqualTo("arn:stage-1");
		assertThat(livestream.getStartedAt()).isEqualTo(T1);
		assertThat(livestream.isLive()).isTrue();
	}

	@Test
	void 종료하면_ENDED가_되고_종료_시각이_기록된다() {
		Livestream livestream = Livestream.builder().auctionId(1L).build();
		livestream.activate("arn:stage-1", T1);

		livestream.markEnded(T2);

		assertThat(livestream.getStatus()).isEqualTo(LivestreamStatus.ENDED);
		assertThat(livestream.getEndedAt()).isEqualTo(T2);
		assertThat(livestream.isLive()).isFalse();
	}

	@Test
	void 끝난_방송을_다시_시작하면_새_스테이지로_바뀌고_종료_시각이_비워진다() {
		Livestream livestream = Livestream.builder().auctionId(1L).build();
		livestream.activate("arn:stage-1", T1);
		livestream.markEnded(T2);

		livestream.activate("arn:stage-2", T2.plusMinutes(5));

		assertThat(livestream.getStageArn()).isEqualTo("arn:stage-2");
		assertThat(livestream.getEndedAt()).isNull();
		assertThat(livestream.isLive()).isTrue();
	}
}
