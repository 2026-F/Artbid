package com.artbid.payment.controller;

import com.artbid.payment.service.PaymentService;
import com.artbid.payment.domain.PaymentMethod;
import com.artbid.payment.domain.PaymentStatus;
import com.artbid.payment.dto.PaymentResponse;
import com.artbid.payment.exception.PaymentException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.security.Principal;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PaymentController {

	private final PaymentService paymentService;

	@PostMapping("/settlements/{id}/payments")
	public ResponseEntity<PaymentResponse> pay(@PathVariable("id") Long id,
			@Valid @RequestBody PaymentRequest request, Principal principal) {
		PaymentResponse response = paymentService.pay(id, memberId(principal), request.method());
		return ResponseEntity.status(response.status() == PaymentStatus.REQUESTED
				? HttpStatus.ACCEPTED : HttpStatus.OK).body(response);
	}

	@GetMapping("/payments/{id}")
	public PaymentResponse get(@PathVariable("id") Long id, Principal principal) {
		return paymentService.get(id, memberId(principal));
	}

	// Authentication 연동 계약: Principal.name에는 검증된 Member ID를 사용한다.
	private Long memberId(Principal principal) {
		if (principal != null) {
			try {
				long id = Long.parseLong(principal.getName());
				if (id > 0) return id;
			} catch (NumberFormatException ignored) {
				// 임의의 Body/Header 사용자 ID로 대체하지 않는다.
			}
		}
		throw new PaymentException(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "로그인이 필요합니다.");
	}

	public record PaymentRequest(@NotNull PaymentMethod method) {
	}
}
