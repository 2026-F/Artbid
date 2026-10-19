package com.artbid.artist.controller;

import com.artbid.artist.dto.ArtistCreateRequest;
import com.artbid.artist.dto.ArtistPageResponse;
import com.artbid.artist.dto.ArtistResponse;
import com.artbid.artist.dto.ArtistUpdateRequest;
import com.artbid.artist.exception.ArtistNotFoundException;
import com.artbid.artist.service.ArtistService;
import com.artbid.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ArtistControllerTest {

	private ArtistService service;
	private MockMvc mvc;

	@BeforeEach
	void setup() {
		service = mock(ArtistService.class);
		mvc = MockMvcBuilders.standaloneSetup(new ArtistController(service))
				.setControllerAdvice(new GlobalExceptionHandler(), new ArtistExceptionHandler()).build();
	}

	private ArtistResponse artist(String name) {
		LocalDateTime now = LocalDateTime.now();
		return new ArtistResponse(1L, name, "소개", 1980, "대한민국", null, now, now);
	}

	@Test
	void 작가를_등록하면_201과_Location을_반환한다() throws Exception {
		when(service.register(any(ArtistCreateRequest.class))).thenReturn(artist("김작가"));

		mvc.perform(post("/api/artists").contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"김작가\",\"birthYear\":1980}"))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", "/api/artists/1"))
				.andExpect(jsonPath("$.name").value("김작가"));
	}

	@Test
	void 이름_없이_등록하면_400을_반환한다() throws Exception {
		mvc.perform(post("/api/artists").contentType(MediaType.APPLICATION_JSON).content("{\"biography\":\"소개\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		verifyNoInteractions(service);
	}

	@Test
	void 키워드로_작가_목록을_조회한다() throws Exception {
		when(service.getArtists(eq("김"), any(Pageable.class)))
				.thenReturn(new ArtistPageResponse(List.of(artist("김작가")), 0, 20, 1, 1));

		mvc.perform(get("/api/artists").param("keyword", "김"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].name").value("김작가"))
				.andExpect(jsonPath("$.totalElements").value(1));
	}

	@Test
	void 없는_작가를_조회하면_404를_반환한다() throws Exception {
		when(service.getArtist(99L)).thenThrow(new ArtistNotFoundException(99L));

		mvc.perform(get("/api/artists/99"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("ARTIST_NOT_FOUND"));
	}

	@Test
	void 작가_정보를_부분_수정한다() throws Exception {
		when(service.update(eq(1L), any(ArtistUpdateRequest.class))).thenReturn(artist("새이름"));

		mvc.perform(patch("/api/artists/1").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"새이름\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("새이름"));
	}
}
