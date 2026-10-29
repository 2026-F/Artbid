package com.artbid.notification.service;

import com.artbid.notification.domain.Notification;
import com.artbid.notification.domain.NotificationType;
import com.artbid.notification.dto.NotificationPageResponse;
import com.artbid.notification.exception.NotificationException;
import com.artbid.notification.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

	static final Long MEMBER_ID = 1L;

	@Mock
	NotificationRepository notificationRepository;

	NotificationService service;

	@BeforeEach
	void setUp() {
		service = new NotificationService(notificationRepository);
	}

	static Notification notification(Long id, Long memberId) {
		Notification notification = Notification.create(memberId, NotificationType.PAYMENT_COMPLETED, "결제 완료", null, "/mypage",
				LocalDateTime.of(2026, 10, 29, 12, 0));
		ReflectionTestUtils.setField(notification, "id", id);
		return notification;
	}

	static void assertError(Runnable call, HttpStatus status, String code) {
		assertThatThrownBy(call::run).isInstanceOfSatisfying(NotificationException.class, e -> {
			assertThat(e.getStatus()).isEqualTo(status);
			assertThat(e.getCode()).isEqualTo(code);
		});
	}

	@Test
	void 내_알림을_최신순으로_페이지와_안_읽은_개수와_함께_돌려준다() {
		PageRequest pageable = PageRequest.of(0, 20);
		when(notificationRepository.findByMemberIdOrderByIdDesc(MEMBER_ID, pageable))
				.thenReturn(new PageImpl<>(List.of(notification(2L, MEMBER_ID), notification(1L, MEMBER_ID)), pageable, 2));
		when(notificationRepository.countByMemberIdAndReadAtIsNull(MEMBER_ID)).thenReturn(2L);

		NotificationPageResponse response = service.getMyNotifications(MEMBER_ID, 0, 20);

		assertThat(response.content()).extracting("id").containsExactly(2L, 1L);
		assertThat(response.totalElements()).isEqualTo(2);
		assertThat(response.unreadCount()).isEqualTo(2);
	}

	@Test
	void 페이지_크기가_범위를_벗어나면_400() {
		assertError(() -> service.getMyNotifications(MEMBER_ID, 0, 51), HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
		assertError(() -> service.getMyNotifications(MEMBER_ID, -1, 20), HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
	}

	@Test
	void 로그인하지_않으면_401() {
		assertError(() -> service.getMyNotifications(null, 0, 20), HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED");
		assertError(() -> service.countUnread(null), HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED");
	}

	@Test
	void 안_읽은_알림_개수() {
		when(notificationRepository.countByMemberIdAndReadAtIsNull(MEMBER_ID)).thenReturn(3L);

		assertThat(service.countUnread(MEMBER_ID)).isEqualTo(3L);
	}
}
