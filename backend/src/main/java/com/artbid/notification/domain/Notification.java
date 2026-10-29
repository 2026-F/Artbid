package com.artbid.notification.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 회원 한 명에게 보내는 알림 한 건. 읽음 여부는 readAt(null이면 안 읽음)으로만 관리한다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(indexes = @Index(name = "idx_notification_member", columnList = "memberId, id"))
public class Notification {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private Long memberId;

	@Enumerated(EnumType.STRING)
	@Column(length = 30)
	private NotificationType type;

	@Column(length = 100)
	private String title;

	@Column(length = 300)
	private String message;

	// 눌렀을 때 이동할 화면 경로 (예: /mypage). 없으면 null
	@Column(length = 200)
	private String linkUrl;

	private LocalDateTime createdAt;
	private LocalDateTime readAt;

	public static Notification create(Long memberId, NotificationType type, String title, String message,
			String linkUrl, LocalDateTime now) {
		if (memberId == null || memberId <= 0 || type == null || now == null) {
			throw new IllegalArgumentException("회원, 알림 종류, 생성 시각은 필수입니다.");
		}
		if (title == null || title.isBlank() || title.length() > 100) {
			throw new IllegalArgumentException("알림 제목은 1~100자여야 합니다.");
		}
		if (message != null && message.length() > 300) {
			throw new IllegalArgumentException("알림 내용은 300자 이하여야 합니다.");
		}
		if (linkUrl != null && (!linkUrl.startsWith("/") || linkUrl.startsWith("//") || linkUrl.length() > 200)) {
			// 외부 주소로 보내는 링크를 막는다. 앱 안의 경로만 허용
			throw new IllegalArgumentException("알림 링크는 /로 시작하는 앱 내부 경로여야 합니다.");
		}
		Notification notification = new Notification();
		notification.memberId = memberId;
		notification.type = type;
		notification.title = title.trim();
		notification.message = message;
		notification.linkUrl = linkUrl;
		notification.createdAt = now;
		return notification;
	}

	public boolean isRead() {
		return readAt != null;
	}

	/** 읽음 처리. 이미 읽었으면 처음 읽은 시각을 유지한다. */
	public void markRead(LocalDateTime now) {
		if (readAt == null) {
			readAt = now;
		}
	}
}
