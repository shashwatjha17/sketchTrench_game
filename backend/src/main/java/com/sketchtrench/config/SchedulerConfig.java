package com.sketchtrench.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Threading + transaction infrastructure for the game timers. Round-end callbacks run on
 * a dedicated scheduler thread pool, and the persistence they do runs through a
 * TransactionTemplate (the scheduler thread is outside Spring's request transaction).
 */
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

    @Bean
    public TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }
}
