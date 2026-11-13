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
@Table(name = "chat_message", indexes = @Index(name = "idx_chat_message_auction_id", columnList = "auctionId, id"))
public class ChatMessage {

	public static final int MAX_CONTENT_LENGTH = 200;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long auctionId;

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
		String trimmed = content == null ? "" : content.trim();
		if (trimmed.isEmpty() || trimmed.length() > MAX_CONTENT_LENGTH) {
			throw new IllegalArgumentException("채팅 메시지는 1자 이상 " + MAX_CONTENT_LENGTH + "자 이하여야 합니다.");
		}
		ChatMessage message = new ChatMessage();
		message.auctionId = auctionId;
		message.senderId = senderId;
		message.senderName = senderName;
		message.host = host;
		message.content = trimmed;
		message.createdAt = now;
		return message;
	}
}
