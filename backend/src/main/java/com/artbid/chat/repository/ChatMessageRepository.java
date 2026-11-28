package com.artbid.chat.repository;

import com.artbid.chat.domain.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

	// 경매 단위 채팅. 라이브 편성 채팅(showId 있음)도 auctionId를 채우므로 섞이지 않게 showId가 없는 것만 본다.

	/** 가장 최근 메시지부터 size개 */
	List<ChatMessage> findByAuctionIdAndShowIdIsNullOrderByIdDesc(Long auctionId, Pageable pageable);

	/** beforeId보다 오래된 메시지를 최근 것부터 size개 (위로 스크롤해 이전 대화를 더 볼 때) */
	List<ChatMessage> findByAuctionIdAndShowIdIsNullAndIdLessThanOrderByIdDesc(Long auctionId, Long beforeId,
			Pageable pageable);

	// 라이브 편성 채팅. 작품이 바뀌어도 같은 라이브의 대화를 이어서 본다.

	List<ChatMessage> findByShowIdOrderByIdDesc(Long showId, Pageable pageable);

	List<ChatMessage> findByShowIdAndIdLessThanOrderByIdDesc(Long showId, Long beforeId, Pageable pageable);
}
