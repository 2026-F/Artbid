package com.artbid.chat.controller;

import com.artbid.chat.dto.ChatMessageRequest;
import com.artbid.chat.dto.ChatMessageResponse;
import com.artbid.chat.exception.ChatAuthRequiredException;
import com.artbid.chat.service.LiveChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
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

	/** 채팅 전송. 로그인한 회원만 보낼 수 있다. */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ChatMessageResponse send(@PathVariable Long auctionId, @Valid @RequestBody ChatMessageRequest request,
			Principal principal) {
		return liveChatService.send(auctionId, requireMemberId(principal), request.content());
	}

	// Principal.name에는 검증된 회원 ID가 들어온다 (BidController·StreamingController와 같은 방식).
	private Long requireMemberId(Principal principal) {
		if (principal != null) {
			try {
				long id = Long.parseLong(principal.getName());
				if (id > 0) {
					return id;
				}
			} catch (NumberFormatException ignored) {
				// 아래에서 인증 필요 예외로 처리
			}
		}
		throw new ChatAuthRequiredException();
	}
}
