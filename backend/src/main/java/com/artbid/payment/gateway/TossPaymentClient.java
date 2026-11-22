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

import lombok.extern.slf4j.Slf4j;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Component
public class TossPaymentClient {

    private static final String CONFIRM_URL = "https://api.tosspayments.com/v1/payments/confirm";
    private static final String LOOKUP_URL = "https://api.tosspayments.com/v1/payments/orders/{orderId}";
    private static final Set<String> CONFIG_ERROR_CODES = Set.of("UNAUTHORIZED_KEY", "NOT_FOUND_MERCHANT");

    private final RestClient restClient;
    private final String authorizationHeader;

    public TossPaymentClient(RestClient.Builder restClientBuilder,
                             @Value("${app.payment.toss.secret-key}") String secretKey) {
        this.restClient = restClientBuilder.build();   // RestClient.create() 대신
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
            // 5xx는 토스 내부 오류라 승인이 됐는지 알 수 없다.
            // 거절로 처리하면 실제로는 돈이 빠져나간 결제를 실패로 만들 수 있으므로 결과 불명으로 넘긴다.
            if (e.getStatusCode().is5xxServerError()) {
                throw new PaymentGatewayUnavailableException("토스페이먼츠 서버 오류: " + e.getStatusCode().value());
            }
            // 4xx: 토스가 승인을 명확히 거절함 (한도 초과, 유효하지 않은 카드 등)
            return new PaymentGateway.Result(false, null, extractErrorMessage(e));
        } catch (Exception e) {
            // 네트워크 오류·타임아웃 등 승인 성공/실패를 알 수 없는 경우
            throw new PaymentGatewayUnavailableException("토스페이먼츠 응답 없음: " + e.getMessage());
        }
    }

    /**
     * 주문번호로 토스에 결제 상태를 물어본다. 결과 불명(REQUESTED) 결제를 확정하는 복구 스케줄러가 쓴다
     *  - 결제 완료(DONE)이고 금액이 우리 DB와 같음 → 승인(approved=true), 거래 ID는 paymentKey
     *  - 결제 내역 없음, 중단(ABORTED), 만료(EXPIRED), 취소(CANCELED) → 미승인(approved=false). 돈이 빠져나가지 않은 상태다.
     *  - 그 밖의 모든 경우 → Optional.empty() = 아직 모른다. 실패로 단정하지 않고 다음 회차에 다시 조회한다.
     *    (진행 중 상태, 키 오류 401, 요청 과다 429, 토스 장애 5xx, 네트워크 오류, 금액 불일치)
     */
    public Optional<PaymentGateway.Result> lookup(String orderId, Long expectedAmount) {
        JsonNode response;
        try {
            response = restClient.get()
                    .uri(LOOKUP_URL, orderId)
                    .header(HttpHeaders.AUTHORIZATION, authorizationHeader)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException e) {
            String code = extractErrorCode(e);
            // 결제창에서 인증 전에 나가면 토스에는 이 주문번호의 결제가 아예 없다.
            if (e.getStatusCode().value() == 404 && "NOT_FOUND_PAYMENT".equals(code)) {
                return Optional.of(new PaymentGateway.Result(false, null, "토스 결제 내역 없음 (결제창 이탈)"));
            }
            // 키나 상점 설정 문제는 기다려도 풀리지 않는다. 사람이 바로 알 수 있게 ERROR로 남긴다.
            if (CONFIG_ERROR_CODES.contains(code)) {
                log.error("[결제 복구] 토스 키·상점 설정 오류, 결제를 확정할 수 없음 (orderId={}, code={})", orderId, code);
                return Optional.empty();
            }
            log.warn("[결제 복구] 토스 조회 실패, 다음 회차에 재시도 (orderId={}, status={}, code={})",
                    orderId, e.getStatusCode().value(), code);
            return Optional.empty();
        } catch (Exception e) {
            log.warn("[결제 복구] 토스 조회 응답 없음, 다음 회차에 재시도 (orderId={}): {}", orderId, e.getMessage());
            return Optional.empty();
        }

        if (response == null) {
            return Optional.empty();
        }
        String status = response.path("status").asText("");
        return switch (status) {
            case "DONE" -> approvedIfAmountMatches(response, orderId, expectedAmount);
            case "ABORTED", "EXPIRED", "CANCELED" ->
                    Optional.of(new PaymentGateway.Result(false, null, "토스 결제 상태: " + status));
            // READY, IN_PROGRESS(인증 후 승인 대기) 등은 아직 바뀔 수 있으므로 건드리지 않는다.
            default -> Optional.empty();
        };
    }

    // 토스는 결제 완료인데 금액이 우리 DB와 다르면 자동으로 확정하지 않는다. 사람이 확인해야 하는 상황이다.
    private Optional<PaymentGateway.Result> approvedIfAmountMatches(JsonNode response, String orderId, Long expectedAmount) {
        long totalAmount = response.path("totalAmount").asLong(-1);
        if (expectedAmount == null || totalAmount != expectedAmount) {
            log.error("[결제 복구] 토스 결제 금액 불일치, 수동 확인 필요 (orderId={}, 토스={}, DB={})",
                    orderId, totalAmount, expectedAmount);
            return Optional.empty();
        }
        String paymentKey = response.path("paymentKey").asText("");
        if (paymentKey.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new PaymentGateway.Result(true, paymentKey, null));
    }

    private String extractErrorCode(RestClientResponseException e) {
        try {
            JsonNode body = e.getResponseBodyAs(JsonNode.class);
            return body != null ? body.path("code").asText(null) : null;
        } catch (Exception parseError) {
            return null;
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