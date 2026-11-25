package com.artbid.artwork.controller;

import com.artbid.artwork.dto.ArtworkPageResponse;
import com.artbid.artwork.exception.ArtworkNotFoundException;
import com.artbid.artwork.service.ArtworkService;
import com.artbid.common.exception.GlobalExceptionHandler;
import com.artbid.member.auth.JwtAuthenticationFilter;
import com.artbid.member.domain.MemberRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.data.domain.Sort;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SellerArtworkControllerTest {
	private ArtworkService service;
	private MockMvc mvc;

	@BeforeEach
	void setup() {
		service = mock(ArtworkService.class);
		mvc = MockMvcBuilders.standaloneSetup(new ArtworkController(service))
				.setControllerAdvice(new GlobalExceptionHandler(), new ArtworkExceptionHandler()).build();
	}

	private RequestPostProcessor loginAs(MemberRole role) {
		return request -> {
			request.setUserPrincipal(() -> "7");
			request.setAttribute(JwtAuthenticationFilter.ROLE_ATTRIBUTE, role);
			return request;
		};
	}

	@Test
	void 목록은_로그인_판매자와_기본_페이지를_사용한다() throws Exception {
		when(service.getMyArtworks(eq(7L), any())).thenReturn(new ArtworkPageResponse(List.of(), 0, 20, 0, 0));
		mvc.perform(get("/api/artworks/mine").with(loginAs(MemberRole.CONSIGNOR)).param("consignorId", "99"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content").isEmpty())
				.andExpect(jsonPath("$.page").value(0))
				.andExpect(jsonPath("$.size").value(20));
		verify(service).getMyArtworks(eq(7L), argThat(page -> page.getPageNumber() == 0
				&& page.getPageSize() == 20 && page.getSort().equals(Sort.by(Sort.Direction.DESC, "id"))));
	}

	@Test
	void 목록의_페이지와_크기를_기존_규칙에_맞춰_보정한다() throws Exception {
		mvc.perform(get("/api/artworks/mine").with(loginAs(MemberRole.CONSIGNOR))
				.param("page", "-1").param("size", "101")).andExpect(status().isOk());
		verify(service).getMyArtworks(eq(7L), argThat(page -> page.getPageNumber() == 0 && page.getPageSize() == 100));
		mvc.perform(get("/api/artworks/mine").with(loginAs(MemberRole.CONSIGNOR))
				.param("page", "1").param("size", "0")).andExpect(status().isOk());
		verify(service).getMyArtworks(eq(7L), argThat(page -> page.getPageNumber() == 1 && page.getPageSize() == 1));
	}

	@Test
	void 상세는_인증된_회원과_작품_ID로_조회한다() throws Exception {
		mvc.perform(get("/api/artworks/mine/42").with(loginAs(MemberRole.CONSIGNOR)))
				.andExpect(status().isOk());
		verify(service).getMyArtwork(42L, 7L);
	}

	@Test
	void 비로그인은_두_조회에서_401을_반환한다() throws Exception {
		for (String path : List.of("/api/artworks/mine", "/api/artworks/mine/42")) {
			mvc.perform(get(path)).andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
		}
		verifyNoInteractions(service);
	}

	@ParameterizedTest
	@EnumSource(value = MemberRole.class, names = "CONSIGNOR", mode = EnumSource.Mode.EXCLUDE)
	void 판매자_외_역할은_403을_반환한다(MemberRole role) throws Exception {
		for (String path : List.of("/api/artworks/mine", "/api/artworks/mine/42")) {
			mvc.perform(get(path).with(loginAs(role))).andExpect(status().isForbidden());
		}
		verifyNoInteractions(service);
	}

	@Test
	void 타인_작품과_없는_작품은_둘_다_404를_반환한다() throws Exception {
		when(service.getMyArtwork(42L, 7L)).thenThrow(new ArtworkNotFoundException(42L));
		when(service.getMyArtwork(99L, 7L)).thenThrow(new ArtworkNotFoundException(99L));
		mvc.perform(get("/api/artworks/mine/42").with(loginAs(MemberRole.CONSIGNOR)))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ARTWORK_NOT_FOUND"));
		mvc.perform(get("/api/artworks/mine/99").with(loginAs(MemberRole.CONSIGNOR)))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ARTWORK_NOT_FOUND"));
	}
}
