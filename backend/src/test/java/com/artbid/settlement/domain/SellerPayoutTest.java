package com.artbid.settlement.domain;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SellerPayoutTest {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 29, 12, 0);

	private Settlement paidSettlement(long finalPrice) {
		Settlement settlement = Settlement.create(5L, 2L, finalPrice, Math.round(finalPrice * 0.1), 30_000, NOW.plusDays(3));
		ReflectionTestUtils.setField(settlement, "id", 7L);
		settlement.markPaid();
		return settlement;
	}

	@Test
	void 지급액은_낙찰가에서_판매_수수료를_뺀_금액이다() {
		SellerPayout payout = SellerPayout.ready(paidSettlement(850_000), 100L, 3L, new BigDecimal("0.10"), NOW);

		assertThat(payout.getSettlementId()).isEqualTo(7L);
		assertThat(payout.getAuctionId()).isEqualTo(5L);
		assertThat(payout.getConsignorId()).isEqualTo(3L);
		assertThat(payout.getCommissionAmount()).isEqualTo(85_000L);
		assertThat(payout.getPayoutAmount()).isEqualTo(765_000L);
		assertThat(payout.getStatus()).isEqualTo(SellerPayoutStatus.READY);
		assertThat(payout.getCreatedAt()).isEqualTo(NOW);
	}

	@Test
	void 구매_수수료와_배송비는_지급액에_들어가지_않는다() {
		Settlement settlement = paidSettlement(100_000);

		SellerPayout payout = SellerPayout.ready(settlement, 100L, 3L, BigDecimal.ZERO, NOW);

		assertThat(settlement.getTotalAmount()).isEqualTo(140_000L);
		assertThat(payout.getPayoutAmount()).isEqualTo(100_000L);
	}

	@Test
	void 수수료는_원_단위로_반올림한다() {
		SellerPayout payout = SellerPayout.ready(paidSettlement(12_345), 100L, 3L, new BigDecimal("0.15"), NOW);

		assertThat(payout.getCommissionAmount()).isEqualTo(1_852L); // 1851.75 → 1852
		assertThat(payout.getPayoutAmount()).isEqualTo(10_493L);
	}

	@Test
	void 결제_완료되지_않은_정산은_지급_대상이_아니다() {
		Settlement pending = Settlement.create(5L, 2L, 100_000, 0, 0, NOW.plusDays(3));

		assertThatThrownBy(() -> SellerPayout.ready(pending, 100L, 3L, new BigDecimal("0.10"), NOW))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void 수수료율이_범위를_벗어나면_거절한다() {
		assertThatThrownBy(() -> SellerPayout.ready(paidSettlement(100_000), 100L, 3L, new BigDecimal("-0.01"), NOW))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> SellerPayout.ready(paidSettlement(100_000), 100L, 3L, new BigDecimal("1.01"), NOW))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
