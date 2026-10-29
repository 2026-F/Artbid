package com.artbid.certificate.service;

import com.artbid.artwork.domain.Artwork;
import com.artbid.artwork.domain.ArtworkCategory;
import com.artbid.artwork.repository.ArtworkRepository;
import com.artbid.certificate.domain.ArtworkCertificate;
import com.artbid.certificate.domain.CertificateStatus;
import com.artbid.certificate.dto.CertificateVerifyResponse.Result;
import com.artbid.certificate.repository.ArtworkCertificateRepository;
import com.artbid.member.domain.MemberRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

// 로컬 docker-compose의 postgres·redis가 떠 있어야 통과함 (BidServiceConcurrencyTest와 같은 조건, CI는 서비스 컨테이너 사용)
// 실제 DB에서 발급, 검증, DB 직접 변조 감지, 재발급, 동시 발급을 확인한다.
@SpringBootTest
class CertificatePostgresTest {

	@Autowired
	CertificateService certificateService;
	@Autowired
	ArtworkRepository artworkRepository;
	@Autowired
	ArtworkCertificateRepository certificateRepository;
	@Autowired
	JdbcTemplate jdbcTemplate;

	// DB에 이전 실행 데이터가 남아 있어도 겹치지 않도록 매번 다른 위탁자 ID를 쓴다
	final long consignorId = ThreadLocalRandom.current().nextLong(1_000_000_000L, Long.MAX_VALUE / 2);

	Long artworkId() {
		return artworkRepository.save(Artwork.builder()
				.consignorId(consignorId).artistId(3L).title("보증서 테스트").category(ArtworkCategory.PAINTING)
				.medium("캔버스에 유채").widthCm(72.7).heightCm(90.9).productionYear(2024).startPrice(1_000_000L)
				.build()).getId();
	}

	@Test
	void 발급한_보증서는_검증되고_DB를_직접_고치면_검증에_실패한다() {
		ArtworkCertificate issued = certificateService.issue(artworkId(), consignorId, MemberRole.CONSIGNOR);

		assertThat(certificateService.verify(issued.getSerialNumber(), issued.getSignature()).result()).isEqualTo(Result.VALID);

		jdbcTemplate.update("update artwork_certificate set production_year = 1950 where id = ?", issued.getId());

		assertThat(certificateService.verify(issued.getSerialNumber(), issued.getSignature()).result())
				.isEqualTo(Result.SIGNATURE_MISMATCH);
	}

	@Test
	void 재발급하면_이전_보증서는_폐기되고_현재_보증서가_바뀐다() {
		Long artworkId = artworkId();
		ArtworkCertificate first = certificateService.issue(artworkId, consignorId, MemberRole.CONSIGNOR);
		ArtworkCertificate second = certificateService.issue(artworkId, consignorId, MemberRole.CONSIGNOR);

		assertThat(certificateService.verify(first.getSerialNumber(), first.getSignature()).result()).isEqualTo(Result.REVOKED);
		assertThat(certificateService.getActive(artworkId).getSerialNumber()).isEqualTo(second.getSerialNumber());
	}

	@Test
	void 같은_작품에_동시에_발급해도_유효한_보증서는_하나다() throws InterruptedException {
		Long artworkId = artworkId();
		int threads = 5;
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		CountDownLatch start = new CountDownLatch(1);
		CountDownLatch done = new CountDownLatch(threads);
		for (int i = 0; i < threads; i++) {
			pool.submit(() -> {
				try {
					start.await();
					certificateService.issue(artworkId, consignorId, MemberRole.CONSIGNOR);
				} catch (Exception ignored) {
				} finally {
					done.countDown();
				}
			});
		}
		start.countDown();
		assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
		pool.shutdown();

		assertThat(certificateRepository.findByArtworkIdAndStatus(artworkId, CertificateStatus.ACTIVE)).hasSize(1);
	}
}
