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
}