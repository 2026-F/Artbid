package com.artbid.settlement.controller;

import com.artbid.settlement.domain.Settlement;
import com.artbid.settlement.service.SettlementService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settlements")
@RequiredArgsConstructor
public class SettlementController {

	private final SettlementService settlementService;

	@GetMapping("/{id}")
	public Settlement getSettlement(@PathVariable Long id) {
		return settlementService.getSettlement(id);
	}
}
