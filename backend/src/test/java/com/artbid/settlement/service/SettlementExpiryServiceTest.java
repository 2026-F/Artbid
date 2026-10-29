package com.artbid.settlement.service;

import com.artbid.payment.domain.PaymentStatus;
import com.artbid.payment.repository.PaymentRepository;
import com.artbid.settlement.domain.Settlement;
import com.artbid.settlement.repository.SettlementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SettlementExpiryServiceTest {

	private static final LocalDateTime DEADLINE = LocalDateTime.of(2026, 10, 29, 12, 0);

	@Mock
	private SettlementRepository settlementRepository;
	@Mock
	private PaymentRepository paymentRepository;

	private SettlementExpiryService service;

	@BeforeEach
	void setUp() {
		service = new SettlementExpiryService(settlementRepository, paymentRepository);
	}

	private Settlement pending() {
		return Settlement.create(1L, 2L, 100_000, 10_000, 30_000, DEADLINE);
	}

	@Test
	void 기한이_지난_미결제_정산을_만료한다() {
		Settlement settlement = pending();
		when(settlementRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(settlement));
		when(paymentRepository.existsBySettlementIdAndStatusIn(1L, List.of(PaymentStatus.REQUESTED))).thenReturn(false);

		assertThat(service.expireOne(1L, DEADLINE.plusMinutes(1))).isTrue();
		assertThat(settlement.getPaymentStatus()).isEqualTo(com.artbid.settlement.domain.PaymentStatus.FAILED);
	}

	@Test
	void 결과_불명_결제가_있으면_만료하지_않는다() {
		Settlement settlement = pending();
		when(settlementRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(settlement));
		when(paymentRepository.existsBySettlementIdAndStatusIn(1L, List.of(PaymentStatus.REQUESTED))).thenReturn(true);

		assertThat(service.expireOne(1L, DEADLINE.plusMinutes(1))).isFalse();
		assertThat(settlement.getPaymentStatus()).isEqualTo(com.artbid.settlement.domain.PaymentStatus.PENDING);
	}

	@Test
	void 그새_결제가_끝난_정산은_만료하지_않는다() {
		Settlement settlement = pending();
		settlement.markPaid();
		when(settlementRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(settlement));
		when(paymentRepository.existsBySettlementIdAndStatusIn(1L, List.of(PaymentStatus.REQUESTED))).thenReturn(false);

		assertThat(service.expireOne(1L, DEADLINE.plusMinutes(1))).isFalse();
		assertThat(settlement.getPaymentStatus()).isEqualTo(com.artbid.settlement.domain.PaymentStatus.PAID);
	}

	@Test
	void 정산이_없으면_false() {
		when(settlementRepository.findByIdForUpdate(1L)).thenReturn(Optional.empty());

		assertThat(service.expireOne(1L, DEADLINE)).isFalse();
	}
}
