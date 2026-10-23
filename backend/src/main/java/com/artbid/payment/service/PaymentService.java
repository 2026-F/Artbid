package com.artbid.payment.service;

import com.artbid.payment.domain.PaymentMethod;
import com.artbid.payment.domain.PaymentStatus;
import com.artbid.payment.dto.PaymentResponse;
import com.artbid.payment.exception.PaymentException;
import com.artbid.payment.gateway.PaymentGateway;
import com.artbid.payment.gateway.PaymentGatewayUnavailableException;
import com.artbid.payment.gateway.TossPaymentClient;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentService {

	private final PaymentTransactionService transactions;
	private final PaymentGateway gateway;
	private final TossPaymentClient tossPaymentClient;   // 추가

	@Transactional(propagation = Propagation.NEVER)
	public PaymentResponse pay(Long settlementId, Long memberId, PaymentMethod method) {
		PaymentResponse requested = transactions.start(settlementId, memberId, method);

		if (method == PaymentMethod.TOSS) {
			// 토스는 프론트가 결제위젯으로 체크아웃을 끝내야 승인할 수 있음 -> 여기서는 요청만 만들고 반환
			// 프론트는 이 응답의 id로 orderId("payment_" + id)를 구성해서 위젯을 띄운다
			return requested;
		}

		PaymentGateway.Result result;
		try {
			result = gateway.pay(requested.id(), requested.amount());
		} catch (PaymentGatewayUnavailableException e) {
			return requested;
		}
		return transactions.complete(settlementId, requested.id(), result);
	}

	/** 토스 결제위젯 체크아웃이 끝난 뒤, 프론트가 받은 paymentKey/orderId로 최종 승인을 확정한다. */
	@Transactional(propagation = Propagation.NEVER)
	public PaymentResponse confirmToss(Long paymentId, Long memberId, String paymentKey, String orderId) {
		PaymentResponse payment = transactions.get(paymentId, memberId);

		String expectedOrderId = "payment_" + paymentId;
		if (!expectedOrderId.equals(orderId)) {
			throw new PaymentException(HttpStatus.CONFLICT, "ORDER_ID_MISMATCH", "결제 요청 정보가 일치하지 않습니다.");
		}
		if (payment.status() != PaymentStatus.REQUESTED) {
			throw new PaymentException(HttpStatus.CONFLICT, "ALREADY_PROCESSED", "이미 처리된 결제입니다.");
		}

		PaymentGateway.Result result;
		try {
			// amount는 클라이언트가 아니라 DB에 저장된 payment.amount()를 그대로 사용 (위변조 방지)
			result = tossPaymentClient.confirm(paymentKey, expectedOrderId, payment.amount());
		} catch (PaymentGatewayUnavailableException e) {
			return payment;
		}
		return transactions.complete(payment.settlementId(), paymentId, result);
	}


	public PaymentResponse get(Long paymentId, Long memberId) {
		return transactions.get(paymentId, memberId);
	}
}