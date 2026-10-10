package com.artbid.settlement.controller;

import com.artbid.settlement.domain.Settlement;
import com.artbid.settlement.service.SettlementService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/settlements")
@RequiredArgsConstructor
public class SettlementController {

	private final SettlementService settlementService;

	@GetMapping("/{id}")
	public Settlement getSettlement(@PathVariable Long id) {
		return settlementService.getSettlement(id);
	}

	@GetMapping(params = "auctionId")
	public Settlement getSettlementByAuctionId(@RequestParam Long auctionId) {
		return settlementService.getSettlementByAuctionId(auctionId);
	}
}
