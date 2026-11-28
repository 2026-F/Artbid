package com.artbid.chat.dto;

import com.artbid.chat.domain.ChatMessage;

import java.time.LocalDateTime;

/**
 * 채팅 메시지 응답. 조회 API와 SSE(auction-chat, show-chat) 이벤트가 같은 모양을 쓴다.
 * showId는 라이브 편성 채팅일 때만 들어 있고, 경매 단위 채팅이면 null이다.
 */
public record ChatMessageResponse(
		Long id,
		Long auctionId,
		Long senderId,
		String senderName,
		boolean host,
		String content,
		LocalDateTime createdAt,
		Long showId
) {

	public static ChatMessageResponse from(ChatMessage message) {
		return new ChatMessageResponse(
				message.getId(),
				message.getAuctionId(),
				message.getSenderId(),
				message.getSenderName(),
				message.isHost(),
				message.getContent(),
				message.getCreatedAt(),
				message.getShowId());
	}
}
