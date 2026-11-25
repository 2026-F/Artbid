package com.artbid.chat.exception;

/** 닉네임을 정하지 않은 회원이 채팅을 보내려 했다. 채팅에는 실명 대신 닉네임만 보여준다. */
public class ChatNicknameRequiredException extends RuntimeException {

	public ChatNicknameRequiredException() {
		super("채팅을 보내려면 닉네임을 먼저 정해주세요.");
	}
}
