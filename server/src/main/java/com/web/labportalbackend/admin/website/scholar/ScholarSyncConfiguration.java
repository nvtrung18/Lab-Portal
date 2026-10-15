package com.web.labportalbackend.admin.website.scholar;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class ScholarSyncConfiguration {
    @Bean(name = "scholarSyncExecutor")
    public ThreadPoolTaskExecutor scholarSyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1); executor.setMaxPoolSize(1); executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("scholar-sync-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        return executor;
    }
}
