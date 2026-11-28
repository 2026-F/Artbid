package com.artbid.liveshow.service;

import com.artbid.chat.config.LiveChatProperties;
import com.artbid.chat.domain.ChatMessage;
import com.artbid.chat.dto.ChatMessageResponse;
import com.artbid.chat.exception.ChatAuthRequiredException;
import com.artbid.chat.exception.ChatNicknameRequiredException;
import com.artbid.chat.exception.ChatRateLimitExceededException;
import com.artbid.chat.repository.ChatMessageRepository;
import com.artbid.infra.redis.FixedWindowRateLimiter;
import com.artbid.liveshow.domain.LiveShow;
import com.artbid.liveshow.domain.LiveShowLot;
import com.artbid.liveshow.exception.LiveShowConflictException;
import com.artbid.liveshow.exception.LiveShowNotFoundException;
import com.artbid.liveshow.realtime.LiveShowSseEvents;
import com.artbid.liveshow.realtime.LiveShowSseRegistry;
import com.artbid.liveshow.repository.LiveShowLotRepository;
import com.artbid.liveshow.repository.LiveShowRepository;
import com.artbid.member.domain.Member;
import com.artbid.member.repository.MemberRepository;
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
 * 라이브 편성 단위 채팅. 작품이 바뀌어도 같은 라이브의 대화가 이어진다.
 * 길이 제한, 속도 제한, 닉네임 필수, host 표시는 경매 채팅(LiveChatService)과 같은 규칙이다. 진행자 판정만 LiveShow.hostId로 한다.
 * 새 메시지는 라이브 SSE(show-chat)로 보낸다. 경매 SSE(auction-chat)로는 보내지 않는다.
 */
@Service
@RequiredArgsConstructor
public class LiveShowChatService {

	static final int DEFAULT_HISTORY_SIZE = 50;
	static final int MAX_HISTORY_SIZE = 100;
	private static final String RATE_LIMIT_KEY_PREFIX = "chat:rate:show:";

	private final LiveShowRepository showRepository;
	private final LiveShowLotRepository lotRepository;
	private final ChatMessageRepository chatMessageRepository;
	private final MemberRepository memberRepository;
	private final LiveShowSseRegistry sseRegistry;
	private final FixedWindowRateLimiter rateLimiter;
	private final LiveChatProperties properties;

	/**
	 * 방송 중(LIVE)인 라이브에만 보낼 수 있다. 보낸 사람과 진행자 여부는 검증된 회원 ID로 서버가 정한다.
	 * 트랜잭션을 걸지 않는다. save()가 커밋된 뒤에 알려야 조회 API와 어긋나지 않는다.
	 */
	public ChatMessageResponse send(Long showId, Long senderId, String content) {
		LiveShow show = showRepository.findById(showId).orElseThrow(() -> new LiveShowNotFoundException(showId));
		if (!show.isLive()) {
			throw new LiveShowConflictException("방송 중인 라이브에서만 채팅을 보낼 수 있습니다. 현재 상태: " + show.getStatus());
		}

		Member sender = memberRepository.findById(senderId).orElseThrow(ChatAuthRequiredException::new);
		if (!sender.hasNickname()) {
			throw new ChatNicknameRequiredException();
		}

		enforceRateLimit(showId, senderId);

		ChatMessage saved = chatMessageRepository.save(ChatMessage.ofShow(
				showId, currentAuctionId(show), senderId, sender.getNickname(), show.isHost(senderId), content,
				LocalDateTime.now()));

		ChatMessageResponse response = ChatMessageResponse.from(saved);
		sseRegistry.broadcastEvent(showId, LiveShowSseEvents.CHAT, response);
		return response;
	}

	/** 최근 대화를 오래된 것부터 돌려준다. beforeId가 있으면 그보다 오래된 메시지만 본다. 비로그인도 볼 수 있다. */
	@Transactional(readOnly = true)
	public List<ChatMessageResponse> getMessages(Long showId, Long beforeId, Integer size) {
		if (!showRepository.existsById(showId)) {
			throw new LiveShowNotFoundException(showId);
		}
		Pageable limit = PageRequest.of(0, clampSize(size));
		List<ChatMessage> newestFirst = beforeId == null
				? chatMessageRepository.findByShowIdOrderByIdDesc(showId, limit)
				: chatMessageRepository.findByShowIdAndIdLessThanOrderByIdDesc(showId, beforeId, limit);

		List<ChatMessage> oldestFirst = new ArrayList<>(newestFirst);
		Collections.reverse(oldestFirst);
		return oldestFirst.stream().map(ChatMessageResponse::from).toList();
	}

	/** 보낸 시점의 현재 작품 경매. 첫 작품을 열기 전이면 1번 작품의 경매(설계 원본) */
	private Long currentAuctionId(LiveShow show) {
		if (show.getCurrentLotId() != null) {
			return lotRepository.findById(show.getCurrentLotId())
					.map(LiveShowLot::getAuctionId)
					.orElseGet(() -> firstAuctionId(show));
		}
		return firstAuctionId(show);
	}

	private Long firstAuctionId(LiveShow show) {
		return lotRepository.findByShowIdOrderByLotNo(show.getId()).stream()
				.findFirst()
				.map(LiveShowLot::getAuctionId)
				.orElseThrow(() -> new LiveShowConflictException("작품이 없는 라이브에는 채팅을 보낼 수 없습니다."));
	}

	private void enforceRateLimit(Long showId, Long senderId) {
		String key = RATE_LIMIT_KEY_PREFIX + showId + ":" + senderId;
		if (rateLimiter.increment(key, properties.getRateLimitWindow()) > properties.getRateLimit()) {
			throw new ChatRateLimitExceededException();
		}
	}

	private int clampSize(Integer size) {
		if (size == null) {
			return DEFAULT_HISTORY_SIZE;
		}
		return Math.max(1, Math.min(size, MAX_HISTORY_SIZE));
	}
}
