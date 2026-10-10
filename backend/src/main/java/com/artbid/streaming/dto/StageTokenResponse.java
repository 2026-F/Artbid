package com.artbid.streaming.dto;

import software.amazon.awssdk.services.ivsrealtime.model.ParticipantToken;
import software.amazon.awssdk.services.ivsrealtime.model.ParticipantTokenCapability;

import java.time.Instant;

/** 스테이지 참여 토큰 응답. role로 PUBLISH/SUBSCRIBE 중 어떤 토큰인지 알려준다. */
public record StageTokenResponse(
		String token,
		String role,
		Instant expiresAt
) {

	public static StageTokenResponse from(ParticipantToken participantToken, ParticipantTokenCapability capability) {
		return new StageTokenResponse(participantToken.token(), capability.toString(), participantToken.expirationTime());
	}
}
