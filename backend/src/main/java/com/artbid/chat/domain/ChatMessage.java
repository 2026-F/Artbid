package com.artbid.chat.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 경매 라이브 방송 채팅 메시지. 한 번 저장하면 수정하지 않는다.
 * 보낸 사람 이름과 위탁자 여부는 보낸 시점 값을 그대로 남긴다 — 나중에 이름을 바꿔도 지난 대화는 그대로 보인다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "chat_message", indexes = {
		@Index(name = "idx_chat_message_auction_id", columnList = "auctionId, id"),
		@Index(name = "idx_chat_message_show_id", columnList = "showId, id")})
public class ChatMessage {

	public static final int MAX_CONTENT_LENGTH = 200;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** 경매 채팅이면 그 경매, 라이브 편성 채팅이면 보낸 시점의 현재 작품 경매 */
	@Column(nullable = false)
	private Long auctionId;

	/** 라이브 편성(LiveShow) 채팅이면 그 라이브 ID. 경매 단위 채팅이면 null */
	private Long showId;

	@Column(nullable = false)
	private Long senderId;

	@Column(nullable = false)
	private String senderName;

	/** 이 경매의 위탁자(방송 진행자)가 보낸 메시지인지 */
	@Column(nullable = false)
	private boolean host;

	@Column(nullable = false, length = MAX_CONTENT_LENGTH)
	private String content;

	@Column(nullable = false)
	private LocalDateTime createdAt;

	public static ChatMessage of(Long auctionId, Long senderId, String senderName, boolean host, String content,
			LocalDateTime now) {
		return create(auctionId, null, senderId, senderName, host, content, now);
	}

	/** 라이브 편성 채팅. 작품이 바뀌어도 같은 showId로 한 대화가 이어진다. */
	public static ChatMessage ofShow(Long showId, Long auctionId, Long senderId, String senderName, boolean host,
			String content, LocalDateTime now) {
		if (showId == null) {
			throw new IllegalArgumentException("라이브는 필수입니다.");
		}
		return create(auctionId, showId, senderId, senderName, host, content, now);
	}

	private static ChatMessage create(Long auctionId, Long showId, Long senderId, String senderName, boolean host,
			String content, LocalDateTime now) {
		String trimmed = content == null ? "" : content.trim();
		if (trimmed.isEmpty() || trimmed.length() > MAX_CONTENT_LENGTH) {
			throw new IllegalArgumentException("채팅 메시지는 1자 이상 " + MAX_CONTENT_LENGTH + "자 이하여야 합니다.");
		}
		ChatMessage message = new ChatMessage();
		message.auctionId = auctionId;
		message.showId = showId;
		message.senderId = senderId;
		message.senderName = senderName;
		message.host = host;
		message.content = trimmed;
		message.createdAt = now;
		return message;
	}
}
