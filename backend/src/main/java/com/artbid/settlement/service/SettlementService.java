package com.artbid.settlement.service;

import com.artbid.settlement.domain.Settlement;
import com.artbid.settlement.repository.SettlementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SettlementService {

	private final SettlementRepository settlementRepository;

	public Settlement getSettlement(Long id) {
		return settlementRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("정산 내역을 찾을 수 없습니다: " + id));
	}
}
