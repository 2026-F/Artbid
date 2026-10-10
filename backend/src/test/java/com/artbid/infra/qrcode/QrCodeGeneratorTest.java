package com.artbid.infra.qrcode;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;

import static org.assertj.core.api.Assertions.assertThat;

class QrCodeGeneratorTest {

	private final QrCodeGenerator qrCodeGenerator = new QrCodeGenerator();

	@Test
	void PNG_이미지를_만들고_디코딩하면_원래_내용이_나온다() throws Exception {
		String content = "http://localhost:3000/bid/7";

		byte[] png = qrCodeGenerator.generatePng(content);

		assertThat(png[0]).isEqualTo((byte) 0x89); // PNG 시그니처
		assertThat(decode(png)).isEqualTo(content);
	}

	@Test
	void 크기를_지정하면_그_크기로_만들어진다() throws Exception {
		byte[] png = qrCodeGenerator.generatePng("http://localhost:3000/bid/1", 200);

		BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
		assertThat(image.getWidth()).isEqualTo(200);
		assertThat(image.getHeight()).isEqualTo(200);
	}

	private String decode(byte[] png) throws Exception {
		BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
		BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(image)));
		Result result = new MultiFormatReader().decode(bitmap);
		return result.getText();
	}
}
