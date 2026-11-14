package com.artbid.chat.repository;

import com.artbid.chat.domain.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

	/** 가장 최근 메시지부터 size개 */
	List<ChatMessage> findByAuctionIdOrderByIdDesc(Long auctionId, Pageable pageable);

	/** beforeId보다 오래된 메시지를 최근 것부터 size개 (위로 스크롤해 이전 대화를 더 볼 때) */
	List<ChatMessage> findByAuctionIdAndIdLessThanOrderByIdDesc(Long auctionId, Long beforeId, Pageable pageable);
}
