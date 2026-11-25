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
import com.artbid.payment.domain.PaymentMethod;
import java.util.Optional;

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

	/**
	 * 결제창에서 취소하거나 실패했을 때 프론트가 부른다. 요청됨(REQUESTED) 결제를 바로 정리해서
	 * 복구 스케줄러(15분)를 기다리지 않고 다시 결제할 수 있게 한다.
	 *
	 * 바로 실패로 바꾸지 않고 토스에 먼저 물어본다. 사용자가 실제로는 카드 인증을 끝냈고 승인이 진행 중일 수 있어서다.
	 *  - 이미 확정된 결제 → 그대로 돌려준다 (여러 번 불러도 안전)
	 *  - 토스: 결제 내역 없음·중단·만료·취소 → 실패로 확정 / 결제 완료 → 완료로 확정
	 *  - 토스: 진행 중이거나 응답 없음 → 바꾸지 않고 409 (조금 뒤 다시 시도하거나 복구 스케줄러가 정리)
	 *  - 모의 결제: 조회할 곳이 없으므로 바로 실패로 확정
	 */
	@Transactional(propagation = Propagation.NEVER)
	public PaymentResponse cancel(Long paymentId, Long memberId) {
		PaymentResponse payment = transactions.get(paymentId, memberId); // 낙찰자 본인인지 여기서 확인한다
		if (payment.status() != PaymentStatus.REQUESTED) {
			return payment;
		}

		Optional<PaymentGateway.Result> result = payment.method() == PaymentMethod.TOSS
				? tossPaymentClient.lookup("payment_" + paymentId, payment.amount())
				: Optional.of(new PaymentGateway.Result(false, null, "사용자가 결제를 취소함"));

		if (result.isEmpty()) {
			throw new PaymentException(HttpStatus.CONFLICT, "PAYMENT_IN_PROGRESS",
					"결제를 확인하고 있어요. 잠시 후 다시 시도해 주세요.");
		}
		transactions.resolveUnknown(paymentId, result.get());
		return transactions.get(paymentId, memberId);
	}


	public PaymentResponse get(Long paymentId, Long memberId) {
		return transactions.get(paymentId, memberId);
	}
}