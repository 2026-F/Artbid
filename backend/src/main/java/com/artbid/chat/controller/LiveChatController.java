package com.artbid.chat.controller;

import com.artbid.chat.dto.ChatMessageResponse;
import com.artbid.chat.service.LiveChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/auctions/{auctionId}/chat/messages")
@RequiredArgsConstructor
public class LiveChatController {

	private final LiveChatService liveChatService;

	/** 최근 대화 조회. 비로그인 시청자도 호출한다. 새 메시지는 SSE(auction-chat)로 받는다. */
	@GetMapping
	public List<ChatMessageResponse> getMessages(@PathVariable Long auctionId,
			@RequestParam(required = false) Long beforeId,
			@RequestParam(required = false) Integer size) {
		return liveChatService.getMessages(auctionId, beforeId, size);
	}
}
