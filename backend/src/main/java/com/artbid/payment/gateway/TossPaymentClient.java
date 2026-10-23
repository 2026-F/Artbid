package com.artbid.payment.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

@Component
public class TossPaymentClient {

    private static final String CONFIRM_URL = "https://api.tosspayments.com/v1/payments/confirm";

    private final RestClient restClient;
    private final String authorizationHeader;

    public TossPaymentClient(@Value("${app.payment.toss.secret-key}") String secretKey) {
        this.restClient = RestClient.create();
        // 토스 인증 방식: "시크릿키:" 를 Base64 인코딩해서 Basic 인증 헤더로 전송
        String encoded = Base64.getEncoder().encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8));
        this.authorizationHeader = "Basic " + encoded;
    }

    /** amount는 반드시 우리 서버 DB에 저장된 신뢰 가능한 금액을 넘겨야 한다 (클라이언트 값 사용 금지). */
    public PaymentGateway.Result confirm(String paymentKey, String orderId, Long amount) {
        try {
            JsonNode response = restClient.post()
                    .uri(CONFIRM_URL)
                    .header(HttpHeaders.AUTHORIZATION, authorizationHeader)
                    .body(Map.of("paymentKey", paymentKey, "orderId", orderId, "amount", amount))
                    .retrieve()
                    .body(JsonNode.class);

            String transactionId = response != null ? response.path("paymentKey").asText(paymentKey) : paymentKey;
            return new PaymentGateway.Result(true, transactionId, null);

        } catch (RestClientResponseException e) {
            // 4xx: 토스가 승인을 거절함 (한도 초과, 유효하지 않은 카드 등)
            return new PaymentGateway.Result(false, null, extractErrorMessage(e));

        } catch (Exception e) {
            // 네트워크 오류·타임아웃 등 승인 성공/실패를 알 수 없는 경우
            throw new PaymentGatewayUnavailableException("토스페이먼츠 응답 없음: " + e.getMessage());
        }
    }

    private String extractErrorMessage(RestClientResponseException e) {
        try {
            JsonNode body = e.getResponseBodyAs(JsonNode.class);
            String msg = body != null ? body.path("message").asText(null) : null;
            return (msg != null && !msg.isBlank()) ? msg : "토스 결제 승인이 거절되었습니다.";
        } catch (Exception parseError) {
            return "토스 결제 승인이 거절되었습니다.";
        }
    }
}