package com.artbid.payment.service;

import com.artbid.payment.domain.PaymentMethod;
import com.artbid.payment.dto.PaymentResponse;
import com.artbid.payment.gateway.PaymentGateway;
import com.artbid.payment.gateway.PaymentGatewayUnavailableException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentService {

	private final PaymentTransactionService transactions;
	private final PaymentGateway gateway;

	/** 외부 Transaction 안에서 PG를 호출하지 않도록 잘못된 호출도 차단한다. */
	@Transactional(propagation = Propagation.NEVER)
	public PaymentResponse pay(Long settlementId, Long memberId, PaymentMethod method) {
		PaymentResponse requested = transactions.start(settlementId, memberId, method);
		PaymentGateway.Result result;
		try {
			result = gateway.pay(requested.id(), requested.amount());
		} catch (PaymentGatewayUnavailableException e) {
			// 승인 여부 불명: 기존 요청을 유지하며 Client는 이 ID로 조회할 수 있다.
			return requested;
		}
		return transactions.complete(settlementId, requested.id(), result);
	}

	public PaymentResponse get(Long paymentId, Long memberId) {
		return transactions.get(paymentId, memberId);
	}
}
