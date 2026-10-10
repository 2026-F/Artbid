package com.artbid.infra.streaming;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.ivsrealtime.IvsRealTimeClient;
import software.amazon.awssdk.services.ivsrealtime.model.CreateParticipantTokenRequest;
import software.amazon.awssdk.services.ivsrealtime.model.CreateParticipantTokenResponse;
import software.amazon.awssdk.services.ivsrealtime.model.CreateStageRequest;
import software.amazon.awssdk.services.ivsrealtime.model.CreateStageResponse;
import software.amazon.awssdk.services.ivsrealtime.model.DeleteStageRequest;
import software.amazon.awssdk.services.ivsrealtime.model.ParticipantToken;
import software.amazon.awssdk.services.ivsrealtime.model.ParticipantTokenCapability;
import software.amazon.awssdk.services.ivsrealtime.model.ResourceNotFoundException;
import software.amazon.awssdk.services.ivsrealtime.model.Stage;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class IvsStageClientTest {

	private static final String STAGE_ARN = "arn:aws:ivs:ap-northeast-2:123456789012:stage/AbCdEf123456";

	private IvsRealTimeClient ivsRealTimeClient;
	private IvsStageClient ivsStageClient;

	@BeforeEach
	void setup() {
		ivsRealTimeClient = mock(IvsRealTimeClient.class);
		ivsStageClient = new IvsStageClient(ivsRealTimeClient);
	}

	@Test
	void 경매_ID로_이름과_태그를_붙여_스테이지를_만들고_ARN을_돌려준다() {
		when(ivsRealTimeClient.createStage(any(CreateStageRequest.class)))
				.thenReturn(CreateStageResponse.builder()
						.stage(Stage.builder().arn(STAGE_ARN).build())
						.build());

		String stageArn = ivsStageClient.createStage(7L);

		ArgumentCaptor<CreateStageRequest> captor = ArgumentCaptor.forClass(CreateStageRequest.class);
		verify(ivsRealTimeClient).createStage(captor.capture());
		assertThat(captor.getValue().name()).isEqualTo("artbid-auction-7");
		assertThat(captor.getValue().tags()).containsEntry("auctionId", "7").containsEntry("service", "artbid");
		assertThat(stageArn).isEqualTo(STAGE_ARN);
	}

	@Test
	void 스테이지를_ARN으로_삭제한다() {
		ivsStageClient.deleteStageIfExists(STAGE_ARN);

		ArgumentCaptor<DeleteStageRequest> captor = ArgumentCaptor.forClass(DeleteStageRequest.class);
		verify(ivsRealTimeClient).deleteStage(captor.capture());
		assertThat(captor.getValue().arn()).isEqualTo(STAGE_ARN);
	}

	@Test
	void 이미_삭제된_스테이지면_예외없이_넘어간다() {
		when(ivsRealTimeClient.deleteStage(any(DeleteStageRequest.class)))
				.thenThrow(ResourceNotFoundException.builder().message("not found").build());

		assertThatCode(() -> ivsStageClient.deleteStageIfExists(STAGE_ARN)).doesNotThrowAnyException();
	}

	@Test
	void 참여_토큰을_분_단위_duration으로_변환해서_요청한다() {
		Instant expiresAt = Instant.parse("2026-01-01T00:00:00Z");
		ParticipantToken participantToken = ParticipantToken.builder()
				.token("token-value")
				.participantId("participant-1")
				.expirationTime(expiresAt)
				.build();
		when(ivsRealTimeClient.createParticipantToken(any(CreateParticipantTokenRequest.class)))
				.thenReturn(CreateParticipantTokenResponse.builder().participantToken(participantToken).build());

		ParticipantToken result = ivsStageClient.createParticipantToken(
				STAGE_ARN, "member-1", ParticipantTokenCapability.PUBLISH, Duration.ofSeconds(150));

		ArgumentCaptor<CreateParticipantTokenRequest> captor = ArgumentCaptor.forClass(CreateParticipantTokenRequest.class);
		verify(ivsRealTimeClient).createParticipantToken(captor.capture());
		assertThat(captor.getValue().stageArn()).isEqualTo(STAGE_ARN);
		assertThat(captor.getValue().userId()).isEqualTo("member-1");
		assertThat(captor.getValue().capabilities()).containsExactly(ParticipantTokenCapability.PUBLISH);
		assertThat(captor.getValue().duration()).isEqualTo(2); // 150초 -> 2분(버림)
		assertThat(result.token()).isEqualTo("token-value");
		assertThat(result.expirationTime()).isEqualTo(expiresAt);
	}
}
