package com.artbid.seller.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 판매자(위탁자, Member.CONSIGNOR) 프로필. 한 회원당 하나만 가질 수 있다.
 * Member 엔티티와는 memberId(Long)로만 연결 — JPA 연관관계 없이 느슨하게 참조한다 (Artwork.consignorId와 같은 방식).
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SellerProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long memberId;

    @Column(nullable = false, length = 50)
    private String shopName;

    @Column(length = 1000)
    private String introduction;

    @Column(length = 500)
    private String profileImageUrl;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public static SellerProfile register(Long memberId, String shopName, String introduction, String profileImageUrl) {
        SellerProfile profile = new SellerProfile();
        profile.memberId = requireMemberId(memberId);
        profile.shopName = requireShopName(shopName);
        profile.introduction = introduction;
        profile.profileImageUrl = profileImageUrl;
        return profile;
    }

    // 부분 수정 — null로 보낸 필드는 기존 값 유지 (Artist/Artwork update()랑 같은 컨벤션)
    public void update(String shopName, String introduction, String profileImageUrl) {
        if (shopName != null) {
            this.shopName = requireShopName(shopName);
        }
        if (introduction != null) {
            this.introduction = introduction;
        }
        if (profileImageUrl != null) {
            this.profileImageUrl = profileImageUrl;
        }
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    private static Long requireMemberId(Long memberId) {
        if (memberId == null || memberId <= 0) {
            throw new IllegalArgumentException("회원 ID는 필수입니다");
        }
        return memberId;
    }

    private static String requireShopName(String shopName) {
        if (shopName == null || shopName.isBlank()) {
            throw new IllegalArgumentException("상호명은 비어 있을 수 없습니다");
        }
        return shopName.trim();
    }
}