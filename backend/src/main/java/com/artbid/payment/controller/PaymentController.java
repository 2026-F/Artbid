package com.artbid.payment.controller;

import com.artbid.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settlements")
@RequiredArgsConstructor
public class PaymentController {

	private final PaymentService paymentService;

	@PostMapping("/{id}/payments")
	public void pay(@PathVariable Long id, @RequestBody PaymentRequest request) {
		paymentService.pay(id, request.amount());
	}

	public record PaymentRequest(Long amount) {
	}
}
