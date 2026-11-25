package com.artbid.media.service;

import com.artbid.media.domain.ArtworkMedia;
import com.artbid.media.domain.TranscodeStatus;
import com.artbid.media.repository.ArtworkMediaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
class TranscodeDispatcher {

	// AWS 응답 유실 직후 작업 이력이 갱신될 시간을 두고, 서버 종료 시 처리권을 회수한다.
	private static final int CLAIM_SECONDS = 120;

	record TranscodeRequested(Long mediaId) {
	}

	private record Claim(ArtworkMedia media, boolean recovery) {
	}

	private final ArtworkMediaRepository repository;
	private final TranscodeService transcodeService;
	private final TransactionTemplate transactions;
	private final TaskExecutor executor;
	private final Set<Long> scheduled = ConcurrentHashMap.newKeySet();
	private final Semaphore submissionSlots = new Semaphore(8);
	private final AtomicBoolean refreshing = new AtomicBoolean();

	TranscodeDispatcher(ArtworkMediaRepository repository, TranscodeService transcodeService,
			PlatformTransactionManager transactionManager, @Qualifier("applicationTaskExecutor") TaskExecutor executor) {
		this.repository = repository;
		this.transcodeService = transcodeService;
		this.transactions = new TransactionTemplate(transactionManager);
		this.executor = executor;
	}

	@TransactionalEventListener
	public void afterCommit(TranscodeRequested request) {
		schedule(request.mediaId());
	}

	void retryPending() {
		repository.findPendingTranscodeIds(TranscodeStatus.PROCESSING, Instant.now(), PageRequest.of(0, 50))
				.forEach(this::schedule);
	}

	void scheduleStatusRefresh() {
		if (!refreshing.compareAndSet(false, true)) {
			return;
		}
		try {
			executor.execute(() -> {
				try {
					transcodeService.refreshProcessingJobs();
				} catch (RuntimeException e) {
					log.warn("[Transcode] 처리 중인 작업 상태 조회 실패", e);
				} finally {
					refreshing.set(false);
				}
			});
		} catch (RuntimeException e) {
			refreshing.set(false);
			log.warn("[Transcode] 상태 조회 실행을 다음 폴링으로 미룹니다", e);
		}
	}

	private void schedule(Long mediaId) {
		if (!scheduled.add(mediaId)) {
			return;
		}
		if (!submissionSlots.tryAcquire()) {
			scheduled.remove(mediaId);
			return;
		}
		try {
			executor.execute(() -> {
				try {
					dispatch(mediaId);
				} finally {
					submissionSlots.release();
					scheduled.remove(mediaId);
				}
			});
		} catch (RuntimeException e) {
			submissionSlots.release();
			scheduled.remove(mediaId);
			log.warn("[Transcode] media {} 요청 실행을 다음 폴링으로 미룹니다", mediaId, e);
		}
	}

	private void dispatch(Long mediaId) {
		try {
			Claim claim = transactions.execute(status -> claim(mediaId));
			if (claim == null) {
				return;
			}

			// 처리권과 요청 시각을 확정한 뒤 DB 트랜잭션 없이 AWS를 호출한다.
			ArtworkMedia media = claim.media();
			if (claim.recovery()) {
				transcodeService.recoverSubmission(media);
			} else {
				transcodeService.start(media, media.getObjectKey());
			}
			transactions.executeWithoutResult(status -> repository.findByIdForUpdate(mediaId)
					.filter(current -> media.getTranscodeClaimUntil().equals(current.getTranscodeClaimUntil()))
					.ifPresent(current -> recordResult(current, media)));
		} catch (RuntimeException e) {
			log.warn("[Transcode] media {} 접수 결과를 다음 폴링에서 확인합니다", mediaId, e);
		}
	}

	private Claim claim(Long mediaId) {
		ArtworkMedia media = repository.findByIdForUpdate(mediaId).orElse(null);
		Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
		if (media == null || media.getTranscodeStatus() != TranscodeStatus.PROCESSING
				|| media.getObjectKey() == null || media.getTranscodeJobId() != null
				|| (media.getTranscodeClaimUntil() != null && media.getTranscodeClaimUntil().isAfter(now))) {
			return null;
		}

		boolean recovery = media.getTranscodeFirstAttemptAt() != null;
		media.claimTranscode(now, now.plusSeconds(CLAIM_SECONDS));
		return new Claim(media, recovery);
	}

	private void recordResult(ArtworkMedia current, ArtworkMedia result) {
		switch (result.getTranscodeStatus()) {
			case PROCESSING -> current.startTranscode(result.getTranscodeJobId());
			case SKIPPED -> current.skipTranscode();
			case FAILED -> current.failTranscode(result.getTranscodeFailureReason());
			default -> throw new IllegalStateException("예상하지 않은 트랜스코딩 요청 상태: " + result.getTranscodeStatus());
		}
		current.finishTranscodeSubmission();
	}
}
