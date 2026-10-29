package com.artbid.notification.service;

import com.artbid.notification.domain.Notification;
import com.artbid.notification.domain.NotificationType;
import com.artbid.notification.dto.NotificationPageResponse;
import com.artbid.notification.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

// 로컬 docker-compose의 postgres·redis가 떠 있어야 통과함 (BidServiceConcurrencyTest와 같은 조건, CI는 서비스 컨테이너 사용)
// 커밋 후 생성, 롤백 시 미생성, 목록, 모두 읽음(일괄 UPDATE)을 실제 DB로 확인한다.
@SpringBootTest
class NotificationPostgresTest {

	@Autowired
	ApplicationEventPublisher eventPublisher;
	@Autowired
	TransactionTemplate transactionTemplate;
	@Autowired
	NotificationService notificationService;
	@Autowired
	NotificationRepository notificationRepository;

	// DB에 이전 실행 데이터가 남아 있어도 겹치지 않도록 매번 다른 회원 ID를 쓴다
	final long memberId = ThreadLocalRandom.current().nextLong(1_000_000_000L, Long.MAX_VALUE / 2);

	NotificationRequestedEvent request(String title) {
		return new NotificationRequestedEvent(memberId, NotificationType.PAYMENT_COMPLETED, title, "내용", "/mypage");
	}

	@Test
	void 요청한_트랜잭션이_커밋되면_알림이_생기고_롤백되면_생기지_않는다() {
		transactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(request("커밋된 결제")));
		transactionTemplate.executeWithoutResult(status -> {
			eventPublisher.publishEvent(request("롤백된 결제"));
			status.setRollbackOnly();
		});

		NotificationPageResponse page = notificationService.getMyNotifications(memberId, 0, 20);
		assertThat(page.content()).extracting("title").containsExactly("커밋된 결제");
		assertThat(page.unreadCount()).isEqualTo(1);
	}

	@Test
	void 목록은_최신순이고_모두_읽음은_내_알림만_한번에_바꾼다() {
		for (int i = 1; i <= 3; i++) {
			eventPublisher.publishEvent(request("알림 " + i)); // 트랜잭션 밖이면 바로 생성
		}
		long otherMember = memberId + 1;
		eventPublisher.publishEvent(new NotificationRequestedEvent(otherMember, NotificationType.SYSTEM, "다른 회원", null, null));

		assertThat(notificationService.getMyNotifications(memberId, 0, 2).content())
				.extracting("title").containsExactly("알림 3", "알림 2");

		assertThat(notificationService.markAllRead(memberId)).isEqualTo(3);
		assertThat(notificationService.countUnread(memberId)).isZero();
		assertThat(notificationService.countUnread(otherMember)).isEqualTo(1);

		// 다시 눌러도 바뀌는 것이 없다
		assertThat(notificationService.markAllRead(memberId)).isZero();
		Notification first = notificationRepository.findByMemberIdOrderByIdDesc(memberId, org.springframework.data.domain.PageRequest.of(0, 1))
				.getContent().get(0);
		assertThat(first.isRead()).isTrue();
	}
}
