package com.artbid.settlement.controller;

import com.artbid.settlement.domain.FeePolicy;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FeePolicyControllerTest {

	@Test
	void 비로그인도_수수료율과_배송비를_조회한다() throws Exception {
		MockMvc mvc = MockMvcBuilders.standaloneSetup(new FeePolicyController()).build();

		mvc.perform(get("/api/fee-policy"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.buyerPremiumRate").value(0.10))
				.andExpect(jsonPath("$.shippingFee").value(30000));
	}

	@Test
	void 수수료는_기존_정산과_같이_반올림한다() {
		assertThat(FeePolicy.buyerPremiumOf(15L)).isEqualTo(2L); // 1.5 -> 2
		assertThat(FeePolicy.buyerPremiumOf(1_234_567L)).isEqualTo(123_457L);
	}
}
