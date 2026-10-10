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
import com.artbid.artwork.repository.ArtworkSearchCondition;
import com.artbid.artwork.service.ArtworkService;
import com.artbid.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
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

	private static final String CREATE_BODY = """
			{"consignorId":1,"artistId":2,"title":"무제","category":"PAINTING","startPrice":1000000}
			""";

	private ArtworkService service;
	private MockMvc mvc;

	@BeforeEach
	void setup() {
		service = mock(ArtworkService.class);
		mvc = MockMvcBuilders.standaloneSetup(new ArtworkController(service))
				.setControllerAdvice(new GlobalExceptionHandler(), new ArtworkExceptionHandler()).build();
	}

	private ArtworkDetailResponse detail(String title) {
		LocalDateTime now = LocalDateTime.now();
		return new ArtworkDetailResponse(1L, 1L, new ArtworkDetailResponse.ArtistSummary(2L, "김작가", null), title,
				null, ArtworkCategory.PAINTING, null, null, null, null, null, 1_000_000L, null, null, null,
				ArtworkStatus.PENDING_REVIEW, List.of(), now, now);
	}

	@Test
	void 작품을_등록하면_201과_Location을_반환한다() throws Exception {
		when(service.register(any(ArtworkCreateRequest.class))).thenReturn(detail("무제"));

		mvc.perform(post("/api/artworks").contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", "/api/artworks/1"))
				.andExpect(jsonPath("$.artist.name").value("김작가"))
				.andExpect(jsonPath("$.status").value("PENDING_REVIEW"));
	}

	@Test
	void 필수값이_없으면_400을_반환한다() throws Exception {
		mvc.perform(post("/api/artworks").contentType(MediaType.APPLICATION_JSON)
						.content("{\"artistId\":2,\"title\":\"무제\",\"category\":\"PAINTING\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		verifyNoInteractions(service);
	}

	@Test
	void 없는_작가로_등록하면_404를_반환한다() throws Exception {
		when(service.register(any(ArtworkCreateRequest.class))).thenThrow(new ArtistNotFoundException(2L));

		mvc.perform(post("/api/artworks").contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
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
		when(service.update(eq(1L), any(ArtworkUpdateRequest.class)))
				.thenThrow(new ArtworkNotEditableException(1L, ArtworkStatus.IN_AUCTION));

		mvc.perform(patch("/api/artworks/1").contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"새 제목\"}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("ARTWORK_NOT_EDITABLE"));
	}

	@Test
	void 작품_정보를_부분_수정한다() throws Exception {
		when(service.update(eq(1L), any(ArtworkUpdateRequest.class))).thenReturn(detail("새 제목"));

		mvc.perform(patch("/api/artworks/1").contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"새 제목\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.title").value("새 제목"));
	}
}
