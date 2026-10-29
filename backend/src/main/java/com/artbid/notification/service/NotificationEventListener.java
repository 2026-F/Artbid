package com.artbid.notification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 알림 요청을 받아 저장한다.
 * - 요청한 쪽 트랜잭션이 커밋된 뒤에만 만든다. 롤백된 결제에 대해 "결제 완료" 알림이 가면 안 되기 때문이다.
 * - 커밋 후에는 기존 트랜잭션을 쓸 수 없어서 새 트랜잭션(REQUIRES_NEW)으로 저장한다.
 * - 저장이 실패해도 예외를 밖으로 던지지 않는다. 알림은 부가 기능이라 원래 요청 응답에 영향을 주지 않는다.
 *   로그에는 회원 ID와 종류만 남기고 제목·내용은 남기지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

	private final NotificationService notificationService;

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void onRequested(NotificationRequestedEvent event) {
		try {
			notificationService.create(event);
		} catch (RuntimeException e) {
			log.warn("[알림] 생성 실패: memberId={}, type={}, 원인={}", event.memberId(), event.type(), e.getClass().getSimpleName());
		}
	}
}
