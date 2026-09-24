package com.artbid.payment.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentTest {
	private static final LocalDateTime REQUESTED_AT = LocalDateTime.of(2026, 9, 24, 12, 0);
	private static final LocalDateTime PAID_AT = REQUESTED_AT.plusSeconds(2);

	private Payment request() {
		return Payment.request(1L, 965_000L, PaymentMethod.MOCK_PG, REQUESTED_AT);
	}

	@Test
	void 결제_요청은_정산_금액과_요청_시각을_기록한다() {
		Payment payment = request();
		assertThat(payment.getSettlementId()).isEqualTo(1L);
		assertThat(payment.getAmount()).isEqualTo(965_000L);
		assertThat(payment.getMethod()).isEqualTo(PaymentMethod.MOCK_PG);
		assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REQUESTED);
		assertThat(payment.getRequestedAt()).isEqualTo(REQUESTED_AT);
		assertThat(payment.getTransactionId()).isNull();
		assertThat(payment.getPaidAt()).isNull();
		assertThat(payment.getFailureReason()).isNull();
	}

	@Test
	void 성공하면_거래_ID와_결제_시각을_기록한다() {
		Payment payment = request();
		payment.markPaid("txn_mock_001", PAID_AT);
		assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
		assertThat(payment.getTransactionId()).isEqualTo("txn_mock_001");
		assertThat(payment.getPaidAt()).isEqualTo(PAID_AT);
		assertThat(payment.getFailureReason()).isNull();
	}

	@Test
	void 명확한_거절은_실패_사유를_기록한다() {
		Payment payment = request();
		payment.markFailed("모의 PG 승인 거절");
		assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
		assertThat(payment.getFailureReason()).isEqualTo("모의 PG 승인 거절");
		assertThat(payment.getPaidAt()).isNull();
	}

	@Test
	void 같은_성공_결과를_다시_받아도_최초_결제_시각을_유지한다() {
		Payment payment = request();
		payment.markPaid("txn_mock_001", PAID_AT);
		payment.markPaid("txn_mock_001", PAID_AT.plusMinutes(1));
		assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
		assertThat(payment.getTransactionId()).isEqualTo("txn_mock_001");
		assertThat(payment.getPaidAt()).isEqualTo(PAID_AT);
	}

	@Test
	void 완료된_결제는_늦게_도착한_실패로_바뀌지_않는다() {
		Payment payment = request();
		payment.markPaid("txn_mock_001", PAID_AT);
		assertThatThrownBy(() -> payment.markFailed("뒤늦은 거절"))
				.isInstanceOf(IllegalStateException.class);
		assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
		assertThat(payment.getFailureReason()).isNull();
		assertThat(payment.getPaidAt()).isEqualTo(PAID_AT);
	}

	@Test
	void 완료된_결제에_다른_거래_ID를_덮어쓸_수_없다() {
		Payment payment = request();
		payment.markPaid("txn_mock_001", PAID_AT);
		assertThatThrownBy(() -> payment.markPaid("txn_mock_002", PAID_AT.plusSeconds(1)))
				.isInstanceOf(IllegalStateException.class);
		assertThat(payment.getTransactionId()).isEqualTo("txn_mock_001");
		assertThat(payment.getPaidAt()).isEqualTo(PAID_AT);
	}

	@Test
	void 실패한_시도는_유지하고_재시도는_새_결제로_만든다() {
		Payment failed = request();
		failed.markFailed("승인 거절");
		assertThatThrownBy(() -> failed.markPaid("txn_mock_002", PAID_AT))
				.isInstanceOf(IllegalStateException.class);
		Payment retry = request();
		retry.markPaid("txn_mock_002", PAID_AT);
		assertThat(failed.getStatus()).isEqualTo(PaymentStatus.FAILED);
		assertThat(failed.getTransactionId()).isNull();
		assertThat(retry.getStatus()).isEqualTo(PaymentStatus.PAID);
	}

	@Test
	void 같은_실패는_재수신할_수_있지만_다른_사유로_덮어쓸_수_없다() {
		Payment payment = request();
		payment.markFailed("승인 거절");
		payment.markFailed("승인 거절");
		assertThatThrownBy(() -> payment.markFailed("다른 사유"))
				.isInstanceOf(IllegalStateException.class);
		assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
		assertThat(payment.getFailureReason()).isEqualTo("승인 거절");
	}

	@ParameterizedTest
	@NullSource
	@ValueSource(longs = {0, -1})
	void 유효하지_않은_금액으로_요청할_수_없다(Long amount) {
		assertThatThrownBy(() -> Payment.request(1L, amount, PaymentMethod.MOCK_PG, REQUESTED_AT))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@ParameterizedTest
	@NullSource
	@ValueSource(longs = {0, -1})
	void 유효하지_않은_정산_ID로_요청할_수_없다(Long settlementId) {
		assertThatThrownBy(() -> Payment.request(settlementId, 965_000L, PaymentMethod.MOCK_PG, REQUESTED_AT))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void 수단과_요청_시각은_필수다() {
		assertThatThrownBy(() -> Payment.request(1L, 965_000L, null, REQUESTED_AT))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> Payment.request(1L, 965_000L, PaymentMethod.MOCK_PG, null))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@ParameterizedTest
	@NullSource
	@ValueSource(strings = {"", " "})
	void 잘못된_결과로는_상태가_변경되지_않는다(String value) {
		Payment payment = request();
		assertThatThrownBy(() -> payment.markPaid(value, PAID_AT))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> payment.markFailed(value))
				.isInstanceOf(IllegalArgumentException.class);
		assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REQUESTED);
		assertThat(payment.getTransactionId()).isNull();
		assertThat(payment.getFailureReason()).isNull();
	}

	@Test
	void 저장_길이를_넘는_결과는_상태_변경_전에_거절한다() {
		Payment payment = request();
		assertThatThrownBy(() -> payment.markPaid("x".repeat(101), PAID_AT))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> payment.markFailed("x".repeat(501)))
				.isInstanceOf(IllegalArgumentException.class);
		assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REQUESTED);
	}

	@Test
	void 결제_시각이_없거나_요청보다_빠르면_상태가_변경되지_않는다() {
		Payment payment = request();
		assertThatThrownBy(() -> payment.markPaid("txn_mock_001", null))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> payment.markPaid("txn_mock_001", REQUESTED_AT.minusSeconds(1)))
				.isInstanceOf(IllegalArgumentException.class);
		assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REQUESTED);
		assertThat(payment.getTransactionId()).isNull();
		assertThat(payment.getPaidAt()).isNull();
	}
}
