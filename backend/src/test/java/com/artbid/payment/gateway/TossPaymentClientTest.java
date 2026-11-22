package com.artbid.payment.gateway;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class TossPaymentClientTest {

    private MockRestServiceServer server;
    private TossPaymentClient client;

    @BeforeEach
    void setup() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new TossPaymentClient(builder, "test_sk_dummy");
    }

    @Test
    void 승인_성공시_approved_true와_거래ID를_반환한다() {
        server.expect(requestTo("https://api.tosspayments.com/v1/payments/confirm"))
                .andExpect(header("Authorization", startsWith("Basic ")))
                .andExpect(jsonPath("$.paymentKey").value("pk_test_abc"))
                .andExpect(jsonPath("$.orderId").value("payment_1"))
                .andExpect(jsonPath("$.amount").value(10000))
                .andRespond(withSuccess("{\"paymentKey\":\"pk_test_abc\",\"status\":\"DONE\"}", MediaType.APPLICATION_JSON));

        PaymentGateway.Result result = client.confirm("pk_test_abc", "payment_1", 10_000L);

        assertThat(result.approved()).isTrue();
        assertThat(result.transactionId()).isEqualTo("pk_test_abc");
        server.verify();
    }

    @Test
    void 토스가_거절하면_approved_false와_사유를_반환한다() {
        server.expect(requestTo("https://api.tosspayments.com/v1/payments/confirm"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"REJECT_CARD_COMPANY\",\"message\":\"카드사에서 거절한 결제입니다.\"}"));

        PaymentGateway.Result result = client.confirm("pk_test_bad", "payment_1", 10_000L);

        assertThat(result.approved()).isFalse();
        assertThat(result.failureReason()).isEqualTo("카드사에서 거절한 결제입니다.");
    }

    @Test
    void 네트워크_오류는_승인_불명_예외로_구분한다() {
        server.expect(requestTo("https://api.tosspayments.com/v1/payments/confirm"))
                .andRespond(request -> { throw new IOException("연결 끊김"); });

        assertThatThrownBy(() -> client.confirm("pk_test_x", "payment_1", 10_000L))
                .isInstanceOf(PaymentGatewayUnavailableException.class);
    }

    // ── 결제 조회 (복구용) ──

    @Test
    void 조회_결과가_DONE이고_금액이_같으면_승인으로_확정한다() {
        server.expect(requestTo("https://api.tosspayments.com/v1/payments/orders/payment_2"))
                .andRespond(withSuccess("{\"status\":\"DONE\",\"paymentKey\":\"pk_done\",\"totalAmount\":42100}",
                        MediaType.APPLICATION_JSON));

        var result = client.lookup("payment_2", 42_100L);

        assertThat(result).isPresent();
        assertThat(result.get().approved()).isTrue();
        assertThat(result.get().transactionId()).isEqualTo("pk_done");
    }

    @Test
    void DONE이어도_금액이_다르면_확정하지_않는다() {
        server.expect(requestTo("https://api.tosspayments.com/v1/payments/orders/payment_2"))
                .andRespond(withSuccess("{\"status\":\"DONE\",\"paymentKey\":\"pk_done\",\"totalAmount\":1000}",
                        MediaType.APPLICATION_JSON));

        assertThat(client.lookup("payment_2", 42_100L)).isEmpty();
    }

    @Test
    void 결제_내역이_없으면_미승인으로_확정한다() {
        server.expect(requestTo("https://api.tosspayments.com/v1/payments/orders/payment_2"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"NOT_FOUND_PAYMENT\",\"message\":\"존재하지 않는 결제 정보 입니다.\"}"));

        var result = client.lookup("payment_2", 42_100L);

        assertThat(result).isPresent();
        assertThat(result.get().approved()).isFalse();
    }

    @Test
    void 만료나_중단이면_미승인으로_확정한다() {
        server.expect(requestTo("https://api.tosspayments.com/v1/payments/orders/payment_2"))
                .andRespond(withSuccess("{\"status\":\"EXPIRED\"}", MediaType.APPLICATION_JSON));

        var result = client.lookup("payment_2", 42_100L);

        assertThat(result).isPresent();
        assertThat(result.get().approved()).isFalse();
    }

    @Test
    void 진행_중이면_아직_모름으로_둔다() {
        server.expect(requestTo("https://api.tosspayments.com/v1/payments/orders/payment_2"))
                .andRespond(withSuccess("{\"status\":\"IN_PROGRESS\"}", MediaType.APPLICATION_JSON));

        assertThat(client.lookup("payment_2", 42_100L)).isEmpty();
    }

    @Test
    void 키_오류나_토스_장애는_아직_모름으로_둔다() {
        server.expect(requestTo("https://api.tosspayments.com/v1/payments/orders/payment_2"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"UNAUTHORIZED_KEY\",\"message\":\"인증되지 않은 시크릿 키\"}"));

        assertThat(client.lookup("payment_2", 42_100L)).isEmpty();
    }

    @Test
    void 승인_요청에서_토스_5xx는_거절이_아니라_결과_불명이다() {
        server.expect(requestTo("https://api.tosspayments.com/v1/payments/confirm"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> client.confirm("pk_test_x", "payment_1", 10_000L))
                .isInstanceOf(PaymentGatewayUnavailableException.class);
    }
}