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
import org.springframework.dao.DataIntegrityViolationException;

@Service
@RequiredArgsConstructor
public class SellerProfileService {

    private final SellerProfileRepository sellerProfileRepository;

    @Transactional
    public SellerProfile register(Long memberId, MemberRole role, SellerProfileRegisterRequest request) {
        requireConsignor(role);

        // 1차 방어: 평소(동시 요청이 없는 경우)엔 여기서 바로 걸러져서 DB까지 안 가고 끝남
        if (sellerProfileRepository.existsByMemberId(memberId)) {
            throw new SellerProfileAlreadyExistsException(memberId);
        }

        SellerProfile profile = SellerProfile.register(memberId, request.shopName(), request.introduction(),
                request.profileImageUrl());

        // 2차 방어(최종 방어선)
        try {
            return sellerProfileRepository.save(profile);
        } catch (DataIntegrityViolationException e) {
            if (isMemberIdUniqueViolation(e)) {
                throw new SellerProfileAlreadyExistsException(memberId);
            }
            // memberId 중복이 아닌 다른 무결성 제약 위반이면 그대로 던져서
            // 원래대로 500 처리되게 둔다 (원인을 숨기지 않기 위해)
            throw e;
        }
    }

    // DataIntegrityViolationException의 가장 근본 원인(실제 DB 드라이버가 준 메시지)에
    // 우리가 지정한 제약조건 이름이 포함돼 있는지로 "memberId 중복이 맞는지" 판별.
    private boolean isMemberIdUniqueViolation(DataIntegrityViolationException e) {
        String message = e.getMostSpecificCause().getMessage();
        return message != null && message.contains("uk_seller_profile_member_id");
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