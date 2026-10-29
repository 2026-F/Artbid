package com.artbid.streaming.domain;

import com.artbid.streaming.exception.InvalidStreamTransitionException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

	@Test
	void 연결이_끊기면_DISCONNECTED가_되고_끊긴_시각이_기록된다() {
		Livestream livestream = Livestream.builder().auctionId(1L).build();
		livestream.activate("arn:stage-1", T1);

		livestream.markDisconnected(T2);

		assertThat(livestream.getStatus()).isEqualTo(LivestreamStatus.DISCONNECTED);
		assertThat(livestream.getDisconnectedAt()).isEqualTo(T2);
		assertThat(livestream.isLive()).isFalse();
		assertThat(livestream.isActive()).isTrue();
	}

	@Test
	void LIVE가_아닌데_연결_끊김을_기록하려_하면_예외() {
		Livestream livestream = Livestream.builder().auctionId(1L).build();

		assertThatThrownBy(() -> livestream.markDisconnected(T1))
				.isInstanceOf(InvalidStreamTransitionException.class);
	}

	@Test
	void 끊겼던_방송이_재연결되면_다시_LIVE가_되고_시작_시각은_그대로다() {
		Livestream livestream = Livestream.builder().auctionId(1L).build();
		livestream.activate("arn:stage-1", T1);
		livestream.markDisconnected(T2);

		livestream.reconnect();

		assertThat(livestream.getStatus()).isEqualTo(LivestreamStatus.LIVE);
		assertThat(livestream.getDisconnectedAt()).isNull();
		assertThat(livestream.getStartedAt()).isEqualTo(T1);
	}

	@Test
	void 끊긴_적이_없는데_재연결하려_하면_예외() {
		Livestream livestream = Livestream.builder().auctionId(1L).build();
		livestream.activate("arn:stage-1", T1);

		assertThatThrownBy(livestream::reconnect)
				.isInstanceOf(InvalidStreamTransitionException.class);
	}
}
