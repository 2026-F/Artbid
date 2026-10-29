package com.artbid.payment.service;

import com.artbid.notification.domain.NotificationType;
import com.artbid.notification.service.NotificationRequestedEvent;
import com.artbid.payment.domain.Payment;
import com.artbid.payment.domain.PaymentMethod;
import com.artbid.payment.gateway.PaymentGateway;
import com.artbid.payment.repository.PaymentRepository;
import com.artbid.settlement.domain.Settlement;
import com.artbid.settlement.repository.SettlementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 결제 완료 시 낙찰자에게 알림 요청을 보내는지만 확인한다. */
@ExtendWith(MockitoExtension.class)
class PaymentTransactionServiceNotificationTest {

	@Mock
	SettlementRepository settlementRepository;
	@Mock
	PaymentRepository paymentRepository;
	@Mock
	ApplicationEventPublisher eventPublisher;

	PaymentTransactionService service;
	Settlement settlement;
	Payment payment;

	@BeforeEach
	void setUp() {
		service = new PaymentTransactionService(settlementRepository, paymentRepository, eventPublisher);
		settlement = Settlement.create(5L, 2L, 100_000, 10_000, 30_000, LocalDateTime.now().plusDays(3));
		ReflectionTestUtils.setField(settlement, "id", 1L);
		payment = Payment.request(1L, settlement.getTotalAmount(), PaymentMethod.MOCK_PG, LocalDateTime.now());
		ReflectionTestUtils.setField(payment, "id", 10L);
		when(settlementRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(settlement));
		when(paymentRepository.findById(10L)).thenReturn(Optional.of(payment));
	}

	@Test
	void 결제가_승인되면_낙찰자에게_결제_완료_알림을_요청한다() {
		service.complete(1L, 10L, new PaymentGateway.Result(true, "tx-1", null));

		ArgumentCaptor<NotificationRequestedEvent> event = ArgumentCaptor.forClass(NotificationRequestedEvent.class);
		verify(eventPublisher).publishEvent(event.capture());
		assertThat(event.getValue().memberId()).isEqualTo(2L);
		assertThat(event.getValue().type()).isEqualTo(NotificationType.PAYMENT_COMPLETED);
		assertThat(event.getValue().linkUrl()).isEqualTo("/mypage");
	}

	@Test
	void 결제가_거절되면_알림을_보내지_않는다() {
		service.complete(1L, 10L, new PaymentGateway.Result(false, null, "카드 한도 초과"));

		verify(eventPublisher, never()).publishEvent(any(Object.class));
	}
}
