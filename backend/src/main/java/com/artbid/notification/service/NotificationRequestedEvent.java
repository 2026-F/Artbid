package com.artbid.notification.service;

import com.artbid.notification.domain.NotificationType;

/**
 * 다른 기능이 "이 회원에게 알림을 보내 달라"고 요청하는 이벤트.
 * ApplicationEventPublisher로 발행하면 요청한 쪽 트랜잭션이 커밋된 뒤에 알림이 만들어진다.
 * 알림 저장이 실패해도 요청한 쪽(결제 등)은 롤백되지 않는다.
 */
public record NotificationRequestedEvent(
		Long memberId,
		NotificationType type,
		String title,
		String message,
		String linkUrl) {
}
