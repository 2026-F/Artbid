package com.artbid.seller.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SellerProfileTest {

    @Test
    void 정상적인_값으로_등록할_수_있다() {
        SellerProfile profile = SellerProfile.register(1L, "  아트샵  ", "소개글입니다", "https://img/profile.png");

        assertThat(profile.getMemberId()).isEqualTo(1L);
        // register()는 상호명 앞뒤 공백을 trim한다 (requireShopName)
        assertThat(profile.getShopName()).isEqualTo("아트샵");
        assertThat(profile.getIntroduction()).isEqualTo("소개글입니다");
        assertThat(profile.getProfileImageUrl()).isEqualTo("https://img/profile.png");
    }

    @Test
    void 회원ID가_없으면_등록할_수_없다() {
        assertThatThrownBy(() -> SellerProfile.register(null, "아트샵", null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 회원ID가_0이하이면_등록할_수_없다() {
        assertThatThrownBy(() -> SellerProfile.register(0L, "아트샵", null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SellerProfile.register(-1L, "아트샵", null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 상호명이_비어있으면_등록할_수_없다() {
        assertThatThrownBy(() -> SellerProfile.register(1L, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SellerProfile.register(1L, "   ", null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 부분_수정시_null인_필드는_기존_값을_유지한다() {
        SellerProfile profile = SellerProfile.register(1L, "아트샵", "소개", "https://img/old.png");

        profile.update(null, "새 소개", null);

        assertThat(profile.getShopName()).isEqualTo("아트샵");
        assertThat(profile.getIntroduction()).isEqualTo("새 소개");
        assertThat(profile.getProfileImageUrl()).isEqualTo("https://img/old.png");
    }

    @Test
    void 상호명을_공백으로_수정할_수_없다() {
        SellerProfile profile = SellerProfile.register(1L, "아트샵", null, null);

        assertThatThrownBy(() -> profile.update("   ", null, null))
                .isInstanceOf(IllegalArgumentException.class);
        // 예외가 터져도 기존 값은 그대로 유지돼야 한다
        assertThat(profile.getShopName()).isEqualTo("아트샵");
    }

    @Test
    void 모든_필드를_한번에_수정할_수_있다() {
        SellerProfile profile = SellerProfile.register(1L, "아트샵", "소개", "https://img/old.png");

        profile.update("새 아트샵", "새 소개", "https://img/new.png");

        assertThat(profile.getShopName()).isEqualTo("새 아트샵");
        assertThat(profile.getIntroduction()).isEqualTo("새 소개");
        assertThat(profile.getProfileImageUrl()).isEqualTo("https://img/new.png");
    }
}
