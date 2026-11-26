package com.artbid.settlement.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 정산 배치. 기본 10분마다 실행한다 (app.settlement.batch.cron).
 * 1) 결제 기한이 지난 미결제 정산 만료  2) 결제 완료 정산의 위탁자 지급 대기 생성
 * 건마다 트랜잭션을 따로 쓰므로 한 건이 실패해도 나머지는 처리되고, 실패한 건은 다음 실행에서 다시 시도한다.
 * 서버가 여러 대여도 정산 행 잠금과 지급 유니크 제약으로 중복 처리되지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.settlement.batch", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SettlementBatchScheduler {

	private final SettlementExpiryService settlementExpiryService;
	private final SellerPayoutService sellerPayoutService;

	@Scheduled(cron = "${app.settlement.batch.cron:0 */10 * * * *}")
	public void run() {
		BatchResult result = runOnce(LocalDateTime.now().truncatedTo(ChronoUnit.MICROS));
		if (result.expired() + result.payoutsCreated() + result.failed() > 0) {
			log.info("[정산 배치] 만료 {}건, 지급 대기 생성 {}건, 실패 {}건",
					result.expired(), result.payoutsCreated(), result.failed());
		}
	}

	public BatchResult runOnce(LocalDateTime now) {
		int expired = 0;
		int payoutsCreated = 0;
		int failed = 0;

		for (Long settlementId : settlementExpiryService.findOverdueIds(now)) {
			try {
				if (settlementExpiryService.expireOne(settlementId, now)) {
					expired++;
				}
			} catch (Exception e) {
				failed++;
				log.error("[정산 배치] settlementId={} 만료 처리 실패, 다음 실행에서 재시도", settlementId, e);
			}
		}

		for (Long settlementId : sellerPayoutService.findTargetIds()) {
			try {
				if (sellerPayoutService.createReady(settlementId, now)) {
					payoutsCreated++;
				}
			} catch (Exception e) {
				failed++;
				log.error("[정산 배치] settlementId={} 지급 대기 생성 실패, 다음 실행에서 재시도", settlementId, e);
			}
		}
		return new BatchResult(expired, payoutsCreated, failed);
	}

	public record BatchResult(int expired, int payoutsCreated, int failed) {
	}
}
