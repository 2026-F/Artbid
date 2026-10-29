package com.artbid.seller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SellerProfileRegisterRequest(
        @NotBlank @Size(max = 50) String shopName,
        @Size(max = 1000) String introduction,
        @Size(max = 500) String profileImageUrl) {
}