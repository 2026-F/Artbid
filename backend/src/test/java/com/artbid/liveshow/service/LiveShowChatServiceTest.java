package com.artbid.liveshow.service;

import com.artbid.chat.config.LiveChatProperties;
import com.artbid.chat.domain.ChatMessage;
import com.artbid.chat.dto.ChatMessageResponse;
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
import com.artbid.member.domain.MemberRole;
import com.artbid.member.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class LiveShowChatServiceTest {

	private static final Long SHOW_ID = 1L;
	private static final Long HOST_ID = 100L;
	private static final Long VIEWER_ID = 200L;

	private LiveShowRepository showRepository;
	private LiveShowLotRepository lotRepository;
	private ChatMessageRepository chatMessageRepository;
	private MemberRepository memberRepository;
	private LiveShowSseRegistry sseRegistry;
	private FixedWindowRateLimiter rateLimiter;
	private LiveShowChatService service;

	@BeforeEach
	void setUp() {
		showRepository = mock(LiveShowRepository.class);
		lotRepository = mock(LiveShowLotRepository.class);
		chatMessageRepository = mock(ChatMessageRepository.class);
		memberRepository = mock(MemberRepository.class);
		sseRegistry = mock(LiveShowSseRegistry.class);
		rateLimiter = mock(FixedWindowRateLimiter.class);
		service = new LiveShowChatService(showRepository, lotRepository, chatMessageRepository, memberRepository,
				sseRegistry, rateLimiter, new LiveChatProperties());

		when(rateLimiter.increment(anyString(), any())).thenReturn(1L);
		when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> {
			ChatMessage message = invocation.getArgument(0);
			ReflectionTestUtils.setField(message, "id", 9L);
			return message;
		});
		List<LiveShowLot> lots = List.of(lot(11L, 1001L, 1), lot(12L, 1002L, 2));
		when(lotRepository.findByShowIdOrderByLotNo(SHOW_ID)).thenReturn(lots);
		lots.forEach(lot -> when(lotRepository.findById(lot.getId())).thenReturn(Optional.of(lot)));
		givenMember(HOST_ID, "진행자");
		givenMember(VIEWER_ID, "그림좋아");
	}

	private LiveShowLot lot(Long id, Long auctionId, int lotNo) {
		LiveShowLot lot = LiveShowLot.of(SHOW_ID, auctionId, lotNo, 5);
		ReflectionTestUtils.setField(lot, "id", id);
		return lot;
	}

	private void givenMember(Long memberId, String nickname) {
		Member member = Member.signUp("m" + memberId + "@example.com", "hashed", "실명", MemberRole.BIDDER);
		member.updateNickname(nickname);
		when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
	}

	private LiveShow show(boolean live) {
		LiveShow show = LiveShow.create(HOST_ID, "특별전", null, null, LocalDateTime.now());
		ReflectionTestUtils.setField(show, "id", SHOW_ID);
		if (live) {
			show.start("arn:stage", LocalDateTime.now());
		}
		when(showRepository.findById(SHOW_ID)).thenReturn(Optional.of(show));
		when(showRepository.existsById(SHOW_ID)).thenReturn(true);
		return show;
	}

	@Test
	void 첫_작품을_열기_전에는_1번_작품_경매로_저장하고_show_chat으로_보낸다() {
		show(true);

		ChatMessageResponse response = service.send(SHOW_ID, VIEWER_ID, " 안녕하세요 ");

		assertThat(response.showId()).isEqualTo(SHOW_ID);
		assertThat(response.auctionId()).isEqualTo(1001L);
		assertThat(response.content()).isEqualTo("안녕하세요");
		assertThat(response.host()).isFalse();
		verify(sseRegistry).broadcastEvent(SHOW_ID, LiveShowSseEvents.CHAT, response);
	}

	@Test
	void 작품이_바뀌면_현재_작품_경매로_저장하고_같은_라이브_대화로_이어진다() {
		LiveShow show = show(true);
		show.moveTo(12L);

		ChatMessageResponse response = service.send(SHOW_ID, HOST_ID, "두 번째 작품입니다");

		assertThat(response.auctionId()).isEqualTo(1002L);
		assertThat(response.showId()).isEqualTo(SHOW_ID);
		assertThat(response.host()).isTrue();
	}

	@Test
	void 방송_중이_아니면_보낼_수_없다() {
		show(false);

		assertThatThrownBy(() -> service.send(SHOW_ID, VIEWER_ID, "hi")).isInstanceOf(LiveShowConflictException.class);
		verify(chatMessageRepository, never()).save(any());
	}

	@Test
	void 닉네임이_없거나_너무_빨리_보내면_막는다() {
		show(true);
		givenMember(VIEWER_ID, null);
		assertThatThrownBy(() -> service.send(SHOW_ID, VIEWER_ID, "hi"))
				.isInstanceOf(ChatNicknameRequiredException.class);

		when(rateLimiter.increment(eq("chat:rate:show:1:100"), any())).thenReturn(99L);
		assertThatThrownBy(() -> service.send(SHOW_ID, HOST_ID, "hi"))
				.isInstanceOf(ChatRateLimitExceededException.class);
		verify(chatMessageRepository, never()).save(any());
	}

	@Test
	void 빈_내용과_200자_초과는_거절한다() {
		show(true);

		assertThatThrownBy(() -> service.send(SHOW_ID, VIEWER_ID, "   ")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> service.send(SHOW_ID, VIEWER_ID, "a".repeat(201)))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void 대화는_라이브_기준으로_오래된_것부터_돌려준다() {
		show(true);
		ChatMessage newer = ChatMessage.ofShow(SHOW_ID, 1002L, VIEWER_ID, "그림좋아", false, "둘", LocalDateTime.now());
		ChatMessage older = ChatMessage.ofShow(SHOW_ID, 1001L, VIEWER_ID, "그림좋아", false, "하나", LocalDateTime.now());
		when(chatMessageRepository.findByShowIdOrderByIdDesc(eq(SHOW_ID), any(Pageable.class)))
				.thenReturn(List.of(newer, older));

		List<ChatMessageResponse> messages = service.getMessages(SHOW_ID, null, null);

		assertThat(messages).extracting(ChatMessageResponse::content).containsExactly("하나", "둘");
	}

	@Test
	void 없는_라이브의_대화는_404() {
		when(showRepository.existsById(99L)).thenReturn(false);

		assertThatThrownBy(() -> service.getMessages(99L, null, null)).isInstanceOf(LiveShowNotFoundException.class);
	}
}
