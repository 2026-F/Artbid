package com.artbid.payment.service;

import com.artbid.payment.domain.Payment;
import com.artbid.payment.domain.PaymentMethod;
import com.artbid.payment.domain.PaymentStatus;
import com.artbid.payment.dto.PaymentResponse;
import com.artbid.payment.exception.PaymentException;
import com.artbid.payment.gateway.*;
import com.artbid.payment.repository.PaymentRepository;
import com.artbid.settlement.domain.Settlement;
import com.artbid.settlement.repository.SettlementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 로컬 PostgreSQL에서 고유 Schema를 만들고 종료 시 해당 Schema만 정리한다. */
@EnabledIfEnvironmentVariable(named = "PAYMENT_POSTGRES_TEST", matches = "true")
@SpringBootTest(classes = PaymentFlowPostgresTest.Config.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PaymentFlowPostgresTest {
	@Configuration
	@EnableAutoConfiguration
	@EntityScan(basePackageClasses = {Payment.class, Settlement.class})
	@EnableJpaRepositories(basePackageClasses = {PaymentRepository.class, SettlementRepository.class})
	@Import({PaymentService.class, PaymentTransactionService.class})
	static class Config {}

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		String schema = "payment_it_" + UUID.randomUUID().toString().replace("-", "");
		registry.add("spring.datasource.url", () -> System.getenv().getOrDefault("PAYMENT_TEST_DB_URL", "jdbc:postgresql://localhost:5432/artbid"));
		registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("PAYMENT_TEST_DB_USER", "artbid"));
		registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("PAYMENT_TEST_DB_PASSWORD", "artbid"));
		registry.add("spring.jpa.properties.hibernate.default_schema", () -> schema);
		registry.add("spring.jpa.properties.hibernate.hbm2ddl.create_namespaces", () -> "true");
		registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
		registry.add("spring.jpa.open-in-view", () -> "false");
		registry.add("spring.jpa.show-sql", () -> "false");
		registry.add("spring.datasource.hikari.connection-init-sql", () -> "SET lock_timeout = '3s'");
	}

	@Autowired PaymentService service;
	@Autowired PaymentTransactionService transactions;
	@Autowired PaymentRepository payments;
	@Autowired SettlementRepository settlements;
	@MockBean PaymentGateway gateway;

	@BeforeEach
	void setup() {
		doAnswer(call -> {
			assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
			Payment saved = payments.findById(call.getArgument(0)).orElseThrow();
			assertThat(saved.getStatus()).isEqualTo(PaymentStatus.REQUESTED);
			return new PaymentGateway.Result(true, "txn_" + saved.getId(), null);
		}).when(gateway).pay(anyLong(), anyLong());
	}

	private Settlement settlement(LocalDateTime deadline) {
		return settlements.saveAndFlush(Settlement.create(1L, 2L, 850_000, 85_000, 30_000, deadline));
	}

	@Test
	void 서버_금액으로_결제하고_Payment와_Settlement를_저장한다() {
		Settlement settlement = settlement(LocalDateTime.now().plusDays(1));
		PaymentResponse response = service.pay(settlement.getId(), 2L, PaymentMethod.MOCK_PG);
		assertThat(response.status()).isEqualTo(PaymentStatus.PAID);
		assertThat(response.amount()).isEqualTo(965_000L);
		assertThat(service.get(response.id(), 2L).paidAt()).isNotNull();
		assertThat(settlements.findById(settlement.getId()).orElseThrow().getPaymentStatus())
				.isEqualTo(com.artbid.settlement.domain.PaymentStatus.PAID);
		verify(gateway).pay(response.id(), 965_000L);
	}

	@Test
	void 거절된_시도는_실패로_남기고_새_시도로_재결제한다() {
		Settlement settlement = settlement(LocalDateTime.now().plusDays(1));
		doReturn(new PaymentGateway.Result(false, null, "승인 거절")).when(gateway).pay(anyLong(), anyLong());
		PaymentResponse failed = service.pay(settlement.getId(), 2L, PaymentMethod.MOCK_PG);
		assertThat(failed.status()).isEqualTo(PaymentStatus.FAILED);
		assertThat(settlements.findById(settlement.getId()).orElseThrow().getPaymentStatus())
				.isEqualTo(com.artbid.settlement.domain.PaymentStatus.PENDING);
		doReturn(new PaymentGateway.Result(true, "retry_txn", null)).when(gateway).pay(anyLong(), anyLong());
		PaymentResponse retry = service.pay(settlement.getId(), 2L, PaymentMethod.MOCK_PG);
		assertThat(retry.id()).isNotEqualTo(failed.id());
		assertThat(retry.status()).isEqualTo(PaymentStatus.PAID);
		assertThat(service.get(failed.id(), 2L).status()).isEqualTo(PaymentStatus.FAILED);
	}

	@Test
	void Timeout이면_REQUESTED를_유지하고_추가_PG_호출을_막는다() {
		Settlement settlement = settlement(LocalDateTime.now().plusDays(1));
		doThrow(new PaymentGatewayUnavailableException("timeout")).when(gateway).pay(anyLong(), anyLong());
		PaymentResponse pending = service.pay(settlement.getId(), 2L, PaymentMethod.MOCK_PG);
		assertThat(service.get(pending.id(), 2L).status()).isEqualTo(PaymentStatus.REQUESTED);
		assertThatThrownBy(() -> service.pay(settlement.getId(), 2L, PaymentMethod.MOCK_PG))
				.isInstanceOf(PaymentException.class).hasMessageContaining("진행 중");
		verify(gateway, times(1)).pay(anyLong(), anyLong());
	}

	@Test
	void 권한과_기한_검사에서_거절되면_PG를_호출하지_않는다() {
		Settlement active = settlement(LocalDateTime.now().plusDays(1));
		Settlement expired = settlement(LocalDateTime.now().minusSeconds(1));
		assertThatThrownBy(() -> service.pay(active.getId(), 3L, PaymentMethod.MOCK_PG))
				.isInstanceOf(PaymentException.class).hasMessageContaining("낙찰자");
		assertThatThrownBy(() -> service.pay(expired.getId(), 2L, PaymentMethod.MOCK_PG))
				.isInstanceOf(PaymentException.class).hasMessageContaining("결제 가능한");
		verifyNoInteractions(gateway);
	}

	@Test
	void 다른_회원은_결제_결과를_조회할_수_없다() {
		Settlement settlement = settlement(LocalDateTime.now().plusDays(1));
		PaymentResponse response = service.pay(settlement.getId(), 2L, PaymentMethod.MOCK_PG);
		assertThatThrownBy(() -> service.get(response.id(), 3L))
				.isInstanceOf(PaymentException.class).hasMessageContaining("낙찰자");
	}

	@Test
	void 결과_저장_실패시_Payment_변경도_rollback한다() {
		Settlement settlement = settlement(LocalDateTime.now().plusDays(1));
		PaymentResponse pending = transactions.start(settlement.getId(), 2L, PaymentMethod.MOCK_PG);
		// legacy/비정상 Settlement 상태로 결과 반영 중 실패를 발생시킨다.
		org.springframework.test.util.ReflectionTestUtils.setField(settlement, "paymentStatus",
				com.artbid.settlement.domain.PaymentStatus.FAILED);
		settlements.saveAndFlush(settlement);
		assertThatThrownBy(() -> transactions.complete(settlement.getId(), pending.id(),
				new PaymentGateway.Result(true, "txn_rollback", null))).isInstanceOf(IllegalStateException.class);
		assertThat(payments.findById(pending.id()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.REQUESTED);
		assertThat(payments.findById(pending.id()).orElseThrow().getTransactionId()).isNull();
	}

	@Test
	void 같은_성공_결과를_재반영해도_최초_결제_시각을_유지한다() {
		Settlement settlement = settlement(LocalDateTime.now().plusDays(1));
		PaymentResponse paid = service.pay(settlement.getId(), 2L, PaymentMethod.MOCK_PG);
		PaymentResponse repeated = transactions.complete(settlement.getId(), paid.id(),
				new PaymentGateway.Result(true, paid.transactionId(), null));
		assertThat(repeated.paidAt()).isEqualTo(paid.paidAt());
		assertThatThrownBy(() -> service.pay(settlement.getId(), 2L, PaymentMethod.MOCK_PG))
				.isInstanceOf(PaymentException.class);
		verify(gateway, times(1)).pay(anyLong(), anyLong());
	}

	@Test
	void 동시에_시작한_요청_중_하나만_PG를_호출한다() throws Exception {
		Settlement settlement = settlement(LocalDateTime.now().plusDays(1));
		ExecutorService executor = Executors.newFixedThreadPool(4);
		CountDownLatch ready = new CountDownLatch(4);
		CountDownLatch start = new CountDownLatch(1);
		java.util.List<Future<Boolean>> results = new java.util.ArrayList<>();
		try {
			for (int i = 0; i < 4; i++) {
				results.add(executor.submit(() -> {
					ready.countDown();
					if (!start.await(5, TimeUnit.SECONDS)) throw new AssertionError("start timeout");
					try {
						service.pay(settlement.getId(), 2L, PaymentMethod.MOCK_PG);
						return true;
					} catch (PaymentException e) {
						assertThat(e.getCode()).isIn("SETTLEMENT_NOT_PAYABLE", "PAYMENT_ALREADY_EXISTS");
						return false;
					}
				}));
			}
			assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
			start.countDown();
			int successes = 0;
			for (Future<Boolean> result : results) {
				if (result.get(10, TimeUnit.SECONDS)) successes++;
			}
			assertThat(successes).isEqualTo(1);
			verify(gateway, times(1)).pay(anyLong(), anyLong());
		} finally {
			start.countDown();
			executor.shutdownNow();
			assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
		}
	}

	@Test
	void PG_대기_중에도_두번째_요청은_중복으로_거절된다() throws Exception {
		Settlement settlement = settlement(LocalDateTime.now().plusDays(1));
		CountDownLatch entered = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		doAnswer(call -> {
			assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
			entered.countDown();
			if (!release.await(10, TimeUnit.SECONDS)) throw new AssertionError("PG release timeout");
			return new PaymentGateway.Result(true, "concurrent_txn", null);
		}).when(gateway).pay(anyLong(), anyLong());
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<PaymentResponse> first = executor.submit(() -> service.pay(settlement.getId(), 2L, PaymentMethod.MOCK_PG));
			assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
			Future<?> second = executor.submit(() -> service.pay(settlement.getId(), 2L, PaymentMethod.MOCK_PG));
			assertThatThrownBy(() -> second.get(2, TimeUnit.SECONDS))
					.isInstanceOf(ExecutionException.class).hasCauseInstanceOf(PaymentException.class);
			release.countDown();
			assertThat(first.get(5, TimeUnit.SECONDS).status()).isEqualTo(PaymentStatus.PAID);
			verify(gateway, times(1)).pay(anyLong(), anyLong());
		} finally {
			release.countDown();
			executor.shutdownNow();
			assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
		}
	}
}
