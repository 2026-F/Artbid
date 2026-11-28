package com.artbid.liveshow.controller;

import com.artbid.liveshow.domain.LiveShowStatus;
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
