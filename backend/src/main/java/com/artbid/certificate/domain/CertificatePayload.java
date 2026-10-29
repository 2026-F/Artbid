package com.artbid.certificate.domain;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 보증서에서 서명하는 내용. 이 값 중 하나라도 바뀌면 서명 검증이 실패한다.
 *
 * 서명은 {@link #canonical()} 문자열에 대해 만든다. 같은 내용이면 언제나 같은 문자열이 나와야 하므로
 * - 필드 순서를 고정하고 이름=값 형태로 한 줄씩 쓴다.
 * - 값은 URL 인코딩한다. 제목에 줄바꿈이나 '='를 넣어 다른 필드처럼 보이게 하는 조작을 막는다.
 * - 숫자와 시각은 형식을 고정한다(60.0과 60, 나노초 차이 등으로 문자열이 달라지지 않게).
 * 필드를 추가하거나 형식을 바꾸면 기존 보증서 검증이 깨지므로 FORMAT_VERSION을 올리고 이전 형식도 계속 지원해야 한다.
 */
public record CertificatePayload(
		String serialNumber,
		Long artworkId,
		Long artistId,
		Long consignorId,
		String title,
		String medium,
		Double widthCm,
		Double heightCm,
		Double depthCm,
		Integer productionYear,
		String certificateUrl,
		LocalDateTime issuedAt,
		String keyVersion) {

	public static final String FORMAT_VERSION = "artbid-certificate-v1";
	private static final DateTimeFormatter ISSUED_AT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS");

	public String canonical() {
		Map<String, String> fields = new LinkedHashMap<>();
		fields.put("serialNumber", serialNumber);
		fields.put("artworkId", text(artworkId));
		fields.put("artistId", text(artistId));
		fields.put("consignorId", text(consignorId));
		fields.put("title", title);
		fields.put("medium", medium);
		fields.put("widthCm", number(widthCm));
		fields.put("heightCm", number(heightCm));
		fields.put("depthCm", number(depthCm));
		fields.put("productionYear", text(productionYear));
		fields.put("certificateUrl", certificateUrl);
		fields.put("issuedAt", issuedAt == null ? null : issuedAt.format(ISSUED_AT_FORMAT));
		fields.put("keyVersion", keyVersion);

		return FORMAT_VERSION + "\n" + fields.entrySet().stream()
				.map(entry -> entry.getKey() + "=" + encode(entry.getValue()))
				.collect(Collectors.joining("\n"));
	}

	private static String text(Object value) {
		return value == null ? null : value.toString();
	}

	private static String number(Double value) {
		return value == null ? null : BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
	}

	private static String encode(String value) {
		return value == null ? "" : URLEncoder.encode(value, StandardCharsets.UTF_8);
	}
}
