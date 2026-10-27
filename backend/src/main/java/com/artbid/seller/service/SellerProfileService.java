package com.artbid.seller.service;

import com.artbid.member.domain.MemberRole;
import com.artbid.seller.domain.SellerProfile;
import com.artbid.seller.dto.SellerProfileRegisterRequest;
import com.artbid.seller.dto.SellerProfileUpdateRequest;
import com.artbid.seller.exception.SellerProfileAlreadyExistsException;
import com.artbid.seller.exception.SellerProfileForbiddenException;
import com.artbid.seller.exception.SellerProfileNotFoundException;
import com.artbid.seller.repository.SellerProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SellerProfileService {

    private final SellerProfileRepository sellerProfileRepository;

    @Transactional
    public SellerProfile register(Long memberId, MemberRole role, SellerProfileRegisterRequest request) {
        requireConsignor(role);
        if (sellerProfileRepository.existsByMemberId(memberId)) {
            throw new SellerProfileAlreadyExistsException(memberId);
        }
        SellerProfile profile = SellerProfile.register(memberId, request.shopName(), request.introduction(),
                request.profileImageUrl());
        return sellerProfileRepository.save(profile);
    }

    public SellerProfile getByMemberId(Long memberId) {
        return sellerProfileRepository.findByMemberId(memberId)
                .orElseThrow(() -> new SellerProfileNotFoundException(memberId));
    }

    @Transactional
    public SellerProfile update(Long memberId, Long requesterId, SellerProfileUpdateRequest request) {
        requireSelf(memberId, requesterId);
        SellerProfile profile = getByMemberId(memberId);
        profile.update(request.shopName(), request.introduction(), request.profileImageUrl());
        return profile;
    }

    // 토큰에 role 클레임이 없는(= 옛날 토큰이거나 비로그인) 경우도 여기서 같이 걸러짐
    private void requireConsignor(MemberRole role) {
        if (role != MemberRole.CONSIGNOR) {
            throw new SellerProfileForbiddenException("판매자(CONSIGNOR)만 프로필을 등록할 수 있습니다.");
        }
    }

    private void requireSelf(Long targetMemberId, Long requesterId) {
        if (!targetMemberId.equals(requesterId)) {
            throw new SellerProfileForbiddenException("본인 프로필만 수정할 수 있습니다.");
        }
    }
}