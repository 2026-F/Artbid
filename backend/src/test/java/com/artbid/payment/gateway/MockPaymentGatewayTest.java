package com.artbid.payment.gateway;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class MockPaymentGatewayTest {
	@Test
	void 같은_Payment의_모의_거래_ID는_동일하다() {
		MockPaymentGateway gateway = new MockPaymentGateway(MockPaymentGateway.Outcome.SUCCESS);
		PaymentGateway.Result result = gateway.pay(1L, 965_000L);
		assertThat(result.approved()).isTrue();
		assertThat(result.transactionId()).isEqualTo("txn_mock_1");
		assertThat(gateway.pay(1L, 965_000L)).isEqualTo(result);
	}

	@Test
	void 명확한_거절은_실패_결과로_반환한다() {
		PaymentGateway.Result result = new MockPaymentGateway(MockPaymentGateway.Outcome.DECLINED).pay(1L, 100L);
		assertThat(result.approved()).isFalse();
		assertThat(result.failureReason()).isNotBlank();
	}

	@Test
	void Timeout은_실패_결과와_구분한다() {
		assertThatThrownBy(() -> new MockPaymentGateway(MockPaymentGateway.Outcome.TIMEOUT).pay(1L, 100L))
				.isInstanceOf(PaymentGatewayUnavailableException.class);
	}
}
