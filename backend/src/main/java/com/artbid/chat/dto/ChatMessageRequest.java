package com.artbid.chat.dto;

import com.artbid.chat.domain.ChatMessage;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 채팅 전송 요청. 보낸 사람은 본문이 아니라 검증된 토큰에서 가져온다. */
public record ChatMessageRequest(
		@NotBlank(message = "메시지를 입력해주세요.")
		@Size(max = ChatMessage.MAX_CONTENT_LENGTH, message = "메시지는 200자 이하로 입력해주세요.")
		String content
) {
}
