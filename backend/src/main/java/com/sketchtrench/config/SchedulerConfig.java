package com.sketchtrench.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/** Dedicated pool for game timers (round end, ticker, disconnect reap). */
@Configuration
public class SchedulerConfig {

    @Bean
    @Primary
    public TaskScheduler gameTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4);
        scheduler.setThreadNamePrefix("game-");
        scheduler.initialize();
        return scheduler;
    }
}
