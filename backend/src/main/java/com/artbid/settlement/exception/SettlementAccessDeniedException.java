package com.artbid.settlement.exception;

public class SettlementAccessDeniedException extends RuntimeException {
	public SettlementAccessDeniedException() {
		super("이 정산 내역을 볼 권한이 없습니다.");
	}
}
