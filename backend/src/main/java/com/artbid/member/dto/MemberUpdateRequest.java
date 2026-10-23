package com.artbid.member.dto;

import jakarta.validation.constraints.Size;

public record MemberUpdateRequest(
        @Size(max = 50) String name
) {
}