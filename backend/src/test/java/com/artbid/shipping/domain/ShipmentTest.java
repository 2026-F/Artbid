package com.artbid.shipping.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ShipmentTest {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 29, 12, 0);

	private ShippingAddress address(String detail) {
		return new ShippingAddress(" 김수령 ", "010-1234-5678", "04524", "서울 중구 세종대로 110", detail, "  ");
	}

	@Test
	void 배송지를_입력하면_발송_대기_상태가_된다() {
		Shipment shipment = Shipment.create(1L, address("3층"), NOW);

		assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.READY);
		assertThat(shipment.getRecipientName()).isEqualTo("김수령"); // 앞뒤 공백 제거
		assertThat(shipment.getAddressDetail()).isEqualTo("3층");
		assertThat(shipment.getRequestMemo()).isNull(); // 빈 값은 null
		assertThat(shipment.getTrackingNumber()).isNull();
	}

	@Test
	void 필수_항목이_비면_거절한다() {
		ShippingAddress noPhone = new ShippingAddress("김수령", " ", "04524", "서울", null, null);

		assertThatThrownBy(() -> Shipment.create(1L, noPhone, NOW)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void 발송_전에는_배송지를_바꿀_수_있다() {
		Shipment shipment = Shipment.create(1L, address("3층"), NOW);

		shipment.changeAddress(address("5층"), NOW.plusHours(1));

		assertThat(shipment.getAddressDetail()).isEqualTo("5층");
		assertThat(shipment.getUpdatedAt()).isEqualTo(NOW.plusHours(1));
	}

	@Test
	void 운송장을_등록하면_발송_완료가_된다() {
		Shipment shipment = Shipment.create(1L, address(null), NOW);

		shipment.registerTracking(" CJ대한통운 ", " 123456789012 ", NOW.plusDays(1));

		assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.SHIPPED);
		assertThat(shipment.getCarrier()).isEqualTo("CJ대한통운");
		assertThat(shipment.getTrackingNumber()).isEqualTo("123456789012");
		assertThat(shipment.getShippedAt()).isEqualTo(NOW.plusDays(1));
	}

	@Test
	void 운송장을_다시_등록하면_번호만_고치고_발송_시각은_유지한다() {
		Shipment shipment = Shipment.create(1L, address(null), NOW);
		shipment.registerTracking("CJ대한통운", "111", NOW.plusDays(1));

		shipment.registerTracking("CJ대한통운", "222", NOW.plusDays(2));

		assertThat(shipment.getTrackingNumber()).isEqualTo("222");
		assertThat(shipment.getShippedAt()).isEqualTo(NOW.plusDays(1));
	}

	@Test
	void 발송_후에는_배송지를_바꿀_수_없다() {
		Shipment shipment = Shipment.create(1L, address(null), NOW);
		shipment.registerTracking("CJ대한통운", "111", NOW.plusDays(1));

		assertThatThrownBy(() -> shipment.changeAddress(address("5층"), NOW.plusDays(2)))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void 배송지_값은_toString에_노출되지_않는다() {
		assertThat(address("3층").toString()).doesNotContain("김수령", "010", "세종대로");
	}
}
