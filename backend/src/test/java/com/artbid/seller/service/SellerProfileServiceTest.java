package com.artbid.seller.service;

import com.artbid.member.domain.MemberRole;
import com.artbid.seller.domain.SellerProfile;
import com.artbid.seller.dto.SellerProfileRegisterRequest;
import com.artbid.seller.dto.SellerProfileUpdateRequest;
import com.artbid.seller.exception.SellerProfileAlreadyExistsException;
import com.artbid.seller.exception.SellerProfileForbiddenException;
import com.artbid.seller.exception.SellerProfileNotFoundException;
import com.artbid.seller.repository.SellerProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SellerProfileServiceTest {

    private SellerProfileRepository repository;
    private SellerProfileService service;

    @BeforeEach
    void setup() {
        repository = mock(SellerProfileRepository.class);
        service = new SellerProfileService(repository);
    }

    private SellerProfileRegisterRequest registerRequest() {
        return new SellerProfileRegisterRequest("아트샵", "소개", "https://img/profile.png");
    }

    @Test
    void CONSIGNOR면_프로필을_등록한다() {
        when(repository.existsByMemberId(1L)).thenReturn(false);
        when(repository.save(any(SellerProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SellerProfile profile = service.register(1L, MemberRole.CONSIGNOR, registerRequest());

        assertThat(profile.getMemberId()).isEqualTo(1L);
        assertThat(profile.getShopName()).isEqualTo("아트샵");
        verify(repository).save(any(SellerProfile.class));
    }

    @ParameterizedTest
    @EnumSource(value = MemberRole.class, names = "CONSIGNOR", mode = EnumSource.Mode.EXCLUDE)
    void CONSIGNOR가_아니면_등록을_거부한다(MemberRole role) {
        assertThatThrownBy(() -> service.register(1L, role, registerRequest()))
                .isInstanceOf(SellerProfileForbiddenException.class);
        verifyNoInteractions(repository);
    }

    @Test
    // 토큰에 role 클레임이 없는 경우 (옛날 토큰 등)
    void role이_없으면_등록을_거부한다() {
        assertThatThrownBy(() -> service.register(1L, null, registerRequest()))
                .isInstanceOf(SellerProfileForbiddenException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void 이미_프로필이_있으면_중복_등록을_거부한다() {
        when(repository.existsByMemberId(1L)).thenReturn(true);

        assertThatThrownBy(() -> service.register(1L, MemberRole.CONSIGNOR, registerRequest()))
                .isInstanceOf(SellerProfileAlreadyExistsException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void memberId로_프로필을_조회한다() {
        SellerProfile profile = SellerProfile.register(1L, "아트샵", null, null);
        when(repository.findByMemberId(1L)).thenReturn(Optional.of(profile));

        SellerProfile result = service.getByMemberId(1L);

        assertThat(result.getMemberId()).isEqualTo(1L);
    }

    @Test
    void 없는_프로필을_조회하면_예외를_던진다() {
        when(repository.findByMemberId(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getByMemberId(99L))
                .isInstanceOf(SellerProfileNotFoundException.class);
    }

    @Test
    void 본인_프로필을_수정한다() {
        SellerProfile profile = SellerProfile.register(1L, "아트샵", "소개", null);
        when(repository.findByMemberId(1L)).thenReturn(Optional.of(profile));
        SellerProfileUpdateRequest request = new SellerProfileUpdateRequest("새 아트샵", null, null);

        SellerProfile result = service.update(1L, 1L, request);

        assertThat(result.getShopName()).isEqualTo("새 아트샵");
        assertThat(result.getIntroduction()).isEqualTo("소개"); // null로 보낸 필드는 기존 값 유지
    }

    @Test
    void 타인_프로필_수정은_거부한다() {
        SellerProfileUpdateRequest request = new SellerProfileUpdateRequest("새 아트샵", null, null);

        assertThatThrownBy(() -> service.update(1L, 2L, request))
                .isInstanceOf(SellerProfileForbiddenException.class);
        // 본인 확인에서 바로 막히므로 조회 자체가 일어나지 않아야 한다
        verifyNoInteractions(repository);
    }
}
