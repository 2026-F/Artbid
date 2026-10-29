package com.artbid.notification.controller;

import com.artbid.notification.dto.NotificationPageResponse;
import com.artbid.notification.dto.NotificationResponse;
import com.artbid.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

/** 내 알림. 항상 로그인한 회원 본인 것만 다룬다(회원 ID는 토큰에서만 가져온다). */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

	private final NotificationService notificationService;

	@GetMapping
	public NotificationPageResponse getMyNotifications(@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size, Principal principal) {
		return notificationService.getMyNotifications(memberId(principal), page, size);
	}

	@GetMapping("/unread-count")
	public UnreadCountResponse countUnread(Principal principal) {
		return new UnreadCountResponse(notificationService.countUnread(memberId(principal)));
	}

	public record UnreadCountResponse(long unreadCount) {
	}

	// 알림 하나 읽음 (본인 알림만)
	@PatchMapping("/{id}/read")
	public NotificationResponse markRead(@PathVariable Long id, Principal principal) {
		return NotificationResponse.from(notificationService.markRead(memberId(principal), id));
	}

	// 모두 읽음. 화면 우측 상단 버튼
	@PatchMapping("/read-all")
	public ReadAllResponse markAllRead(Principal principal) {
		return new ReadAllResponse(notificationService.markAllRead(memberId(principal)));
	}

	public record ReadAllResponse(int updatedCount) {
	}

	// Authentication 연동 계약: Principal.name에는 검증된 Member ID를 사용한다. 로그인하지 않았으면 null → 서비스가 401
	static Long memberId(Principal principal) {
		if (principal == null) {
			return null;
		}
		try {
			return Long.parseLong(principal.getName());
		} catch (NumberFormatException e) {
			return null;
		}
	}
}
