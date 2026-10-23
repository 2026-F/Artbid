package com.artbid.payment.service;

import com.artbid.payment.domain.PaymentMethod;
import com.artbid.payment.domain.PaymentStatus;
import com.artbid.payment.dto.PaymentResponse;
import com.artbid.payment.exception.PaymentException;
import com.artbid.payment.gateway.PaymentGateway;
import com.artbid.payment.gateway.PaymentGatewayUnavailableException;
import com.artbid.payment.gateway.TossPaymentClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PaymentServiceTest {

    private PaymentTransactionService transactions;
    private PaymentGateway gateway;
    private TossPaymentClient tossPaymentClient;
    private PaymentService service;

    @BeforeEach
    void setup() {
        transactions = mock(PaymentTransactionService.class);
        gateway = mock(PaymentGateway.class);
        tossPaymentClient = mock(TossPaymentClient.class);
        service = new PaymentService(transactions, gateway, tossPaymentClient);
    }

    private PaymentResponse response(PaymentStatus status, PaymentMethod method) {
        return new PaymentResponse(1L, 2L, 10_000L, method, status, null, null, LocalDateTime.now(), null);
    }

    @Test
    void TOSS는_요청만_만들고_PG를_호출하지_않는다() {
        when(transactions.start(2L, 3L, PaymentMethod.TOSS))
                .thenReturn(response(PaymentStatus.REQUESTED, PaymentMethod.TOSS));

        PaymentResponse result = service.pay(2L, 3L, PaymentMethod.TOSS);

        assertThat(result.status()).isEqualTo(PaymentStatus.REQUESTED);
        verifyNoInteractions(gateway, tossPaymentClient);
    }

    @Test
    void MOCK_PG는_기존처럼_그_자리에서_승인까지_처리한다() {
        when(transactions.start(2L, 3L, PaymentMethod.MOCK_PG))
                .thenReturn(response(PaymentStatus.REQUESTED, PaymentMethod.MOCK_PG));
        PaymentGateway.Result pgResult = new PaymentGateway.Result(true, "txn_1", null);
        when(gateway.pay(1L, 10_000L)).thenReturn(pgResult);
        when(transactions.complete(2L, 1L, pgResult)).thenReturn(response(PaymentStatus.PAID, PaymentMethod.MOCK_PG));

        PaymentResponse result = service.pay(2L, 3L, PaymentMethod.MOCK_PG);

        assertThat(result.status()).isEqualTo(PaymentStatus.PAID);
        verifyNoInteractions(tossPaymentClient);
    }

    @Test
    void confirm은_클라이언트_금액이_아니라_DB에_저장된_금액을_토스에_보낸다() {
        when(transactions.get(1L, 3L)).thenReturn(response(PaymentStatus.REQUESTED, PaymentMethod.TOSS));
        PaymentGateway.Result pgResult = new PaymentGateway.Result(true, "pk_abc", null);
        when(tossPaymentClient.confirm("pk_abc", "payment_1", 10_000L)).thenReturn(pgResult);
        when(transactions.complete(2L, 1L, pgResult)).thenReturn(response(PaymentStatus.PAID, PaymentMethod.TOSS));

        PaymentResponse result = service.confirmToss(1L, 3L, "pk_abc", "payment_1");

        assertThat(result.status()).isEqualTo(PaymentStatus.PAID);
        verify(tossPaymentClient).confirm("pk_abc", "payment_1", 10_000L); // 10_000L은 DB값(response()의 amount), 클라이언트 입력이 아님
    }

    @Test
    void orderId가_결제ID와_안_맞으면_거부한다() {
        when(transactions.get(1L, 3L)).thenReturn(response(PaymentStatus.REQUESTED, PaymentMethod.TOSS));

        assertThatThrownBy(() -> service.confirmToss(1L, 3L, "pk_abc", "payment_999"))
                .isInstanceOf(PaymentException.class).hasMessageContaining("일치하지");
        verifyNoInteractions(tossPaymentClient);
    }

    @Test
    void 이미_처리된_결제는_재승인을_막는다() {
        when(transactions.get(1L, 3L)).thenReturn(response(PaymentStatus.PAID, PaymentMethod.TOSS));

        assertThatThrownBy(() -> service.confirmToss(1L, 3L, "pk_abc", "payment_1"))
                .isInstanceOf(PaymentException.class).hasMessageContaining("이미 처리");
        verifyNoInteractions(tossPaymentClient);
    }

    @Test
    void 토스_응답_불명이면_REQUESTED를_유지하고_재승인_가능하게_둔다() {
        when(transactions.get(1L, 3L)).thenReturn(response(PaymentStatus.REQUESTED, PaymentMethod.TOSS));
        when(tossPaymentClient.confirm("pk_abc", "payment_1", 10_000L))
                .thenThrow(new PaymentGatewayUnavailableException("타임아웃"));

        PaymentResponse result = service.confirmToss(1L, 3L, "pk_abc", "payment_1");

        assertThat(result.status()).isEqualTo(PaymentStatus.REQUESTED);
        verify(transactions, never()).complete(anyLong(), anyLong(), any());
    }
}