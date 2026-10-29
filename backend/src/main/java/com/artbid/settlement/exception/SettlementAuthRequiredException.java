package com.artbid.settlement.exception;

public class SettlementAuthRequiredException extends RuntimeException {
	public SettlementAuthRequiredException() {
		super("로그인이 필요합니다.");
	}
}
