package com.artbid.shipping.service;

import com.artbid.artwork.repository.ArtworkRepository;
import com.artbid.auction.repository.AuctionRepository;
import com.artbid.member.domain.MemberRole;
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
	private final AuctionRepository auctionRepository;
	private final ArtworkRepository artworkRepository;

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

	/**
	 * 운송장 등록. 해당 작품 위탁자 본인이나 관리자만 할 수 있다.
	 * 결제가 끝난(PAID) 정산이고 배송지가 입력돼 있어야 한다. 이미 등록했으면 운송장 정보만 고친다.
	 */
	@Transactional
	public Shipment registerTracking(Long settlementId, Long memberId, MemberRole role, String carrier, String trackingNumber) {
		requireLogin(memberId);
		Settlement settlement = settlementRepository.findByIdForUpdate(settlementId)
				.orElseThrow(ShipmentService::settlementNotFound);
		if (role != MemberRole.ADMIN && !isConsignor(settlement, memberId)) {
			throw new ShipmentException(FORBIDDEN, "SHIPMENT_FORBIDDEN", "작품 위탁자나 관리자만 운송장을 등록할 수 있습니다.");
		}
		if (settlement.getPaymentStatus() != PaymentStatus.PAID) {
			throw new ShipmentException(CONFLICT, "SETTLEMENT_NOT_PAID", "결제가 완료된 뒤에 발송할 수 있습니다.");
		}
		Shipment shipment = shipmentRepository.findBySettlementId(settlementId)
				.orElseThrow(() -> new ShipmentException(CONFLICT, "SHIPMENT_ADDRESS_REQUIRED", "낙찰자가 아직 배송지를 입력하지 않았습니다."));
		try {
			shipment.registerTracking(carrier, trackingNumber, now());
		} catch (IllegalStateException e) {
			throw new ShipmentException(CONFLICT, "SHIPMENT_ALREADY_DELIVERED", e.getMessage());
		}
		return shipment;
	}

	/** 배송 정보 조회. 낙찰자 본인, 해당 작품 위탁자, 관리자만. */
	public Shipment get(Long settlementId, Long memberId, MemberRole role) {
		requireLogin(memberId);
		Settlement settlement = settlementRepository.findById(settlementId)
				.orElseThrow(ShipmentService::settlementNotFound);
		if (!isWinner(settlement, memberId) && !isConsignor(settlement, memberId) && role != MemberRole.ADMIN) {
			throw new ShipmentException(FORBIDDEN, "SHIPMENT_FORBIDDEN", "이 배송 정보를 볼 권한이 없습니다.");
		}
		return shipmentRepository.findBySettlementId(settlementId)
				.orElseThrow(() -> new ShipmentException(NOT_FOUND, "SHIPMENT_NOT_FOUND", "아직 배송지가 입력되지 않았습니다."));
	}

	private static boolean isWinner(Settlement settlement, Long memberId) {
		return Objects.equals(settlement.getWinnerId(), memberId);
	}

	// 역할(CONSIGNOR)만 보지 않고 이 정산의 작품을 맡긴 위탁자 본인인지 확인한다
	private boolean isConsignor(Settlement settlement, Long memberId) {
		return auctionRepository.findById(settlement.getAuctionId())
				.flatMap(auction -> artworkRepository.findById(auction.getArtworkId()))
				.map(artwork -> Objects.equals(artwork.getConsignorId(), memberId))
				.orElse(false);
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
