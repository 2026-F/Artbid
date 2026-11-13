package com.artbid.chat.exception;

public class ChatRateLimitExceededException extends RuntimeException {

	public ChatRateLimitExceededException() {
		super("메시지를 너무 빠르게 보내고 있습니다. 잠시 후 다시 시도해주세요.");
	}
}
