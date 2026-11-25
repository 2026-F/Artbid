package com.artbid.payment.service;

import com.artbid.payment.domain.Payment;
import com.artbid.payment.domain.PaymentStatus;
import com.artbid.payment.gateway.PaymentGateway;
import com.artbid.payment.gateway.TossPaymentClient;
import com.artbid.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * 결과 불명 결제 복구 (대사).
 *
 * 결제창을 닫거나 승인 응답이 끊기면 결제가 REQUESTED로 남는다. 그러면 재결제가 막히고,
 * 정산 만료 배치도 "돈이 빠져나갔을 수 있다"며 만료를 보류해서 정산이 영원히 그 상태로 남는다.
 * 이 스케줄러가 오래된 REQUESTED 결제를 PG에 물어보고 완료나 실패로 확정한다.
 *
 * - 결과를 모르면 실패로 단정하지 않는다. 토스가 확실히 답한 경우에만 확정하고, 나머지는 다음 회차에 다시 조회한다.
 * - 건마다 트랜잭션을 따로 쓰므로 한 건이 실패해도 나머지는 처리된다.
 * - 서버가 여러 대여도 resolveUnknown()이 정산 행을 잠그고 상태를 다시 확인하므로 한 번만 확정된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.payment.recovery", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PaymentRecoveryScheduler {

    private final PaymentRepository paymentRepository;
    private final PaymentTransactionService transactions;
    private final TossPaymentClient tossPaymentClient;
    private final Clock paymentClock;

    // 요청 후 이 시간이 지난 결제만 확인한다. 사용자가 결제창에서 결제하는 중인 건을 건드리지 않기 위해서다.
    @Value("${app.payment.recovery.stale-after-minutes:15}")
    private long staleAfterMinutes;

    @Scheduled(fixedDelayString = "${app.payment.recovery.interval-ms:60000}")
    public void run() {
        RecoveryResult result = runOnce(LocalDateTime.now(paymentClock).truncatedTo(ChronoUnit.MICROS));
        if (result.resolved() + result.unknown() + result.failed() > 0) {
            log.info("[결제 복구] 확정 {}건, 아직 모름 {}건, 처리 실패 {}건",
                    result.resolved(), result.unknown(), result.failed());
        }
    }

    public RecoveryResult runOnce(LocalDateTime now) {
        LocalDateTime before = now.minusMinutes(staleAfterMinutes);
        List<Payment> targets = paymentRepository
                .findTop50ByStatusAndRequestedAtBeforeOrderByIdAsc(PaymentStatus.REQUESTED, before);

        int resolved = 0;
        int unknown = 0;
        int failed = 0;
        for (Payment payment : targets) {
            try {
                Optional<PaymentGateway.Result> result = lookup(payment);
                if (result.isEmpty()) {
                    unknown++; // 아직 모름 → 다음 회차에 다시 조회
                    continue;
                }
                if (transactions.resolveUnknown(payment.getId(), result.get())) {
                    resolved++;
                    log.info("[결제 복구] paymentId={} → {}", payment.getId(),
                            result.get().approved() ? "결제 완료" : "실패 (" + result.get().failureReason() + ")");
                }
            } catch (Exception e) {
                failed++;
                log.error("[결제 복구] paymentId={} 처리 실패, 다음 회차에 재시도", payment.getId(), e);
            }
        }
        return new RecoveryResult(resolved, unknown, failed);
    }

    private Optional<PaymentGateway.Result> lookup(Payment payment) {
        return switch (payment.getMethod()) {
            // 토스 주문번호는 결제창을 띄울 때와 같은 규칙("payment_" + id)이다. (PaymentService.confirmToss 참고)
            case TOSS -> tossPaymentClient.lookup("payment_" + payment.getId(), payment.getAmount());
            // 모의 PG는 물어볼 곳이 없다. 응답을 못 받은 모의 결제는 승인되지 않은 것으로 정리한다.
            case MOCK_PG -> Optional.of(new PaymentGateway.Result(false, null, "모의 결제 결과를 확인할 수 없어 미승인으로 정리"));
        };
    }

    public record RecoveryResult(int resolved, int unknown, int failed) {
    }
}
