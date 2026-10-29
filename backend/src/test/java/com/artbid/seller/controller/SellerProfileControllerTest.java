package com.artbid.seller.controller;

import com.artbid.common.exception.GlobalExceptionHandler;
import com.artbid.member.auth.JwtAuthenticationFilter;
import com.artbid.member.domain.MemberRole;
import com.artbid.seller.domain.SellerProfile;
import com.artbid.seller.exception.SellerProfileAlreadyExistsException;
import com.artbid.seller.exception.SellerProfileForbiddenException;
import com.artbid.seller.exception.SellerProfileNotFoundException;
import com.artbid.seller.service.SellerProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SellerProfileControllerTest {

    private static final String REGISTER_BODY = """
            {"shopName":"아트샵","introduction":"소개글","profileImageUrl":"https://img/profile.png"}
            """;

    private SellerProfileService service;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        service = mock(SellerProfileService.class);
        mvc = MockMvcBuilders.standaloneSetup(new SellerProfileController(service))
                .setControllerAdvice(new GlobalExceptionHandler(), new SellerProfileExceptionHandler()).build();
    }

    private SellerProfile profile(Long memberId, String shopName) {
        return SellerProfile.register(memberId, shopName, "소개글", "https://img/profile.png");
    }

    @Test
    void CONSIGNOR가_등록하면_201과_Location을_반환한다() throws Exception {
        when(service.register(eq(1L), eq(MemberRole.CONSIGNOR), any())).thenReturn(profile(1L, "아트샵"));

        mvc.perform(post("/api/sellers").principal(() -> "1")
                        .requestAttr(JwtAuthenticationFilter.ROLE_ATTRIBUTE, MemberRole.CONSIGNOR)
                        .contentType(MediaType.APPLICATION_JSON).content(REGISTER_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/sellers/1"))
                .andExpect(jsonPath("$.shopName").value("아트샵"));
        verify(service).register(eq(1L), eq(MemberRole.CONSIGNOR), any());
    }

    @Test
    void 필수값인_상호명이_없으면_400을_반환한다() throws Exception {
        mvc.perform(post("/api/sellers").principal(() -> "1")
                        .requestAttr(JwtAuthenticationFilter.ROLE_ATTRIBUTE, MemberRole.CONSIGNOR)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"introduction\":\"소개\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(service);
    }

    @Test
    void 인증_없이_등록하면_401이다() throws Exception {
        mvc.perform(post("/api/sellers")
                        .requestAttr(JwtAuthenticationFilter.ROLE_ATTRIBUTE, MemberRole.CONSIGNOR)
                        .contentType(MediaType.APPLICATION_JSON).content(REGISTER_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SELLER_AUTH_REQUIRED"));
        verifyNoInteractions(service);
    }

    @Test
    void CONSIGNOR가_아니면_403을_반환한다() throws Exception {
        when(service.register(eq(1L), eq(MemberRole.BIDDER), any()))
                .thenThrow(new SellerProfileForbiddenException("판매자(CONSIGNOR)만 프로필을 등록할 수 있습니다."));

        mvc.perform(post("/api/sellers").principal(() -> "1")
                        .requestAttr(JwtAuthenticationFilter.ROLE_ATTRIBUTE, MemberRole.BIDDER)
                        .contentType(MediaType.APPLICATION_JSON).content(REGISTER_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SELLER_PROFILE_FORBIDDEN"));
    }

    @Test
    void 이미_등록된_회원이면_409를_반환한다() throws Exception {
        when(service.register(eq(1L), eq(MemberRole.CONSIGNOR), any()))
                .thenThrow(new SellerProfileAlreadyExistsException(1L));

        mvc.perform(post("/api/sellers").principal(() -> "1")
                        .requestAttr(JwtAuthenticationFilter.ROLE_ATTRIBUTE, MemberRole.CONSIGNOR)
                        .contentType(MediaType.APPLICATION_JSON).content(REGISTER_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SELLER_PROFILE_ALREADY_EXISTS"));
    }

    @Test
    void 프로필_조회는_인증_없이도_가능하다() throws Exception {
        when(service.getByMemberId(1L)).thenReturn(profile(1L, "아트샵"));

        mvc.perform(get("/api/sellers/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shopName").value("아트샵"));
    }

    @Test
    void 없는_회원의_프로필을_조회하면_404를_반환한다() throws Exception {
        when(service.getByMemberId(99L)).thenThrow(new SellerProfileNotFoundException(99L));

        mvc.perform(get("/api/sellers/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SELLER_PROFILE_NOT_FOUND"));
    }

    @Test
    void 본인_프로필을_수정한다() throws Exception {
        when(service.update(eq(1L), eq(1L), any())).thenReturn(profile(1L, "새 아트샵"));

        mvc.perform(patch("/api/sellers/1").principal(() -> "1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"shopName\":\"새 아트샵\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shopName").value("새 아트샵"));
    }

    @Test
    void 타인_프로필을_수정하면_403을_반환한다() throws Exception {
        when(service.update(eq(1L), eq(2L), any()))
                .thenThrow(new SellerProfileForbiddenException("본인 프로필만 수정할 수 있습니다."));

        mvc.perform(patch("/api/sellers/1").principal(() -> "2")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"shopName\":\"새 아트샵\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SELLER_PROFILE_FORBIDDEN"));
    }

    @Test
    void 인증_없이_수정하면_401이다() throws Exception {
        mvc.perform(patch("/api/sellers/1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"shopName\":\"새 아트샵\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SELLER_AUTH_REQUIRED"));
        verifyNoInteractions(service);
    }
}
