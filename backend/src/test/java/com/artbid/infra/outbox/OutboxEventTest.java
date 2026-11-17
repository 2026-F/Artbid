package com.artbid.infra.outbox;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxEventTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 17, 12, 0);

    private OutboxEvent event() {
        return OutboxEvent.create("auction.closed", "1", "{}");
    }

    @Test
    void 실패할수록_다음_시도_시각이_1초_2초_4초로_늘어난다() {
        OutboxEvent event = event();

        event.markRetryOrFailed(10, NOW);
        assertThat(event.getNextRetryAt()).isEqualTo(NOW.plusSeconds(1));

        event.markRetryOrFailed(10, NOW);
        assertThat(event.getNextRetryAt()).isEqualTo(NOW.plusSeconds(2));

        event.markRetryOrFailed(10, NOW);
        assertThat(event.getNextRetryAt()).isEqualTo(NOW.plusSeconds(4));
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
    }

    @Test
    void 간격은_최대_5분을_넘지_않는다() {
        OutboxEvent event = event();

        for (int i = 0; i < 12; i++) {
            event.markRetryOrFailed(100, NOW);
        }

        assertThat(event.getNextRetryAt()).isEqualTo(NOW.plusSeconds(300));
    }

    @Test
    void 최대_횟수만큼_실패하면_FAILED가_되어_더_시도하지_않는다() {
        OutboxEvent event = event();

        for (int i = 0; i < 10; i++) {
            event.markRetryOrFailed(10, NOW);
        }

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(event.getRetryCount()).isEqualTo(10);
    }
}