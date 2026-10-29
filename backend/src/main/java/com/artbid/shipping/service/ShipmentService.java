package com.artbid.shipping.service;

import com.artbid.settlement.domain.PaymentStatus;
import com.artbid.settlement.domain.Settlement;
import com.artbid.settlement.repository.SettlementRepository;
import com.artbid.shipping.domain.Shipment;
import com.artbid.shipping.domain.ShippingAddress;
import com.artbid.shipping.exception.ShipmentException;
import com.artbid.shipping.repository.ShipmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Service
@RequiredArgsConstructor
public class ShipmentService {

	private final SettlementRepository settlementRepository;
	private final ShipmentRepository shipmentRepository;

	/**
	 * 배송지 입력 또는 수정. 낙찰자 본인만 할 수 있다.
	 * 결제 대기(PENDING)나 결제 완료(PAID) 정산에서 가능하고, 결제 실패/기한 만료(FAILED) 정산은 막는다.
	 * 정산 행을 잠가서 같은 정산에 동시에 입력해도 배송 정보가 두 개 생기지 않게 한다(유니크 제약도 있음).
	 */
	@Transactional
	public Shipment saveAddress(Long settlementId, Long memberId, ShippingAddress address) {
		requireLogin(memberId);
		Settlement settlement = settlementRepository.findByIdForUpdate(settlementId)
				.orElseThrow(ShipmentService::settlementNotFound);
		if (!Objects.equals(settlement.getWinnerId(), memberId)) {
			throw new ShipmentException(FORBIDDEN, "SHIPMENT_FORBIDDEN", "낙찰자 본인만 배송지를 입력할 수 있습니다.");
		}
		if (settlement.getPaymentStatus() == PaymentStatus.FAILED) {
			throw new ShipmentException(CONFLICT, "SETTLEMENT_NOT_SHIPPABLE", "결제가 완료되지 않아 배송할 수 없는 정산입니다.");
		}

		LocalDateTime now = now();
		Shipment shipment = shipmentRepository.findBySettlementId(settlementId).orElse(null);
		if (shipment == null) {
			return shipmentRepository.save(Shipment.create(settlementId, address, now));
		}
		try {
			shipment.changeAddress(address, now);
		} catch (IllegalStateException e) {
			throw new ShipmentException(CONFLICT, "SHIPMENT_ALREADY_SHIPPED", e.getMessage());
		}
		return shipment;
	}

	static void requireLogin(Long memberId) {
		if (memberId == null || memberId <= 0) {
			throw new ShipmentException(UNAUTHORIZED, "AUTH_REQUIRED", "로그인이 필요합니다.");
		}
	}

	static ShipmentException settlementNotFound() {
		return new ShipmentException(NOT_FOUND, "SETTLEMENT_NOT_FOUND", "정산 내역을 찾을 수 없습니다.");
	}

	static LocalDateTime now() {
		// PostgreSQL timestamp 정밀도(마이크로초)에 맞춘다. 결제 서비스와 같은 기준
		return LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
	}
}
