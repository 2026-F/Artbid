package com.artbid.seller.controller;

import com.artbid.member.auth.JwtAuthenticationFilter;
import com.artbid.member.domain.MemberRole;
import com.artbid.seller.domain.SellerProfile;
import com.artbid.seller.dto.SellerProfileRegisterRequest;
import com.artbid.seller.dto.SellerProfileResponse;
import com.artbid.seller.dto.SellerProfileUpdateRequest;
import com.artbid.seller.exception.SellerAuthRequiredException;
import com.artbid.seller.service.SellerProfileService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.security.Principal;

@RestController
@RequestMapping("/api/sellers")
@RequiredArgsConstructor
public class SellerProfileController {

    private final SellerProfileService sellerProfileService;

    // 본인(CONSIGNOR 역할) 프로필 등록. memberId는 요청 바디가 아니라 토큰에서 가져온다.
    @PostMapping
    public ResponseEntity<SellerProfileResponse> register(@Valid @RequestBody SellerProfileRegisterRequest request,
                                                          Principal principal, HttpServletRequest httpRequest) {
        Long memberId = memberId(principal);
        MemberRole role = role(httpRequest);
        SellerProfile profile = sellerProfileService.register(memberId, role, request);
        return ResponseEntity.created(URI.create("/api/sellers/" + profile.getMemberId()))
                .body(SellerProfileResponse.from(profile));
    }

    // 공개 조회 — 로그인 없이 누구나 볼 수 있음
    @GetMapping("/{memberId}")
    public SellerProfileResponse get(@PathVariable Long memberId) {
        return SellerProfileResponse.from(sellerProfileService.getByMemberId(memberId));
    }

    // 본인 프로필만 수정 가능
    @PatchMapping("/{memberId}")
    public SellerProfileResponse update(@PathVariable Long memberId,
                                        @Valid @RequestBody SellerProfileUpdateRequest request, Principal principal) {
        SellerProfile profile = sellerProfileService.update(memberId, memberId(principal), request);
        return SellerProfileResponse.from(profile);
    }

    // Authentication 연동 계약: Principal.name에는 검증된 Member ID를 사용한다. (다른 컨트롤러들과 동일한 컨벤션)
    private Long memberId(Principal principal) {
        if (principal != null) {
            try {
                long id = Long.parseLong(principal.getName());
                if (id > 0) return id;
            } catch (NumberFormatException ignored) {
            }
        }
        throw new SellerAuthRequiredException();
    }

    private MemberRole role(HttpServletRequest request) {
        Object role = request.getAttribute(JwtAuthenticationFilter.ROLE_ATTRIBUTE);
        return role instanceof MemberRole ? (MemberRole) role : null;
    }
}