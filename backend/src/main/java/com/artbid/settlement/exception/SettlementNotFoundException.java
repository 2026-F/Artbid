package com.artbid.settlement.exception;

public class SettlementNotFoundException extends RuntimeException {
	public SettlementNotFoundException() {
		super("정산 내역을 찾을 수 없습니다.");
	}
}
