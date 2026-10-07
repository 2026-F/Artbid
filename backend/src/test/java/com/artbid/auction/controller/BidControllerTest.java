package com.artbid.auction.controller;

import com.artbid.auction.service.BidService;
import com.artbid.common.exception.BidTemporarilyUnavailableException;
import com.artbid.common.exception.GlobalExceptionHandler;
import com.artbid.common.exception.InvalidBidException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import java.nio.charset.StandardCharsets;

/**
 * BidController 단위 테스트.
 * 실제 DB/Spring 컨텍스트 없이 MockMvc standalone 모드로 띄우고,
 * BidService는 Mockito로 대체한다. GlobalExceptionHandler를 controllerAdvice로 등록해서
 * 서비스가 던지는 예외가 실제로 어떤 HTTP 상태코드로 변환되는지까지 검증한다.
 */
class BidControllerTest {

	@Mock
	private BidService bidService;

	private MockMvc mockMvc;
	private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		BidController controller = new BidController(bidService);
		mockMvc = MockMvcBuilders.standaloneSetup(controller)
				.setControllerAdvice(new GlobalExceptionHandler())
				.setMessageConverters(
						new StringHttpMessageConverter(StandardCharsets.UTF_8),
						new MappingJackson2HttpMessageConverter(objectMapper)
				)
				.build();
	}

	@Test
	void 입찰_제출_성공시_200과_갱신된_현재가를_반환한다() throws Exception {
		LocalDateTime endAt = LocalDateTime.now().plusMinutes(10);
		when(bidService.submitBid(anyLong(), anyLong(), anyLong()))
				.thenReturn(new BidService.BidResult(15_000L, endAt, false));

		String requestBody = objectMapper.writeValueAsString(new BidController.BidRequest(1L, 15_000L));

		mockMvc.perform(post("/api/auctions/{auctionId}/bids", 1L)
						.contentType(MediaType.APPLICATION_JSON)
						.content(requestBody))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.currentPrice").value(15_000))
				.andExpect(jsonPath("$.extended").value(false));
	}

	@Test
	void 입찰_제출시_서비스가_InvalidBidException을_던지면_409를_반환한다() throws Exception {
		when(bidService.submitBid(anyLong(), anyLong(), anyLong()))
				.thenThrow(new InvalidBidException("입찰가가 너무 낮습니다."));

		String requestBody = objectMapper.writeValueAsString(new BidController.BidRequest(1L, 1_000L));

		mockMvc.perform(post("/api/auctions/{auctionId}/bids", 1L)
						.contentType(MediaType.APPLICATION_JSON)
						.content(requestBody))
				.andExpect(status().isConflict())
				.andExpect(content().string("입찰가가 너무 낮습니다."));
	}

	@Test
	void 입찰_취소_성공시_200과_되돌아간_현재가를_반환한다() throws Exception {
		LocalDateTime endAt = LocalDateTime.now().plusMinutes(10);
		when(bidService.cancelBid(anyLong(), anyLong()))
				.thenReturn(new BidService.BidResult(12_000L, endAt, false));

		String requestBody = objectMapper.writeValueAsString(new BidController.BidCancelRequest(10L));

		mockMvc.perform(delete("/api/auctions/{auctionId}/bids", 1L)
						.contentType(MediaType.APPLICATION_JSON)
						.content(requestBody))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.currentPrice").value(12_000));
	}

	@Test
	void 취소할_입찰이_없으면_404를_반환한다() throws Exception {
		when(bidService.cancelBid(anyLong(), anyLong()))
				.thenThrow(new IllegalArgumentException("취소할 입찰이 없습니다."));

		String requestBody = objectMapper.writeValueAsString(new BidController.BidCancelRequest(10L));

		mockMvc.perform(delete("/api/auctions/{auctionId}/bids", 1L)
						.contentType(MediaType.APPLICATION_JSON)
						.content(requestBody))
				.andExpect(status().isNotFound())
				.andExpect(content().string("취소할 입찰이 없습니다."));
	}

	@Test
	void 이미_다른_입찰에_덮어써진_입찰을_취소하려하면_409를_반환한다() throws Exception {
		when(bidService.cancelBid(anyLong(), anyLong()))
				.thenThrow(new InvalidBidException("이미 다른 입찰에 덮어써진 입찰은 취소할 수 없습니다."));

		String requestBody = objectMapper.writeValueAsString(new BidController.BidCancelRequest(10L));

		mockMvc.perform(delete("/api/auctions/{auctionId}/bids", 1L)
						.contentType(MediaType.APPLICATION_JSON)
						.content(requestBody))
				.andExpect(status().isConflict())
				.andExpect(content().string("이미 다른 입찰에 덮어써진 입찰은 취소할 수 없습니다."));
	}

	@Test
	void 락_획득에_실패하면_503을_반환한다() throws Exception {
		when(bidService.cancelBid(anyLong(), anyLong()))
				.thenThrow(new BidTemporarilyUnavailableException());

		String requestBody = objectMapper.writeValueAsString(new BidController.BidCancelRequest(10L));

		mockMvc.perform(delete("/api/auctions/{auctionId}/bids", 1L)
						.contentType(MediaType.APPLICATION_JSON)
						.content(requestBody))
				.andExpect(status().isServiceUnavailable());
	}

	@Test
	void 입찰_내역_조회시_취소여부를_포함한_목록을_반환한다() throws Exception {
		LocalDateTime now = LocalDateTime.now();
		List<BidService.BidResponse> history = List.of(
				new BidService.BidResponse(2L, 10L, 15_000L, now, false, null),
				new BidService.BidResponse(1L, 20L, 12_000L, now.minusMinutes(1), true, now)
		);
		when(bidService.getBids(anyLong())).thenReturn(history);

		mockMvc.perform(get("/api/auctions/{auctionId}/bids", 1L))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].bidId").value(2))
				.andExpect(jsonPath("$[0].canceled").value(false))
				.andExpect(jsonPath("$[1].bidId").value(1))
				.andExpect(jsonPath("$[1].canceled").value(true));
	}
}
