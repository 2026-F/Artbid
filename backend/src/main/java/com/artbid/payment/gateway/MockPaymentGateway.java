package com.artbid.payment.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 서버 설정으로 결과를 선택한다. Client가 결제 성공 여부를 지정하지 않는다. */
@Component
public class MockPaymentGateway implements PaymentGateway {
	public enum Outcome { SUCCESS, DECLINED, TIMEOUT }
	private final Outcome outcome;

	public MockPaymentGateway(@Value("${app.payment.mock-outcome:SUCCESS}") Outcome outcome) {
		this.outcome = outcome;
	}

	@Override
	public Result pay(Long paymentId, Long amount) {
		return switch (outcome) {
			case SUCCESS -> new Result(true, "txn_mock_" + paymentId, null);
			case DECLINED -> new Result(false, null, "모의 PG 승인 거절");
			case TIMEOUT -> throw new PaymentGatewayUnavailableException("모의 PG 응답 시간 초과");
		};
	}
}
