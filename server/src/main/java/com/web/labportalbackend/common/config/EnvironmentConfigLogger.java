package com.web.labportalbackend.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class EnvironmentConfigLogger implements ApplicationRunner {

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${spring.mail.password:}")
    private String mailPassword;

    @Value("${app.mail.from:}")
    private String mailFrom;

    @Value("${spring.data.redis.host:}")
    private String redisHost;

    @Override
    public void run(ApplicationArguments args) {
        log.info("MAIL_USERNAME = {}", blankToNotConfigured(mailUsername));
        log.info("MAIL_PASSWORD configured = {}", mailPassword != null && !mailPassword.isBlank());
        log.info("MAIL_FROM = {}", blankToNotConfigured(mailFrom));
        log.info("REDIS_HOST = {}", blankToNotConfigured(redisHost));
    }

    private String blankToNotConfigured(String value) {
        return value == null || value.isBlank() ? "(not configured)" : value;
    }
}
