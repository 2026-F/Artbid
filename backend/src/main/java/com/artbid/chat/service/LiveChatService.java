package com.artbid.chat.service;

import com.artbid.chat.domain.ChatMessage;
import com.artbid.chat.dto.ChatMessageResponse;
import com.artbid.chat.repository.ChatMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 경매 라이브 방송 채팅. */
@Service
@RequiredArgsConstructor
public class LiveChatService {

	static final int DEFAULT_HISTORY_SIZE = 50;
	static final int MAX_HISTORY_SIZE = 100;

	private final ChatMessageRepository chatMessageRepository;

	/**
	 * 최근 대화를 오래된 것부터(화면에 쌓이는 순서대로) 돌려준다.
	 * beforeId가 있으면 그보다 오래된 메시지만 본다. 비로그인 시청자도 볼 수 있다.
	 */
	@Transactional(readOnly = true)
	public List<ChatMessageResponse> getMessages(Long auctionId, Long beforeId, Integer size) {
		Pageable limit = PageRequest.of(0, clampSize(size));
		List<ChatMessage> newestFirst = beforeId == null
				? chatMessageRepository.findByAuctionIdOrderByIdDesc(auctionId, limit)
				: chatMessageRepository.findByAuctionIdAndIdLessThanOrderByIdDesc(auctionId, beforeId, limit);

		List<ChatMessage> oldestFirst = new ArrayList<>(newestFirst);
		Collections.reverse(oldestFirst);
		return oldestFirst.stream().map(ChatMessageResponse::from).toList();
	}

	private int clampSize(Integer size) {
		if (size == null) {
			return DEFAULT_HISTORY_SIZE;
		}
		return Math.max(1, Math.min(size, MAX_HISTORY_SIZE));
	}
}
