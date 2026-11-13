package com.artbid.chat.service;

import com.artbid.artwork.domain.Artwork;
import com.artbid.artwork.repository.ArtworkRepository;
import com.artbid.auction.domain.Auction;
import com.artbid.auction.repository.AuctionRepository;
import com.artbid.chat.domain.ChatMessage;
import com.artbid.chat.dto.ChatMessageResponse;
import com.artbid.chat.exception.ChatAuthRequiredException;
import com.artbid.chat.exception.ChatNotAvailableException;
import com.artbid.chat.repository.ChatMessageRepository;
import com.artbid.infra.realtime.AuctionSseRegistry;
import com.artbid.member.domain.Member;
import com.artbid.member.repository.MemberRepository;
import com.artbid.streaming.domain.Livestream;
import com.artbid.streaming.repository.LivestreamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 경매 라이브 방송 채팅. 읽기는 누구나, 보내기는 로그인한 회원만 할 수 있다.
 * 새 메시지는 저장한 뒤 경매 실시간 구독(SSE)으로 그 경매를 보고 있는 화면에 전달한다.
 * SSE 구독은 서버 인스턴스 메모리에 있어서, 입찰 알림과 마찬가지로 인스턴스가 여러 대면 전달되지 않는 화면이 생긴다.
 */
@Service
@RequiredArgsConstructor
public class LiveChatService {

	static final int DEFAULT_HISTORY_SIZE = 50;
	static final int MAX_HISTORY_SIZE = 100;

	private final ChatMessageRepository chatMessageRepository;
	private final LivestreamRepository livestreamRepository;
	private final MemberRepository memberRepository;
	private final AuctionRepository auctionRepository;
	private final ArtworkRepository artworkRepository;
	private final AuctionSseRegistry sseRegistry;

	/**
	 * 채팅을 보낸다. 방송이 열려 있는 동안(연결이 잠시 끊긴 상태 포함)만 받는다.
	 * 보낸 사람과 위탁자 여부는 요청 본문이 아니라 검증된 회원 ID로 서버가 정한다.
	 * 트랜잭션을 걸지 않는다 — save()가 커밋된 뒤에 알려야 조회 API와 어긋나지 않는다.
	 */
	public ChatMessageResponse send(Long auctionId, Long senderId, String content) {
		livestreamRepository.findByAuctionId(auctionId)
				.filter(Livestream::isActive)
				.orElseThrow(() -> new ChatNotAvailableException(auctionId));

		// 토큰은 유효하지만 회원이 없는 경우(탈퇴 등)도 로그인 필요로 본다.
		Member sender = memberRepository.findById(senderId).orElseThrow(ChatAuthRequiredException::new);

		ChatMessage saved = chatMessageRepository.save(ChatMessage.of(
				auctionId, senderId, displayName(sender), isConsignor(auctionId, senderId), content,
				LocalDateTime.now()));

		ChatMessageResponse response = ChatMessageResponse.from(saved);
		sseRegistry.broadcastEvent(auctionId, LiveChatSseEvents.CHAT, response);
		return response;
	}

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

	private String displayName(Member member) {
		String name = member.getName();
		return name == null || name.isBlank() ? "회원" + member.getId() : name.trim();
	}

	/** 이 경매의 위탁 작품을 올린 본인인지. 채팅에 진행자 표시를 붙이는 데만 쓴다. */
	private boolean isConsignor(Long auctionId, Long memberId) {
		return auctionRepository.findById(auctionId)
				.map(Auction::getArtworkId)
				.flatMap(artworkRepository::findById)
				.map(Artwork::getConsignorId)
				.map(memberId::equals)
				.orElse(false);
	}

	private int clampSize(Integer size) {
		if (size == null) {
			return DEFAULT_HISTORY_SIZE;
		}
		return Math.max(1, Math.min(size, MAX_HISTORY_SIZE));
	}
}
