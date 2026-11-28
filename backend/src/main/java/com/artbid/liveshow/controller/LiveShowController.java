package com.artbid.liveshow.controller;

import com.artbid.chat.dto.ChatMessageRequest;
import com.artbid.chat.dto.ChatMessageResponse;
import com.artbid.liveshow.domain.LiveShowStatus;
import com.artbid.liveshow.service.LiveShowChatService;
import com.artbid.liveshow.service.LiveShowTokenService;
import com.artbid.streaming.dto.StageTokenResponse;
import jakarta.validation.Valid;
import com.artbid.liveshow.dto.LiveShowDtos.CreateRequest;
import com.artbid.liveshow.dto.LiveShowDtos.DetailResponse;
import com.artbid.liveshow.dto.LiveShowDtos.SummaryResponse;
import com.artbid.liveshow.exception.LiveShowAuthRequiredException;
import com.artbid.liveshow.service.LiveShowService;
import com.artbid.member.auth.JwtAuthenticationFilter;
import com.artbid.member.domain.MemberRole;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

/**
 * 여러 작품을 차례로 진행하는 라이브 편성 API.
 * 조회는 누구나, 편성은 위탁자, 시작/다음 작품/종료는 그 라이브의 진행자만 할 수 있다.
 */
@RestController
@RequestMapping("/api/live-shows")
@RequiredArgsConstructor
public class LiveShowController {

	private final LiveShowService liveShowService;
	private final LiveShowTokenService tokenService;
	private final LiveShowChatService chatService;

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public DetailResponse create(@RequestBody CreateRequest request, Principal principal, HttpServletRequest httpRequest) {
		Long memberId = requireMemberId(principal);
		Object role = httpRequest.getAttribute(JwtAuthenticationFilter.ROLE_ATTRIBUTE);
		return liveShowService.create(memberId, role instanceof MemberRole memberRole ? memberRole : null, request);
	}

	/** 상태를 주지 않으면 방송 중(LIVE)인 라이브를 돌려준다. */
	@GetMapping
	public List<SummaryResponse> list(@RequestParam(required = false) LiveShowStatus status) {
		return liveShowService.list(status);
	}

	@GetMapping("/{showId}")
	public DetailResponse get(@PathVariable Long showId) {
		return liveShowService.getDetail(showId);
	}

	@PostMapping("/{showId}/start")
	public DetailResponse start(@PathVariable Long showId, Principal principal) {
		return liveShowService.start(showId, requireMemberId(principal));
	}

	@PostMapping("/{showId}/lots/next")
	public DetailResponse openNextLot(@PathVariable Long showId, Principal principal) {
		return liveShowService.openNextLot(showId, requireMemberId(principal));
	}

	@PostMapping("/{showId}/end")
	public DetailResponse end(@PathVariable Long showId, Principal principal) {
		return liveShowService.end(showId, requireMemberId(principal));
	}

	/** 스테이지 참여 토큰. 로그인은 선택이다. 진행자는 송출 토큰, 그 외(비로그인 포함)는 시청 토큰을 받는다. */
	@PostMapping("/{showId}/stream/tokens")
	public StageTokenResponse issueToken(@PathVariable Long showId, Principal principal, HttpServletRequest request) {
		return tokenService.issueToken(showId, optionalMemberId(principal), clientIp(request));
	}

	/** 최근 대화 조회. 비로그인도 볼 수 있다. 새 메시지는 SSE(show-chat)로 받는다. */
	@GetMapping("/{showId}/chat/messages")
	public List<ChatMessageResponse> getMessages(@PathVariable Long showId,
			@RequestParam(required = false) Long beforeId,
			@RequestParam(required = false) Integer size) {
		return chatService.getMessages(showId, beforeId, size);
	}

	/** 채팅 전송. 로그인한 회원만 보낼 수 있다. */
	@PostMapping("/{showId}/chat/messages")
	@ResponseStatus(HttpStatus.CREATED)
	public ChatMessageResponse sendMessage(@PathVariable Long showId, @Valid @RequestBody ChatMessageRequest request,
			Principal principal) {
		return chatService.send(showId, requireMemberId(principal), request.content());
	}

	/** 로그인하지 않았으면 null. 토큰 API는 비로그인 시청을 허용한다(경매 토큰과 같음). */
	private Long optionalMemberId(Principal principal) {
		if (principal == null) {
			return null;
		}
		try {
			long id = Long.parseLong(principal.getName());
			return id > 0 ? id : null;
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/** 로드밸런서, 프록시를 거치면 X-Forwarded-For에 실제 클라이언트 IP가 먼저 온다(StreamingController와 같음). */
	private String clientIp(HttpServletRequest request) {
		String forwardedFor = request.getHeader("X-Forwarded-For");
		if (forwardedFor != null && !forwardedFor.isBlank()) {
			return forwardedFor.split(",")[0].trim();
		}
		return request.getRemoteAddr();
	}

	private Long requireMemberId(Principal principal) {
		if (principal != null) {
			try {
				return Long.parseLong(principal.getName());
			} catch (NumberFormatException ignored) {
				// 아래에서 인증 필요 예외로 처리
			}
		}
		throw new LiveShowAuthRequiredException();
	}
}
