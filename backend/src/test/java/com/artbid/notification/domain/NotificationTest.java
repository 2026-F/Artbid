package com.artbid.notification.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationTest {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 29, 12, 0);

	@Test
	void 새_알림은_안_읽은_상태다() {
		Notification notification = Notification.create(1L, NotificationType.PAYMENT_COMPLETED, "결제 완료", "내용", "/mypage", NOW);

		assertThat(notification.isRead()).isFalse();
		assertThat(notification.getReadAt()).isNull();
		assertThat(notification.getCreatedAt()).isEqualTo(NOW);
	}

	@Test
	void 읽음_처리하면_처음_읽은_시각을_유지한다() {
		Notification notification = Notification.create(1L, NotificationType.SYSTEM, "공지", null, null, NOW);

		notification.markRead(NOW.plusMinutes(1));
		notification.markRead(NOW.plusMinutes(5));

		assertThat(notification.isRead()).isTrue();
		assertThat(notification.getReadAt()).isEqualTo(NOW.plusMinutes(1));
	}

	@Test
	void 제목이_비었거나_너무_길면_거절한다() {
		assertThatThrownBy(() -> Notification.create(1L, NotificationType.SYSTEM, " ", null, null, NOW))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> Notification.create(1L, NotificationType.SYSTEM, "가".repeat(101), null, null, NOW))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void 링크는_앱_내부_경로만_허용한다() {
		assertThatThrownBy(() -> Notification.create(1L, NotificationType.SYSTEM, "공지", null, "https://evil.example", NOW))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> Notification.create(1L, NotificationType.SYSTEM, "공지", null, "//evil.example", NOW))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
