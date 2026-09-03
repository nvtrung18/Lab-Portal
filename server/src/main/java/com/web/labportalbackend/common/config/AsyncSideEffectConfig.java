package com.web.labportalbackend.common.config;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Slf4j
@Configuration
@EnableAsync
public class AsyncSideEffectConfig {

    @Bean(name = "sideEffectExecutor")
    public Executor sideEffectExecutor(
            MeterRegistry meterRegistry,
            @Value("${app.async.side-effects.core-pool-size:2}") int corePoolSize,
            @Value("${app.async.side-effects.max-pool-size:4}") int maxPoolSize,
            @Value("${app.async.side-effects.queue-capacity:200}") int queueCapacity
    ) {
        int normalizedCorePoolSize = Math.max(1, corePoolSize);
        int normalizedMaxPoolSize = Math.max(normalizedCorePoolSize, maxPoolSize);
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(normalizedCorePoolSize);
        executor.setMaxPoolSize(normalizedMaxPoolSize);
        executor.setQueueCapacity(Math.max(1, queueCapacity));
        executor.setThreadNamePrefix("side-effect-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.setRejectedExecutionHandler((task, pool) -> {
            meterRegistry.counter("application.side_effect.executor.rejected").increment();
            log.error("Side-effect executor rejected a task; active={}, queued={}",
                    pool.getActiveCount(), pool.getQueue().size());
        });
        executor.initialize();
        return executor;
    }
}
