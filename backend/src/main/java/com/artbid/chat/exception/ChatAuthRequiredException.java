package com.artbid.chat.exception;

public class ChatAuthRequiredException extends RuntimeException {

	public ChatAuthRequiredException() {
		super("채팅을 보내려면 로그인이 필요합니다.");
	}
}
