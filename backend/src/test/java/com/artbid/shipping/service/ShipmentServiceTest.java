package com.artbid.shipping.service;

import com.artbid.settlement.domain.Settlement;
import com.artbid.settlement.repository.SettlementRepository;
import com.artbid.shipping.domain.Shipment;
import com.artbid.shipping.domain.ShipmentStatus;
import com.artbid.shipping.domain.ShippingAddress;
import com.artbid.shipping.exception.ShipmentException;
import com.artbid.shipping.repository.ShipmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShipmentServiceTest {

	static final Long SETTLEMENT_ID = 1L;
	static final Long WINNER_ID = 2L;
	static final Long CONSIGNOR_ID = 3L;
	static final Long STRANGER_ID = 4L;

	@Mock
	SettlementRepository settlementRepository;
	@Mock
	ShipmentRepository shipmentRepository;

	ShipmentService service;

	@BeforeEach
	void setUp() {
		service = new ShipmentService(settlementRepository, shipmentRepository);
	}

	static Settlement settlement() {
		Settlement settlement = Settlement.create(5L, WINNER_ID, 100_000, 10_000, 30_000, LocalDateTime.now().plusDays(3));
		ReflectionTestUtils.setField(settlement, "id", SETTLEMENT_ID);
		return settlement;
	}

	static ShippingAddress address(String detail) {
		return new ShippingAddress("김수령", "010-1234-5678", "04524", "서울 중구 세종대로 110", detail, null);
	}

	static void assertError(Runnable call, HttpStatus status, String code) {
		assertThatThrownBy(call::run)
				.isInstanceOfSatisfying(ShipmentException.class, e -> {
					assertThat(e.getStatus()).isEqualTo(status);
					assertThat(e.getCode()).isEqualTo(code);
				});
	}

	@Test
	void 낙찰자가_처음_입력하면_배송_정보를_만든다() {
		when(settlementRepository.findByIdForUpdate(SETTLEMENT_ID)).thenReturn(Optional.of(settlement()));
		when(shipmentRepository.findBySettlementId(SETTLEMENT_ID)).thenReturn(Optional.empty());
		when(shipmentRepository.save(any(Shipment.class))).thenAnswer(invocation -> invocation.getArgument(0));

		Shipment shipment = service.saveAddress(SETTLEMENT_ID, WINNER_ID, address("3층"));

		assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.READY);
		assertThat(shipment.getAddressDetail()).isEqualTo("3층");
	}

	@Test
	void 이미_있으면_배송지를_고친다() {
		Shipment existing = Shipment.create(SETTLEMENT_ID, address("3층"), LocalDateTime.now());
		when(settlementRepository.findByIdForUpdate(SETTLEMENT_ID)).thenReturn(Optional.of(settlement()));
		when(shipmentRepository.findBySettlementId(SETTLEMENT_ID)).thenReturn(Optional.of(existing));

		Shipment shipment = service.saveAddress(SETTLEMENT_ID, WINNER_ID, address("5층"));

		assertThat(shipment).isSameAs(existing);
		assertThat(shipment.getAddressDetail()).isEqualTo("5층");
		verify(shipmentRepository, never()).save(any());
	}

	@Test
	void 발송된_뒤에는_배송지를_고칠_수_없다() {
		Shipment shipped = Shipment.create(SETTLEMENT_ID, address("3층"), LocalDateTime.now());
		shipped.registerTracking("CJ대한통운", "123", LocalDateTime.now());
		when(settlementRepository.findByIdForUpdate(SETTLEMENT_ID)).thenReturn(Optional.of(settlement()));
		when(shipmentRepository.findBySettlementId(SETTLEMENT_ID)).thenReturn(Optional.of(shipped));

		assertError(() -> service.saveAddress(SETTLEMENT_ID, WINNER_ID, address("5층")),
				HttpStatus.CONFLICT, "SHIPMENT_ALREADY_SHIPPED");
	}

	@Test
	void 낙찰자가_아니면_입력할_수_없다() {
		when(settlementRepository.findByIdForUpdate(SETTLEMENT_ID)).thenReturn(Optional.of(settlement()));

		assertError(() -> service.saveAddress(SETTLEMENT_ID, CONSIGNOR_ID, address(null)),
				HttpStatus.FORBIDDEN, "SHIPMENT_FORBIDDEN");
	}

	@Test
	void 결제_실패나_기한_만료된_정산은_입력할_수_없다() {
		Settlement failed = settlement();
		ReflectionTestUtils.setField(failed, "paymentStatus", com.artbid.settlement.domain.PaymentStatus.FAILED);
		when(settlementRepository.findByIdForUpdate(SETTLEMENT_ID)).thenReturn(Optional.of(failed));

		assertError(() -> service.saveAddress(SETTLEMENT_ID, WINNER_ID, address(null)),
				HttpStatus.CONFLICT, "SETTLEMENT_NOT_SHIPPABLE");
	}

	@Test
	void 로그인하지_않으면_401() {
		assertError(() -> service.saveAddress(SETTLEMENT_ID, null, address(null)), HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED");
	}

	@Test
	void 정산이_없으면_404() {
		when(settlementRepository.findByIdForUpdate(SETTLEMENT_ID)).thenReturn(Optional.empty());

		assertError(() -> service.saveAddress(SETTLEMENT_ID, WINNER_ID, address(null)), HttpStatus.NOT_FOUND, "SETTLEMENT_NOT_FOUND");
	}
}
