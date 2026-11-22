package com.artbid.payment.service;

import com.artbid.payment.domain.Payment;
import com.artbid.payment.domain.PaymentMethod;
import com.artbid.payment.domain.PaymentStatus;
import com.artbid.payment.gateway.PaymentGateway;
import com.artbid.payment.gateway.TossPaymentClient;
import com.artbid.payment.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 결과 불명 결제 복구 스케줄러 테스트.
 * 핵심은 "PG가 확실히 답한 경우에만 확정하고, 모르면 건드리지 않는다"이다.
 */
@ExtendWith(MockitoExtension.class)
class PaymentRecoverySchedulerTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 21, 13, 0);

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private PaymentTransactionService transactions;
    @Mock
    private TossPaymentClient tossPaymentClient;

    @InjectMocks
    private PaymentRecoveryScheduler scheduler;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(scheduler, "staleAfterMinutes", 15L);
    }

    private Payment requested(long id, PaymentMethod method) {
        Payment payment = Payment.request(9L, 42_100L, method, NOW.minusMinutes(30));
        ReflectionTestUtils.setField(payment, "id", id);
        return payment;
    }

    @Test
    void 요청된_지_15분이_지난_결제만_조회한다() {
        when(paymentRepository.findTop50ByStatusAndRequestedAtBeforeOrderByIdAsc(PaymentStatus.REQUESTED, NOW.minusMinutes(15)))
                .thenReturn(List.of());

        scheduler.runOnce(NOW);

        verify(paymentRepository).findTop50ByStatusAndRequestedAtBeforeOrderByIdAsc(PaymentStatus.REQUESTED, NOW.minusMinutes(15));
    }

    @Test
    void 토스가_결제_완료라고_답하면_완료로_확정한다() {
        when(paymentRepository.findTop50ByStatusAndRequestedAtBeforeOrderByIdAsc(any(), any()))
                .thenReturn(List.of(requested(2L, PaymentMethod.TOSS)));
        when(tossPaymentClient.lookup("payment_2", 42_100L))
                .thenReturn(Optional.of(new PaymentGateway.Result(true, "pk_done", null)));
        when(transactions.resolveUnknown(eq(2L), any())).thenReturn(true);

        var result = scheduler.runOnce(NOW);

        ArgumentCaptor<PaymentGateway.Result> captor = ArgumentCaptor.forClass(PaymentGateway.Result.class);
        verify(transactions).resolveUnknown(eq(2L), captor.capture());
        assertThat(captor.getValue().approved()).isTrue();
        assertThat(result.resolved()).isEqualTo(1);
    }

    @Test
    void 토스가_모른다고_하면_건드리지_않는다() {
        when(paymentRepository.findTop50ByStatusAndRequestedAtBeforeOrderByIdAsc(any(), any()))
                .thenReturn(List.of(requested(2L, PaymentMethod.TOSS)));
        when(tossPaymentClient.lookup("payment_2", 42_100L)).thenReturn(Optional.empty());

        var result = scheduler.runOnce(NOW);

        verify(transactions, never()).resolveUnknown(anyLong(), any());
        assertThat(result.unknown()).isEqualTo(1);
    }

    @Test
    void 모의_결제는_조회할_곳이_없으므로_미승인으로_정리한다() {
        when(paymentRepository.findTop50ByStatusAndRequestedAtBeforeOrderByIdAsc(any(), any()))
                .thenReturn(List.of(requested(3L, PaymentMethod.MOCK_PG)));
        when(transactions.resolveUnknown(eq(3L), any())).thenReturn(true);

        scheduler.runOnce(NOW);

        ArgumentCaptor<PaymentGateway.Result> captor = ArgumentCaptor.forClass(PaymentGateway.Result.class);
        verify(transactions).resolveUnknown(eq(3L), captor.capture());
        assertThat(captor.getValue().approved()).isFalse();
        verify(tossPaymentClient, never()).lookup(any(), any());
    }

    @Test
    void 한_건이_실패해도_나머지는_처리한다() {
        when(paymentRepository.findTop50ByStatusAndRequestedAtBeforeOrderByIdAsc(any(), any()))
                .thenReturn(List.of(requested(3L, PaymentMethod.MOCK_PG), requested(4L, PaymentMethod.MOCK_PG)));
        when(transactions.resolveUnknown(eq(3L), any())).thenThrow(new IllegalStateException("DB 오류"));
        when(transactions.resolveUnknown(eq(4L), any())).thenReturn(true);

        var result = scheduler.runOnce(NOW);

        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.resolved()).isEqualTo(1);
    }
}