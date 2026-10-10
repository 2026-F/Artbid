package com.artbid.streaming.controller;

import com.artbid.streaming.dto.LivestreamResponse;
import com.artbid.streaming.dto.StageTokenResponse;
import com.artbid.streaming.service.StreamingService;
import com.artbid.streaming.service.StreamingTokenService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/auctions/{auctionId}/stream")
@RequiredArgsConstructor
public class StreamingController {

	private final StreamingService streamingService;
	private final StreamingTokenService streamingTokenService;

	/** 스테이지를 만들고 방송을 LIVE로 연다. */
	@PostMapping("/start")
	@ResponseStatus(HttpStatus.CREATED)
	public LivestreamResponse startStream(@PathVariable Long auctionId) {
		return LivestreamResponse.from(streamingService.startStream(auctionId));
	}

	/** 스테이지를 삭제하고 방송을 종료한다. 이미 끝난 방송이어도 204를 돌려준다. */
	@PostMapping("/end")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void endStream(@PathVariable Long auctionId) {
		streamingService.endStream(auctionId);
	}

	/** 방송 상태 조회. 비로그인 시청자도 호출한다. */
	@GetMapping
	public LivestreamResponse getStream(@PathVariable Long auctionId) {
		return LivestreamResponse.from(streamingService.getStream(auctionId));
	}

	/**
	 * 스테이지 참여 토큰 발급. 로그인은 선택이다 — 비로그인이면 시청 토큰을 받는다.
	 * 위탁자 본인이면 송출 토큰을, 그 외에는 시청 토큰을 받는다.
	 */
	@PostMapping("/tokens")
	public StageTokenResponse issueToken(@PathVariable Long auctionId, Principal principal, HttpServletRequest request) {
		return streamingTokenService.issueToken(auctionId, memberId(principal), clientIp(request));
	}

	/** 로그인하지 않았으면 null — 이 API는 비로그인 시청을 허용하므로 에러로 취급하지 않는다. */
	private Long memberId(Principal principal) {
		if (principal == null) {
			return null;
		}
		try {
			return Long.parseLong(principal.getName());
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/** 로드밸런서·프록시를 거치면 X-Forwarded-For에 실제 클라이언트 IP가 먼저 온다. */
	private String clientIp(HttpServletRequest request) {
		String forwardedFor = request.getHeader("X-Forwarded-For");
		if (forwardedFor != null && !forwardedFor.isBlank()) {
			return forwardedFor.split(",")[0].trim();
		}
		return request.getRemoteAddr();
	}
}
