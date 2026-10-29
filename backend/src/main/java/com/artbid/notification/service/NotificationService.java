package com.artbid.notification.service;

import com.artbid.notification.domain.Notification;
import com.artbid.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
public class NotificationService {

	private final NotificationRepository notificationRepository;

	@Transactional
	public Notification create(NotificationRequestedEvent request) {
		return notificationRepository.save(Notification.create(
				request.memberId(), request.type(), request.title(), request.message(), request.linkUrl(), now()));
	}

	static LocalDateTime now() {
		return LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
	}
}
