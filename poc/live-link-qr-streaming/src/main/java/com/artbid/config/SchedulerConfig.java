package com.artbid.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * 호가 자동 시뮬레이션(auction.service.BidService)에서 사용할 TaskScheduler.
 * 명시적으로 빈을 등록해서 자동설정 여부에 의존하지 않도록 한다.
 */
@Configuration
public class SchedulerConfig {

    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(5);
        scheduler.setThreadNamePrefix("bid-sim-");
        scheduler.initialize();
        return scheduler;
    }
}
