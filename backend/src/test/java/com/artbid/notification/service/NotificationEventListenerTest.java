package com.artbid.notification.service;

import com.artbid.notification.domain.Notification;
import com.artbid.notification.domain.NotificationType;
import com.artbid.notification.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationEventListenerTest {

	@Mock
	NotificationRepository notificationRepository;

	@Test
	void 알림_요청을_받으면_저장한다() {
		NotificationEventListener listener = new NotificationEventListener(new NotificationService(notificationRepository));

		listener.onRequested(new NotificationRequestedEvent(1L, NotificationType.PAYMENT_COMPLETED, "결제 완료", "내용", "/mypage"));

		ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
		verify(notificationRepository).save(saved.capture());
		assertThat(saved.getValue().getMemberId()).isEqualTo(1L);
		assertThat(saved.getValue().getType()).isEqualTo(NotificationType.PAYMENT_COMPLETED);
		assertThat(saved.getValue().getLinkUrl()).isEqualTo("/mypage");
	}

	@Test
	void 잘못된_요청이나_저장_실패는_밖으로_던지지_않는다() {
		NotificationEventListener listener = new NotificationEventListener(new NotificationService(notificationRepository));

		assertThatCode(() -> listener.onRequested(new NotificationRequestedEvent(1L, NotificationType.SYSTEM, " ", null, null)))
				.doesNotThrowAnyException();

		when(notificationRepository.save(any())).thenThrow(new IllegalStateException("DB 오류"));
		assertThatCode(() -> listener.onRequested(new NotificationRequestedEvent(1L, NotificationType.SYSTEM, "공지", null, null)))
				.doesNotThrowAnyException();
	}
}
