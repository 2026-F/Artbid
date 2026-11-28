package com.artbid.chat.service;

import com.artbid.artwork.domain.Artwork;
import com.artbid.artwork.repository.ArtworkRepository;
import com.artbid.auction.domain.Auction;
import com.artbid.auction.repository.AuctionRepository;
import com.artbid.chat.domain.ChatMessage;
import com.artbid.chat.dto.ChatMessageResponse;
import com.artbid.chat.exception.ChatAuthRequiredException;
import com.artbid.chat.config.LiveChatProperties;
import com.artbid.chat.exception.ChatNicknameRequiredException;
import com.artbid.chat.exception.ChatNotAvailableException;
import com.artbid.member.domain.MemberRole;
import com.artbid.chat.exception.ChatRateLimitExceededException;
import com.artbid.infra.redis.FixedWindowRateLimiter;
import java.time.Duration;
import com.artbid.chat.repository.ChatMessageRepository;
import com.artbid.infra.realtime.AuctionSseRegistry;
import com.artbid.member.domain.Member;
import com.artbid.member.repository.MemberRepository;
import com.artbid.streaming.domain.Livestream;
import com.artbid.streaming.repository.LivestreamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LiveChatServiceTest {

	private static final Long AUCTION_ID = 1L;
	private static final Long ARTWORK_ID = 10L;
	private static final Long CONSIGNOR_ID = 100L;
	private static final Long BIDDER_ID = 7L;
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 13, 20, 0);
	private static final String RATE_KEY = "chat:rate:1:7";

	private ChatMessageRepository chatMessageRepository;
	private LivestreamRepository livestreamRepository;
	private MemberRepository memberRepository;
	private AuctionRepository auctionRepository;
	private ArtworkRepository artworkRepository;
	private AuctionSseRegistry sseRegistry;
	private FixedWindowRateLimiter rateLimiter;
	private LiveChatProperties properties;
	private LiveChatService liveChatService;

	@BeforeEach
	void setup() {
		chatMessageRepository = mock(ChatMessageRepository.class);
		livestreamRepository = mock(LivestreamRepository.class);
		memberRepository = mock(MemberRepository.class);
		auctionRepository = mock(AuctionRepository.class);
		artworkRepository = mock(ArtworkRepository.class);
		sseRegistry = mock(AuctionSseRegistry.class);
		rateLimiter = mock(FixedWindowRateLimiter.class);
		when(rateLimiter.increment(RATE_KEY, Duration.ofSeconds(10))).thenReturn(1L);
		properties = new LiveChatProperties();
		liveChatService = new LiveChatService(chatMessageRepository, livestreamRepository, memberRepository,
				auctionRepository, artworkRepository, sseRegistry, rateLimiter, properties);

		when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));
	}

	private ChatMessage message(String content) {
		return ChatMessage.of(AUCTION_ID, BIDDER_ID, "홍길동", false, content, NOW);
	}

	private Livestream liveStream() {
		Livestream livestream = Livestream.builder().auctionId(AUCTION_ID).build();
		livestream.activate("arn:aws:ivs:ap-northeast-2:123456789012:stage/AbCdEf123456", NOW);
		return livestream;
	}

	/** 실명은 항상 "김실명"이고 닉네임만 바꿔 가며 만든다. nickname이 null이면 닉네임을 정하지 않은 회원이다. */
	private void givenMember(Long memberId, String nickname) {
		Member member = Member.signUp("member" + memberId + "@example.com", "hashed", "김실명", MemberRole.BIDDER);
		member.updateNickname(nickname);
		when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
	}

	private void givenConsignor() {
		Auction auction = mock(Auction.class);
		when(auction.getArtworkId()).thenReturn(ARTWORK_ID);
		when(auctionRepository.findById(AUCTION_ID)).thenReturn(Optional.of(auction));
		Artwork artwork = mock(Artwork.class);
		when(artwork.getConsignorId()).thenReturn(CONSIGNOR_ID);
		when(artworkRepository.findById(ARTWORK_ID)).thenReturn(Optional.of(artwork));
	}

	@Test
	void 방송_중이면_메시지를_저장하고_SSE로_알린다() {
		when(livestreamRepository.findByAuctionId(AUCTION_ID)).thenReturn(Optional.of(liveStream()));
		givenMember(BIDDER_ID, "홍길동");
		givenConsignor();

		ChatMessageResponse response = liveChatService.send(AUCTION_ID, BIDDER_ID, " 응원합니다 ");

		assertThat(response.content()).isEqualTo("응원합니다");
		assertThat(response.senderId()).isEqualTo(BIDDER_ID);
		assertThat(response.senderName()).isEqualTo("홍길동");
		assertThat(response.host()).isFalse();
		verify(chatMessageRepository).save(any(ChatMessage.class));
		verify(sseRegistry).broadcastEvent(AUCTION_ID, "auction-chat", response);
	}

	@Test
	void 위탁자가_보내면_진행자로_표시한다() {
		when(livestreamRepository.findByAuctionId(AUCTION_ID)).thenReturn(Optional.of(liveStream()));
		givenMember(CONSIGNOR_ID, "작가");
		givenConsignor();

		assertThat(liveChatService.send(AUCTION_ID, CONSIGNOR_ID, "시작합니다").host()).isTrue();
	}

	@Test
	void 실명이_아니라_닉네임으로_표시한다() {
		when(livestreamRepository.findByAuctionId(AUCTION_ID)).thenReturn(Optional.of(liveStream()));
		givenMember(BIDDER_ID, " 그림좋아 ");

		assertThat(liveChatService.send(AUCTION_ID, BIDDER_ID, "안녕하세요").senderName()).isEqualTo("그림좋아");
	}

	@Test
	void 닉네임을_정하지_않은_회원은_보낼_수_없다() {
		when(livestreamRepository.findByAuctionId(AUCTION_ID)).thenReturn(Optional.of(liveStream()));
		givenMember(BIDDER_ID, null);

		assertThatThrownBy(() -> liveChatService.send(AUCTION_ID, BIDDER_ID, "안녕하세요"))
				.isInstanceOf(ChatNicknameRequiredException.class);
		verify(chatMessageRepository, never()).save(any());
		verifyNoInteractions(sseRegistry, rateLimiter);
	}

	@Test
	void 연결이_잠시_끊긴_방송에도_보낼_수_있다() {
		Livestream livestream = liveStream();
		livestream.markDisconnected(NOW);
		when(livestreamRepository.findByAuctionId(AUCTION_ID)).thenReturn(Optional.of(livestream));
		givenMember(BIDDER_ID, "홍길동");

		assertThat(liveChatService.send(AUCTION_ID, BIDDER_ID, "기다릴게요").content()).isEqualTo("기다릴게요");
	}

	@Test
	void 방송이_없으면_보낼_수_없다() {
		when(livestreamRepository.findByAuctionId(AUCTION_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> liveChatService.send(AUCTION_ID, BIDDER_ID, "안녕하세요"))
				.isInstanceOf(ChatNotAvailableException.class);
		verify(chatMessageRepository, never()).save(any());
		verifyNoInteractions(sseRegistry);
	}

	@Test
	void 끝난_방송에는_보낼_수_없다() {
		Livestream livestream = liveStream();
		livestream.markEnded(NOW);
		when(livestreamRepository.findByAuctionId(AUCTION_ID)).thenReturn(Optional.of(livestream));

		assertThatThrownBy(() -> liveChatService.send(AUCTION_ID, BIDDER_ID, "안녕하세요"))
				.isInstanceOf(ChatNotAvailableException.class);
		verify(chatMessageRepository, never()).save(any());
	}

	@Test
	void 회원이_없으면_로그인_필요로_막는다() {
		when(livestreamRepository.findByAuctionId(AUCTION_ID)).thenReturn(Optional.of(liveStream()));
		when(memberRepository.findById(BIDDER_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> liveChatService.send(AUCTION_ID, BIDDER_ID, "안녕하세요"))
				.isInstanceOf(ChatAuthRequiredException.class);
		verify(chatMessageRepository, never()).save(any());
	}

	@Test
	void 회원과_경매별_키로_설정된_기간_동안_횟수를_센다() {
		when(livestreamRepository.findByAuctionId(AUCTION_ID)).thenReturn(Optional.of(liveStream()));
		givenMember(BIDDER_ID, "홍길동");
		when(rateLimiter.increment(RATE_KEY, Duration.ofSeconds(10))).thenReturn(1L, 2L);

		liveChatService.send(AUCTION_ID, BIDDER_ID, "하나");
		liveChatService.send(AUCTION_ID, BIDDER_ID, "둘");

		verify(rateLimiter, times(2)).increment(RATE_KEY, Duration.ofSeconds(10));
	}

	@Test
	void 제한_횟수를_넘으면_저장하지_않고_막는다() {
		when(livestreamRepository.findByAuctionId(AUCTION_ID)).thenReturn(Optional.of(liveStream()));
		givenMember(BIDDER_ID, "홍길동");
		when(rateLimiter.increment(RATE_KEY, Duration.ofSeconds(10))).thenReturn(6L);

		assertThatThrownBy(() -> liveChatService.send(AUCTION_ID, BIDDER_ID, "도배"))
				.isInstanceOf(ChatRateLimitExceededException.class);
		verify(chatMessageRepository, never()).save(any());
		verifyNoInteractions(sseRegistry);
	}

	@Test
	void 최근_대화를_오래된_것부터_돌려준다() {
		when(chatMessageRepository.findByAuctionIdAndShowIdIsNullOrderByIdDesc(AUCTION_ID, PageRequest.of(0, 50)))
				.thenReturn(List.of(message("나중"), message("먼저")));

		List<ChatMessageResponse> result = liveChatService.getMessages(AUCTION_ID, null, null);

		assertThat(result).extracting(ChatMessageResponse::content).containsExactly("먼저", "나중");
	}

	@Test
	void beforeId가_있으면_그보다_오래된_메시지만_조회한다() {
		when(chatMessageRepository.findByAuctionIdAndShowIdIsNullAndIdLessThanOrderByIdDesc(AUCTION_ID, 30L, PageRequest.of(0, 20)))
				.thenReturn(List.of(message("이전 대화")));

		List<ChatMessageResponse> result = liveChatService.getMessages(AUCTION_ID, 30L, 20);

		assertThat(result).hasSize(1);
		verify(chatMessageRepository, never()).findByAuctionIdAndShowIdIsNullOrderByIdDesc(any(), any());
	}

	@Test
	void 조회_개수는_1에서_100_사이로_맞춘다() {
		liveChatService.getMessages(AUCTION_ID, null, 5000);
		liveChatService.getMessages(AUCTION_ID, null, -3);

		verify(chatMessageRepository).findByAuctionIdAndShowIdIsNullOrderByIdDesc(AUCTION_ID, PageRequest.of(0, 100));
		verify(chatMessageRepository).findByAuctionIdAndShowIdIsNullOrderByIdDesc(AUCTION_ID, PageRequest.of(0, 1));
	}
}
