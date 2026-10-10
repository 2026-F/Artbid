package com.artbid.streaming.controller;

import com.artbid.streaming.dto.LivestreamResponse;
import com.artbid.streaming.exception.StreamingAuthRequiredException;
import com.artbid.streaming.service.StreamingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/auctions/{auctionId}/stream")
@RequiredArgsConstructor
public class StreamingController {

	private final StreamingService streamingService;

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

	/** 위탁자 클라이언트가 스테이지 연결이 끊긴 걸 감지하고 알려온다. 본인(위탁자)만 호출할 수 있다. */
	@PostMapping("/disconnect")
	public LivestreamResponse reportDisconnected(@PathVariable Long auctionId, Principal principal) {
		return LivestreamResponse.from(streamingService.reportDisconnected(auctionId, requireMemberId(principal)));
	}

	/** 끊겼던 위탁자 클라이언트가 같은 스테이지로 다시 붙었음을 알려온다. */
	@PostMapping("/reconnect")
	public LivestreamResponse reconnect(@PathVariable Long auctionId, Principal principal) {
		return LivestreamResponse.from(streamingService.reconnect(auctionId, requireMemberId(principal)));
	}

	/** /disconnect, /reconnect는 위탁자 본인 확인이 필요해 비로그인을 허용하지 않는다. */
	private Long requireMemberId(Principal principal) {
		if (principal != null) {
			try {
				return Long.parseLong(principal.getName());
			} catch (NumberFormatException ignored) {
				// 아래에서 인증 필요 예외로 처리
			}
		}
		throw new StreamingAuthRequiredException();
	}
}
