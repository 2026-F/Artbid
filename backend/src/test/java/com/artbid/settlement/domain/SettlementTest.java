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

	@Test
	void 결제_기한이_지난_미결제_정산은_FAILED로_만료된다() {
		Settlement settlement = Settlement.create(1L, 2L, 100, 0, 0, deadline);

		assertThat(settlement.expire(deadline)).isTrue();
		assertThat(settlement.getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
		assertThat(settlement.getExpiredAt()).isEqualTo(deadline);
		assertThat(settlement.isPayableAt(deadline.minusSeconds(1))).isFalse();
	}

	@Test
	void 결제_기한_전에는_만료되지_않는다() {
		Settlement settlement = Settlement.create(1L, 2L, 100, 0, 0, deadline);

		assertThat(settlement.expire(deadline.minusSeconds(1))).isFalse();
		assertThat(settlement.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
		assertThat(settlement.getExpiredAt()).isNull();
	}

	@Test
	void 결제_완료된_정산은_만료되지_않는다() {
		Settlement settlement = Settlement.create(1L, 2L, 100, 0, 0, deadline);
		settlement.markPaid();

		assertThat(settlement.expire(deadline.plusDays(1))).isFalse();
		assertThat(settlement.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
	}

	@Test
	void 만료된_정산은_다시_만료되지_않고_결제_완료할_수_없다() {
		Settlement settlement = Settlement.create(1L, 2L, 100, 0, 0, deadline);
		settlement.expire(deadline);

		assertThat(settlement.expire(deadline.plusDays(1))).isFalse();
		assertThat(settlement.getExpiredAt()).isEqualTo(deadline);
		assertThatThrownBy(settlement::markPaid).isInstanceOf(IllegalStateException.class);
	}
}
