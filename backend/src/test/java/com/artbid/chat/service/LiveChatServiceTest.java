package com.artbid.chat.service;

import com.artbid.chat.domain.ChatMessage;
import com.artbid.chat.dto.ChatMessageResponse;
import com.artbid.chat.repository.ChatMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LiveChatServiceTest {

	private static final Long AUCTION_ID = 1L;
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 13, 20, 0);

	private ChatMessageRepository chatMessageRepository;
	private LiveChatService liveChatService;

	@BeforeEach
	void setup() {
		chatMessageRepository = mock(ChatMessageRepository.class);
		liveChatService = new LiveChatService(chatMessageRepository);
	}

	private ChatMessage message(String content) {
		return ChatMessage.of(AUCTION_ID, 7L, "홍길동", false, content, NOW);
	}

	@Test
	void 최근_대화를_오래된_것부터_돌려준다() {
		when(chatMessageRepository.findByAuctionIdOrderByIdDesc(AUCTION_ID, PageRequest.of(0, 50)))
				.thenReturn(List.of(message("나중"), message("먼저")));

		List<ChatMessageResponse> result = liveChatService.getMessages(AUCTION_ID, null, null);

		assertThat(result).extracting(ChatMessageResponse::content).containsExactly("먼저", "나중");
	}

	@Test
	void beforeId가_있으면_그보다_오래된_메시지만_조회한다() {
		when(chatMessageRepository.findByAuctionIdAndIdLessThanOrderByIdDesc(AUCTION_ID, 30L, PageRequest.of(0, 20)))
				.thenReturn(List.of(message("이전 대화")));

		List<ChatMessageResponse> result = liveChatService.getMessages(AUCTION_ID, 30L, 20);

		assertThat(result).hasSize(1);
		verify(chatMessageRepository, never()).findByAuctionIdOrderByIdDesc(any(), any());
	}

	@Test
	void 조회_개수는_1에서_100_사이로_맞춘다() {
		liveChatService.getMessages(AUCTION_ID, null, 5000);
		liveChatService.getMessages(AUCTION_ID, null, -3);

		verify(chatMessageRepository).findByAuctionIdOrderByIdDesc(AUCTION_ID, PageRequest.of(0, 100));
		verify(chatMessageRepository).findByAuctionIdOrderByIdDesc(AUCTION_ID, PageRequest.of(0, 1));
	}
}
