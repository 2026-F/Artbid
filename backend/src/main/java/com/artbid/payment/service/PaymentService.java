package com.artbid.payment.service;

import com.artbid.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PaymentService {

	private final PaymentRepository paymentRepository;

	/**
	 * 낙찰 후 결제(모의 PG 연동).
	 * TODO: 결제 실패 시 보상 트랜잭션(낙찰 취소/재경매 오픈)을 포함한 Saga 흐름 설계
	 */
	public void pay(Long settlementId, Long amount) {
	}
}
