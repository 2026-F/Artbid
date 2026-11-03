package com.artbid.auction.controller;

import com.artbid.auction.service.BidService;
import com.artbid.common.exception.BidAuthRequiredException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/auctions")
@RequiredArgsConstructor
public class BidController {

	private final BidService bidService;

	@PostMapping("/{auctionId}/bids")
	public BidService.BidResult submitBid(@PathVariable Long auctionId, @Valid @RequestBody BidRequest request,
			Principal principal) {
		return bidService.submitBid(auctionId, memberId(principal), request.price());
	}

	@DeleteMapping("/{auctionId}/bids")
	public BidService.BidResult cancelBid(@PathVariable Long auctionId, Principal principal) {
		return bidService.cancelBid(auctionId, memberId(principal));
	}

	@GetMapping("/{auctionId}/bids")
	public List<BidService.BidResponse> getBids(@PathVariable Long auctionId){
		return bidService.getBids(auctionId);
	}

	// Authentication 연동 계약: Principal.name에는 검증된 Member ID를 사용한다 (PaymentController·StreamingController와 같은 방식).
	// 이전에는 요청 바디의 bidderId를 그대로 믿었는데, 그러면 로그인한 사람과 상관없이 바디에 적은 아무 회원 ID로
	// 입찰/취소가 가능해지는 문제가 있었다. 그래서 반드시 검증된 토큰에서만 회원 ID를 가져오도록 고쳤다.
	private Long memberId(Principal principal) {
		if (principal != null) {
			try {
				long id = Long.parseLong(principal.getName());
				if (id > 0) return id;
			} catch (NumberFormatException ignored) {
				// 아래에서 인증 필요 예외로 처리
			}
		}
		throw new BidAuthRequiredException();
	}

	public record BidRequest(@NotNull Long price) {
	}
}
