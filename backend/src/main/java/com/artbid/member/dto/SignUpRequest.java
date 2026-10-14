package com.artbid.member.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@NotBlank
public record SignUpRequest(
        @Email String email,
        @Size(min = 8, max = 64) String password,
        String name) {
}