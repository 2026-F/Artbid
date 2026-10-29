package com.artbid.shipping.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 낙찰 건(정산) 하나의 배송 정보. 정산 하나당 하나.
 * 흐름: 낙찰자가 배송지 입력(READY) → 위탁자 또는 관리자가 운송장 등록(SHIPPED).
 * 수령인 이름, 연락처, 주소는 개인정보다. 로그와 예외 메시지에 값을 넣지 않는다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(uniqueConstraints = @UniqueConstraint(name = "uk_shipment_settlement", columnNames = "settlementId"))
public class Shipment {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private Long settlementId;

	@Column(length = 50)
	private String recipientName;
	@Column(length = 20)
	private String recipientPhone;
	@Column(length = 10)
	private String zipCode;
	@Column(length = 200)
	private String address;
	@Column(length = 200)
	private String addressDetail;
	@Column(length = 200)
	private String requestMemo;

	@Column(length = 30)
	private String carrier;
	@Column(length = 30)
	private String trackingNumber;

	@Enumerated(EnumType.STRING)
	private ShipmentStatus status;

	private LocalDateTime createdAt;
	private LocalDateTime updatedAt;
	private LocalDateTime shippedAt;

	public static Shipment create(Long settlementId, ShippingAddress address, LocalDateTime now) {
		if (settlementId == null || settlementId <= 0 || address == null || now == null) {
			throw new IllegalArgumentException("정산 ID, 배송지, 시각은 필수입니다.");
		}
		Shipment shipment = new Shipment();
		shipment.settlementId = settlementId;
		shipment.apply(address);
		shipment.status = ShipmentStatus.READY;
		shipment.createdAt = now;
		shipment.updatedAt = now;
		return shipment;
	}

	/** 배송지 수정. 발송 후에는 바꿀 수 없다(이미 출발한 택배의 주소가 바뀐 것처럼 보이면 안 되므로). */
	public void changeAddress(ShippingAddress address, LocalDateTime now) {
		if (status != ShipmentStatus.READY) {
			throw new IllegalStateException("발송 후에는 배송지를 바꿀 수 없습니다.");
		}
		apply(address);
		updatedAt = now;
	}

	/**
	 * 운송장 등록. 발송 후 다시 등록하면 운송장 정보만 고친다(오타 수정). 최초 발송 시각은 유지한다.
	 */
	public void registerTracking(String carrier, String trackingNumber, LocalDateTime now) {
		if (status == ShipmentStatus.DELIVERED) {
			throw new IllegalStateException("수령 완료된 배송의 운송장은 바꿀 수 없습니다.");
		}
		if (isBlank(carrier) || isBlank(trackingNumber)) {
			throw new IllegalArgumentException("택배사와 운송장 번호는 필수입니다.");
		}
		this.carrier = carrier.trim();
		this.trackingNumber = trackingNumber.trim();
		if (shippedAt == null) {
			shippedAt = now;
		}
		status = ShipmentStatus.SHIPPED;
		updatedAt = now;
	}

	private void apply(ShippingAddress address) {
		if (isBlank(address.recipientName()) || isBlank(address.recipientPhone())
				|| isBlank(address.zipCode()) || isBlank(address.address())) {
			throw new IllegalArgumentException("수령인, 연락처, 우편번호, 주소는 필수입니다.");
		}
		this.recipientName = address.recipientName().trim();
		this.recipientPhone = address.recipientPhone().trim();
		this.zipCode = address.zipCode().trim();
		this.address = address.address().trim();
		this.addressDetail = trimToNull(address.addressDetail());
		this.requestMemo = trimToNull(address.requestMemo());
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	private static String trimToNull(String value) {
		return isBlank(value) ? null : value.trim();
	}
}
