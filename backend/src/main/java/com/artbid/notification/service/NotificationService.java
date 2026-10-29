package com.artbid.notification.service;

import com.artbid.notification.domain.Notification;
import com.artbid.notification.dto.NotificationPageResponse;
import com.artbid.notification.exception.NotificationException;
import com.artbid.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Service
@RequiredArgsConstructor
public class NotificationService {

	public static final int MAX_PAGE_SIZE = 50;

	private final NotificationRepository notificationRepository;

	@Transactional
	public Notification create(NotificationRequestedEvent request) {
		return notificationRepository.save(Notification.create(
				request.memberId(), request.type(), request.title(), request.message(), request.linkUrl(), now()));
	}

	/** 내 알림 목록 (최신순). 안 읽은 개수도 함께 돌려준다. */
	@Transactional(readOnly = true)
	public NotificationPageResponse getMyNotifications(Long memberId, int page, int size) {
		requireLogin(memberId);
		if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
			throw new NotificationException(BAD_REQUEST, "VALIDATION_ERROR", "page는 0 이상, size는 1~" + MAX_PAGE_SIZE + "이어야 합니다.");
		}
		return NotificationPageResponse.of(
				notificationRepository.findByMemberIdOrderByIdDesc(memberId, PageRequest.of(page, size)),
				notificationRepository.countByMemberIdAndReadAtIsNull(memberId));
	}

	@Transactional(readOnly = true)
	public long countUnread(Long memberId) {
		requireLogin(memberId);
		return notificationRepository.countByMemberIdAndReadAtIsNull(memberId);
	}

	/**
	 * 알림 하나 읽음 처리. 이미 읽었어도 성공으로 본다.
	 * 다른 회원의 알림이면 존재 여부를 알려주지 않도록 없는 알림과 똑같이 404로 응답한다.
	 */
	@Transactional
	public Notification markRead(Long memberId, Long notificationId) {
		requireLogin(memberId);
		Notification notification = notificationRepository.findById(notificationId)
				.filter(found -> found.getMemberId().equals(memberId))
				.orElseThrow(() -> new NotificationException(NOT_FOUND, "NOTIFICATION_NOT_FOUND", "알림을 찾을 수 없습니다."));
		notification.markRead(now());
		return notification;
	}

	/** 내 안 읽은 알림을 모두 읽음 처리하고 바뀐 개수를 돌려준다. */
	@Transactional
	public int markAllRead(Long memberId) {
		requireLogin(memberId);
		return notificationRepository.markAllRead(memberId, now());
	}

	static void requireLogin(Long memberId) {
		if (memberId == null || memberId <= 0) {
			throw new NotificationException(UNAUTHORIZED, "AUTH_REQUIRED", "로그인이 필요합니다.");
		}
	}

	static LocalDateTime now() {
		return LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
	}
}
