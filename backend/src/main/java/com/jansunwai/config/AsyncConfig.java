package com.jansunwai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * AI calls are slow (hundreds of ms to seconds), so they run on a small bounded pool after the complaint is saved.
 * The citizen gets the tracking ID immediately; the AI result arrives a moment later.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "aiExecutor")
    public Executor aiExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(2);
        ex.setMaxPoolSize(4);
        ex.setQueueCapacity(200);       // bounded: a flood of complaints cannot exhaust memory
        ex.setThreadNamePrefix("ai-");
        ex.initialize();
        return ex;
    }
}
