package com.artbid.settlement.domain;

import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.assertj.core.api.Assertions.*;

class SettlementTest {
	private final LocalDateTime deadline = LocalDateTime.of(2026, 9, 28, 12, 0);

	@Test
	void 청구금액은_낙찰가_수수료_배송비의_합계다() {
		Settlement settlement = Settlement.create(1L, 2L, 850_000, 85_000, 30_000, deadline);
		assertThat(settlement.getTotalAmount()).isEqualTo(965_000L);
		assertThat(settlement.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
		assertThat(settlement.isPayableAt(deadline.minusSeconds(1))).isTrue();
		assertThat(settlement.isPayableAt(deadline)).isFalse();
	}

	@Test
	void 결제완료_후에는_결제할_수_없다() {
		Settlement settlement = Settlement.create(1L, 2L, 100, 0, 0, deadline);
		settlement.markPaid();
		settlement.markPaid();
		assertThat(settlement.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
		assertThat(settlement.isPayableAt(deadline.minusSeconds(1))).isFalse();
	}

	@Test
	void 금액_오류와_overflow를_거절한다() {
		assertThatThrownBy(() -> Settlement.create(1L, 2L, 0, 0, 0, deadline))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> Settlement.create(1L, 2L, 100, -1, 0, deadline))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> Settlement.create(1L, 2L, 100, 0, -1, deadline))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> Settlement.create(1L, 2L, Long.MAX_VALUE, 1, 0, deadline))
				.isInstanceOf(ArithmeticException.class);
	}
}
