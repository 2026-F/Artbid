package com.artbid.settlement.service;

import com.artbid.artwork.repository.ArtworkRepository;
import com.artbid.auction.repository.AuctionRepository;
import com.artbid.member.domain.MemberRole;
import com.artbid.settlement.domain.Settlement;
import com.artbid.settlement.exception.SettlementAccessDeniedException;
import com.artbid.settlement.exception.SettlementNotFoundException;
import com.artbid.settlement.repository.SettlementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class SettlementService {

	private final SettlementRepository settlementRepository;
	private final AuctionRepository auctionRepository;
	private final ArtworkRepository artworkRepository;

	public Settlement getSettlement(Long id, Long memberId, MemberRole role) {
		Settlement settlement = settlementRepository.findById(id)
				.orElseThrow(SettlementNotFoundException::new);
		checkReadable(settlement, memberId, role);
		return settlement;
	}

	public Settlement getSettlementByAuctionId(Long auctionId, Long memberId, MemberRole role) {
		Settlement settlement = settlementRepository.findByAuctionId(auctionId)
				.orElseThrow(SettlementNotFoundException::new);
		checkReadable(settlement, memberId, role);
		return settlement;
	}

	// 낙찰자 본인, 해당 작품의 위탁자, 관리자만 볼 수 있다. 역할만으로는 허용하지 않고 실제 당사자인지 확인한다.
	private void checkReadable(Settlement settlement, Long memberId, MemberRole role) {
		if (role == MemberRole.ADMIN || Objects.equals(settlement.getWinnerId(), memberId)) {
			return;
		}
		boolean isConsignor = auctionRepository.findById(settlement.getAuctionId())
				.flatMap(auction -> artworkRepository.findById(auction.getArtworkId()))
				.map(artwork -> Objects.equals(artwork.getConsignorId(), memberId))
				.orElse(false);
		if (!isConsignor) {
			throw new SettlementAccessDeniedException();
		}
	}
}
