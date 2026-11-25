package com.artbid.auction.service;

import com.artbid.auction.config.BidRateLimitProperties;
import com.artbid.common.exception.BidRateLimitExceededException;
import com.artbid.infra.redis.SlidingWindowRateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BidRateLimiterTest {

	private SlidingWindowRateLimiter slidingWindowRateLimiter;
	private BidRateLimitProperties properties;
	private BidRateLimiter bidRateLimiter;

	@BeforeEach
	void setUp() {
		slidingWindowRateLimiter = mock(SlidingWindowRateLimiter.class);
		properties = new BidRateLimitProperties();
		bidRateLimiter = new BidRateLimiter(slidingWindowRateLimiter, properties);
	}

	@Test
	void 한도_안이면_통과한다() {
		when(slidingWindowRateLimiter.tryAcquire(eq("bid:rate:10"), eq(5), eq(Duration.ofSeconds(1))))
				.thenReturn(new SlidingWindowRateLimiter.Decision(true, 1, Duration.ZERO));

		assertThatCode(() -> bidRateLimiter.check(10L)).doesNotThrowAnyException();
	}

	@Test
	void 한도를_넘으면_남은_시간을_담아_거절한다() {
		when(slidingWindowRateLimiter.tryAcquire(anyString(), anyInt(), any()))
				.thenReturn(new SlidingWindowRateLimiter.Decision(false, 5, Duration.ofMillis(400)));

		assertThatThrownBy(() -> bidRateLimiter.check(10L))
				.isInstanceOf(BidRateLimitExceededException.class)
				.extracting("retryAfter")
				.isEqualTo(Duration.ofMillis(400));
	}

	@Test
	void Redis_장애면_제한_없이_통과한다() {
		when(slidingWindowRateLimiter.tryAcquire(anyString(), anyInt(), any()))
				.thenThrow(new RedisConnectionFailureException("down"));

		assertThatCode(() -> bidRateLimiter.check(10L)).doesNotThrowAnyException();
	}

	@Test
	void 설정으로_끄면_Redis를_호출하지_않는다() {
		properties.setRateLimitEnabled(false);

		bidRateLimiter.check(10L);

		verifyNoInteractions(slidingWindowRateLimiter);
	}
}
