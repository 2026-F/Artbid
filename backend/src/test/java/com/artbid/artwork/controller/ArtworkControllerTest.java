package com.artbid.artwork.controller;

import com.artbid.artist.exception.ArtistNotFoundException;
import com.artbid.artwork.domain.ArtworkCategory;
import com.artbid.artwork.domain.ArtworkStatus;
import com.artbid.artwork.dto.ArtworkCreateRequest;
import com.artbid.artwork.dto.ArtworkDetailResponse;
import com.artbid.artwork.dto.ArtworkPageResponse;
import com.artbid.artwork.dto.ArtworkSummaryResponse;
import com.artbid.artwork.dto.ArtworkUpdateRequest;
import com.artbid.artwork.exception.ArtworkNotEditableException;
import com.artbid.artwork.exception.ArtworkNotFoundException;
import com.artbid.artwork.exception.ArtworkForbiddenException;
import com.artbid.artwork.repository.ArtworkSearchCondition;
import com.artbid.artwork.service.ArtworkService;
import com.artbid.common.exception.GlobalExceptionHandler;
import com.artbid.member.auth.JwtAuthenticationFilter;
import com.artbid.member.domain.MemberRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ArtworkControllerTest {

	// consignorId는 본문에 있어도 무시되고 토큰의 회원 ID로 등록되어야 한다.
	private static final String CREATE_BODY = """
			{"consignorId":999,"artistId":2,"title":"무제","category":"PAINTING","startPrice":1000000}
			""";

	private ArtworkService service;
	private MockMvc mvc;

	@BeforeEach
	void setup() {
		service = mock(ArtworkService.class);
		mvc = MockMvcBuilders.standaloneSetup(new ArtworkController(service))
				.setControllerAdvice(new GlobalExceptionHandler(), new ArtworkExceptionHandler()).build();
	}

	// JwtAuthenticationFilter가 토큰 검증 후 남기는 Principal과 역할 속성을 흉내 낸다.
	private static RequestPostProcessor loginAs(long memberId, MemberRole role) {
		return request -> {
			request.setUserPrincipal(() -> String.valueOf(memberId));
			request.setAttribute(JwtAuthenticationFilter.ROLE_ATTRIBUTE, role);
			return request;
		};
	}

	private ArtworkDetailResponse detail(String title) {
		LocalDateTime now = LocalDateTime.now();
		return new ArtworkDetailResponse(1L, 1L, new ArtworkDetailResponse.ArtistSummary(2L, "김작가", null), title,
				null, ArtworkCategory.PAINTING, null, null, null, null, null, 1_000_000L, null, null, null,
				ArtworkStatus.PENDING_REVIEW, List.of(), now, now, null);
	}

	@Test
	void 작품을_등록하면_201과_Location을_반환한다() throws Exception {
		when(service.register(eq(1L), any(ArtworkCreateRequest.class))).thenReturn(detail("무제"));

		mvc.perform(post("/api/artworks").with(loginAs(1L, MemberRole.CONSIGNOR))
						.contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", "/api/artworks/1"))
				.andExpect(jsonPath("$.artist.name").value("김작가"))
				.andExpect(jsonPath("$.status").value("PENDING_REVIEW"));

		// 본문의 consignorId(999)가 아니라 로그인한 회원(1)으로 등록한다.
		verify(service).register(eq(1L), any(ArtworkCreateRequest.class));
	}

	@Test
	void 비로그인으로_작품을_등록하면_401을_반환한다() throws Exception {
		mvc.perform(post("/api/artworks").contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
		verifyNoInteractions(service);
	}

	@Test
	void 판매자가_아닌_회원이_작품을_등록하면_403을_반환한다() throws Exception {
		mvc.perform(post("/api/artworks").with(loginAs(3L, MemberRole.BIDDER))
						.contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ARTWORK_FORBIDDEN"));
		verifyNoInteractions(service);
	}

	@Test
	void 필수값이_없으면_400을_반환한다() throws Exception {
		mvc.perform(post("/api/artworks").with(loginAs(1L, MemberRole.CONSIGNOR)).contentType(MediaType.APPLICATION_JSON)
						.content("{\"artistId\":2,\"title\":\"무제\",\"category\":\"PAINTING\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		verifyNoInteractions(service);
	}

	@Test
	void 없는_작가로_등록하면_404를_반환한다() throws Exception {
		when(service.register(eq(1L), any(ArtworkCreateRequest.class))).thenThrow(new ArtistNotFoundException(2L));

		mvc.perform(post("/api/artworks").with(loginAs(1L, MemberRole.CONSIGNOR))
						.contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("ARTIST_NOT_FOUND"));
	}

	@Test
	void 필터를_검색_조건과_정렬로_변환해_조회한다() throws Exception {
		ArtworkSummaryResponse item = new ArtworkSummaryResponse(1L, "무제", 2L, "김작가", ArtworkCategory.PAINTING,
				null, 1_000_000L, null, ArtworkStatus.PREVIEW, 3L);
		when(service.getArtworks(any(), any())).thenReturn(new ArtworkPageResponse(List.of(item), 0, 20, 1, 1));

		mvc.perform(get("/api/artworks").param("keyword", "김").param("category", "PAINTING")
						.param("minPrice", "100").param("maxPrice", "2000000").param("sort", "priceAsc"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].artistName").value("김작가"))
				.andExpect(jsonPath("$.totalElements").value(1));

		ArgumentCaptor<ArtworkSearchCondition> condition = ArgumentCaptor.forClass(ArtworkSearchCondition.class);
		ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
		verify(service).getArtworks(condition.capture(), pageable.capture());

		assertThat(condition.getValue().keyword()).isEqualTo("김");
		assertThat(condition.getValue().category()).isEqualTo(ArtworkCategory.PAINTING);
		assertThat(condition.getValue().statuses()).isEqualTo(ArtworkStatus.PUBLIC);
		assertThat(condition.getValue().minPrice()).isEqualTo(100L);
		assertThat(pageable.getValue().getSort().getOrderFor("startPrice").getDirection())
				.isEqualTo(Sort.Direction.ASC);
	}

	@Test
	void status를_지정하면_해당_상태만_조회한다() throws Exception {
		when(service.getArtworks(any(), any())).thenReturn(new ArtworkPageResponse(List.of(), 0, 20, 0, 0));

		mvc.perform(get("/api/artworks").param("status", "PENDING_REVIEW")).andExpect(status().isOk());

		ArgumentCaptor<ArtworkSearchCondition> condition = ArgumentCaptor.forClass(ArtworkSearchCondition.class);
		verify(service).getArtworks(condition.capture(), any());
		assertThat(condition.getValue().statuses()).isEqualTo(Set.of(ArtworkStatus.PENDING_REVIEW));
	}

	@Test
	void 잘못된_필터값은_400을_반환한다() throws Exception {
		mvc.perform(get("/api/artworks").param("category", "UNKNOWN")).andExpect(status().isBadRequest());
		mvc.perform(get("/api/artworks").param("sort", "popular")).andExpect(status().isBadRequest());
		mvc.perform(get("/api/artworks").param("minPrice", "10").param("maxPrice", "1"))
				.andExpect(status().isBadRequest());
		verifyNoInteractions(service);
	}

	@Test
	void 없는_작품을_조회하면_404를_반환한다() throws Exception {
		when(service.getArtwork(99L)).thenThrow(new ArtworkNotFoundException(99L));

		mvc.perform(get("/api/artworks/99"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("ARTWORK_NOT_FOUND"));
	}

	@Test
	void 수정할_수_없는_상태면_409를_반환한다() throws Exception {
		when(service.update(eq(1L), eq(1L), eq(false), any(ArtworkUpdateRequest.class)))
				.thenThrow(new ArtworkNotEditableException(1L, ArtworkStatus.IN_AUCTION));

		mvc.perform(patch("/api/artworks/1").with(loginAs(1L, MemberRole.CONSIGNOR)).contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"새 제목\"}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("ARTWORK_NOT_EDITABLE"));
	}

	@Test
	void 작품_정보를_부분_수정한다() throws Exception {
		when(service.update(eq(1L), eq(1L), eq(false), any(ArtworkUpdateRequest.class))).thenReturn(detail("새 제목"));

		mvc.perform(patch("/api/artworks/1").with(loginAs(1L, MemberRole.CONSIGNOR))
						.contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"새 제목\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.title").value("새 제목"));
	}

	@Test
	void 비로그인으로_작품을_수정하면_401을_반환한다() throws Exception {
		mvc.perform(patch("/api/artworks/1").contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"새 제목\"}"))
				.andExpect(status().isUnauthorized());
		verifyNoInteractions(service);
	}

	@Test
	void 남의_작품을_수정하면_403을_반환한다() throws Exception {
		when(service.update(eq(1L), eq(5L), eq(false), any(ArtworkUpdateRequest.class)))
				.thenThrow(new ArtworkForbiddenException("작품을 등록한 위탁자만 수정할 수 있습니다."));

		mvc.perform(patch("/api/artworks/1").with(loginAs(5L, MemberRole.CONSIGNOR))
						.contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"새 제목\"}"))
				.andExpect(status().isForbidden());
	}
}
