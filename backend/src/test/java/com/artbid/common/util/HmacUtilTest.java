package com.artbid.common.util;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HmacUtilTest {

	private static final byte[] KEY = "key".getBytes(StandardCharsets.UTF_8);
	private static final String MESSAGE = "The quick brown fox jumps over the lazy dog";

	@Test
	void 알려진_HMAC_SHA256_값과_같다() {
		// 널리 쓰이는 HMAC-SHA256 예시 값 (key="key", 위 문장)
		byte[] expected = HexFormat.of().parseHex("f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8");

		String signature = HmacUtil.sign(KEY, MESSAGE);

		assertThat(Base64.getUrlDecoder().decode(signature)).isEqualTo(expected);
		assertThat(signature).doesNotContain("=", "+", "/"); // URL에 넣을 수 있는 형태
	}

	@Test
	void 같은_키와_내용이면_검증에_성공한다() {
		String signature = HmacUtil.sign(KEY, MESSAGE);

		assertThat(HmacUtil.verify(KEY, MESSAGE, signature)).isTrue();
	}

	@Test
	void 내용이_한_글자라도_바뀌면_검증에_실패한다() {
		String signature = HmacUtil.sign(KEY, MESSAGE);

		assertThat(HmacUtil.verify(KEY, MESSAGE + ".", signature)).isFalse();
	}

	@Test
	void 키가_다르면_검증에_실패한다() {
		String signature = HmacUtil.sign(KEY, MESSAGE);

		assertThat(HmacUtil.verify("other".getBytes(StandardCharsets.UTF_8), MESSAGE, signature)).isFalse();
	}

	@Test
	void 서명이_비었거나_형식이_틀리면_예외_없이_실패한다() {
		assertThat(HmacUtil.verify(KEY, MESSAGE, null)).isFalse();
		assertThat(HmacUtil.verify(KEY, MESSAGE, " ")).isFalse();
		assertThat(HmacUtil.verify(KEY, MESSAGE, "가나다")).isFalse();
	}

	@Test
	void 키가_비어_있으면_서명하지_않는다() {
		assertThatThrownBy(() -> HmacUtil.sign(new byte[0], MESSAGE)).isInstanceOf(IllegalArgumentException.class);
	}
}
