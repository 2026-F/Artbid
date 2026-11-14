package com.artbid.chat.dto;

import com.artbid.chat.domain.ChatMessage;

import java.time.LocalDateTime;

/** 채팅 메시지 응답. 조회 API와 SSE(auction-chat) 이벤트가 같은 모양을 쓴다. */
public record ChatMessageResponse(
		Long id,
		Long auctionId,
		Long senderId,
		String senderName,
		boolean host,
		String content,
		LocalDateTime createdAt
) {

	public static ChatMessageResponse from(ChatMessage message) {
		return new ChatMessageResponse(
				message.getId(),
				message.getAuctionId(),
				message.getSenderId(),
				message.getSenderName(),
				message.isHost(),
				message.getContent(),
				message.getCreatedAt());
	}
}
