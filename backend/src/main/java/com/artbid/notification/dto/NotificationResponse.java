package com.artbid.notification.dto;

import com.artbid.notification.domain.Notification;
import com.artbid.notification.domain.NotificationType;

import java.time.LocalDateTime;

public record NotificationResponse(
		Long id,
		NotificationType type,
		String title,
		String message,
		String linkUrl,
		boolean read,
		LocalDateTime createdAt,
		LocalDateTime readAt) {

	public static NotificationResponse from(Notification notification) {
		return new NotificationResponse(
				notification.getId(),
				notification.getType(),
				notification.getTitle(),
				notification.getMessage(),
				notification.getLinkUrl(),
				notification.isRead(),
				notification.getCreatedAt(),
				notification.getReadAt());
	}
}
