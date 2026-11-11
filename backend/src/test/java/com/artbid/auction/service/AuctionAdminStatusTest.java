package com.artbid.auction.service;

import com.artbid.auction.domain.Auction;
import com.artbid.auction.domain.AuctionStatus;
import com.artbid.auction.live.AuctionLiveReason;
import com.artbid.auction.live.AuctionLiveService;
import com.artbid.auction.repository.AuctionRepository;
import com.artbid.member.auth.JwtAuthenticationFilter;
import com.artbid.member.domain.MemberRole;
import com.artbid.settlement.repository.SettlementRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuctionAdminStatusTest {

	@Autowired AuctionRepository auctions;
	@Autowired SettlementRepository settlements;
	@Autowired AuctionService auctionService;
	@Autowired AuctionLiveService liveService;
	@Autowired TransactionTemplate transaction;
	@Autowired MockMvc mvc;

	private Long auctionId;

	@AfterEach
	void deleteAuction() {
		auctions.deleteById(auctionId);
	}

	@Test
	void 수동_시작이_커밋된_후에만_캐시를_갱신한다() {
		createPreview(10);
		assertThat(liveService.getView(auctionId, AuctionLiveReason.SNAPSHOT).status()).isEqualTo(AuctionStatus.PREVIEW);

		transaction.executeWithoutResult(tx -> {
			auctionService.updateStatus(auctionId, AuctionStatus.ONGOING);
			assertThat(liveService.getView(auctionId, AuctionLiveReason.SNAPSHOT).status()).isEqualTo(AuctionStatus.PREVIEW);
			tx.setRollbackOnly();
		});

		assertThat(auctions.findById(auctionId).orElseThrow().getStatus()).isEqualTo(AuctionStatus.PREVIEW);
		assertThat(liveService.getView(auctionId, AuctionLiveReason.SNAPSHOT).status()).isEqualTo(AuctionStatus.PREVIEW);

		transaction.executeWithoutResult(tx -> auctionService.updateStatus(auctionId, AuctionStatus.ONGOING));

		assertThat(auctions.findById(auctionId).orElseThrow().getStatus()).isEqualTo(AuctionStatus.ONGOING);
		var live = liveService.getView(auctionId, AuctionLiveReason.SNAPSHOT);
		assertThat(live.status()).isEqualTo(AuctionStatus.ONGOING);
		assertThat(live.version()).isPositive();
	}

	@ParameterizedTest
	@ValueSource(ints = {-10, 10})
	void 마감_전후_직접_마감은_상태나_정산을_바꾸지_않는다(int minutesUntilEnd) throws Exception {
		createPreview(minutesUntilEnd);

		mvc.perform(patch("/api/auctions/{id}/status", auctionId)
				.requestAttr(JwtAuthenticationFilter.ROLE_ATTRIBUTE, MemberRole.ADMIN)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"CLOSED\"}"))
				.andExpect(status().isConflict());

		assertThat(auctions.findById(auctionId).orElseThrow().getStatus()).isEqualTo(AuctionStatus.PREVIEW);
		assertThat(settlements.findByAuctionId(auctionId)).isEmpty();
	}

	private void createPreview(int minutesUntilEnd) {
		LocalDateTime now = LocalDateTime.now();
		auctionId = auctions.save(Auction.builder()
				.artworkId(1L)
				.startPrice(10_000L)
				.currentPrice(10_000L)
				.minBidUnit(1_000L)
				.previewStart(now.minusHours(2))
				.previewEnd(now.minusHours(1))
				.auctionEndAt(now.plusMinutes(minutesUntilEnd))
				.status(AuctionStatus.PREVIEW)
				.build()).getId();
	}
}
