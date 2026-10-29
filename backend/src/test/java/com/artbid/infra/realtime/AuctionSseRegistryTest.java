package com.artbid.infra.realtime;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

class AuctionSseRegistryTest {

	private final AuctionSseRegistry registry = new AuctionSseRegistry();

	@Test
	void 구독자가_있는_경매만_활성_목록에_나온다() {
		assertThat(registry.activeAuctionIds()).isEmpty();

		registry.subscribe(1L);
		registry.subscribe(2L);

		assertThat(registry.activeAuctionIds()).containsExactlyInAnyOrder(1L, 2L);
	}

	@Test
	void 구독자가_없는_경매에_보내도_예외가_나지_않는다() {
		assertThatCode(() -> registry.broadcastEvent(99L, "auction-live", "payload"))
				.doesNotThrowAnyException();
	}

	@Test
	void 이름_있는_이벤트를_보낼_수_있다() {
		SseEmitter emitter = registry.subscribe(1L);

		assertThat(registry.sendEvent(emitter, "auction-live", "payload")).isTrue();
	}

	@Test
	void 전송에_실패하면_false를_돌려준다() throws IOException {
		SseEmitter broken = mock(SseEmitter.class);
		doThrow(new IOException("연결 끊김")).when(broken).send(any(SseEmitter.SseEventBuilder.class));

		assertThat(registry.sendEvent(broken, "auction-live", "payload")).isFalse();
	}
}
