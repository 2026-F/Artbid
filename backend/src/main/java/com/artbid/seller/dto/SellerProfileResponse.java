package com.artbid.seller.dto;

import com.artbid.seller.domain.SellerProfile;

public record SellerProfileResponse(Long id, Long memberId, String shopName, String introduction,
                                    String profileImageUrl) {
    public static SellerProfileResponse from(SellerProfile profile) {
        return new SellerProfileResponse(profile.getId(), profile.getMemberId(), profile.getShopName(),
                profile.getIntroduction(), profile.getProfileImageUrl());
    }
}