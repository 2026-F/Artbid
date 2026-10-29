package com.artbid.notification.dto;

import com.artbid.notification.domain.Notification;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * 알림 목록 페이지. 프론트 공통 PageResponse(content, page, size, totalElements, totalPages)와 같은 모양에
 * 안 읽은 알림 개수(unreadCount)를 더했다. 헤더 배지와 "모두 읽음" 버튼 활성화에 쓴다.
 */
public record NotificationPageResponse(
		List<NotificationResponse> content,
		int page,
		int size,
		long totalElements,
		int totalPages,
		long unreadCount) {

	public static NotificationPageResponse of(Page<Notification> page, long unreadCount) {
		return new NotificationPageResponse(
				page.getContent().stream().map(NotificationResponse::from).toList(),
				page.getNumber(),
				page.getSize(),
				page.getTotalElements(),
				page.getTotalPages(),
				unreadCount);
	}
}
