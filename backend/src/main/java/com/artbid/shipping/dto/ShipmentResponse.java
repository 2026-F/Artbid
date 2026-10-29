package com.artbid.shipping.dto;

import com.artbid.shipping.domain.Shipment;
import com.artbid.shipping.domain.ShipmentStatus;

import java.time.LocalDateTime;

/** 배송 정보 응답. 낙찰자, 해당 작품 위탁자, 관리자에게만 내려간다(발송에 주소가 필요). */
public record ShipmentResponse(
		Long id,
		Long settlementId,
		String recipientName,
		String recipientPhone,
		String zipCode,
		String address,
		String addressDetail,
		String requestMemo,
		String carrier,
		String trackingNumber,
		ShipmentStatus status,
		LocalDateTime shippedAt,
		LocalDateTime updatedAt) {

	public static ShipmentResponse from(Shipment shipment) {
		return new ShipmentResponse(
				shipment.getId(),
				shipment.getSettlementId(),
				shipment.getRecipientName(),
				shipment.getRecipientPhone(),
				shipment.getZipCode(),
				shipment.getAddress(),
				shipment.getAddressDetail(),
				shipment.getRequestMemo(),
				shipment.getCarrier(),
				shipment.getTrackingNumber(),
				shipment.getStatus(),
				shipment.getShippedAt(),
				shipment.getUpdatedAt());
	}

	@Override
	public String toString() {
		return "ShipmentResponse[id=" + id + ", settlementId=" + settlementId + ", status=" + status + ", masked]";
	}
}
