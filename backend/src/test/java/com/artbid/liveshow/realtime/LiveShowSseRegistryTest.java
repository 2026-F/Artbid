package com.artbid.liveshow.realtime;

import com.artbid.liveshow.domain.LiveShowStatus;
import com.artbid.liveshow.dto.LiveShowDtos.ShowStateResponse;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class LiveShowSseRegistryTest {

	@Test
	void 커밋_후_상태_변경을_그_라이브_구독자에게만_보낸다() {
		LiveShowSseRegistry registry = spy(new LiveShowSseRegistry());
		SseEmitter show1 = registry.subscribe(1L);
		SseEmitter show2 = registry.subscribe(2L);
		ShowStateResponse state = new ShowStateResponse(1L, LiveShowStatus.LIVE, 11L, null);

		new LiveShowSseListener(registry).onStateChanged(new LiveShowStateChangedEvent(state));

		verify(registry).sendEvent(show1, LiveShowSseEvents.STATE, state);
		verify(registry, never()).sendEvent(eq(show2), any(), any());
	}

	@Test
	void 보내기에_실패한_구독은_정리하고_구독자가_없으면_라이브_키도_지운다() {
		LiveShowSseRegistry registry = new LiveShowSseRegistry();
		SseEmitter emitter = registry.subscribe(1L);
		emitter.completeWithError(new IOException("closed")); // 이후 send는 실패한다
		assertThat(registry.subscriberCount(1L)).isEqualTo(1);

		registry.broadcastEvent(1L, LiveShowSseEvents.STATE, "x");

		assertThat(registry.subscriberCount(1L)).isZero();
	}
}
