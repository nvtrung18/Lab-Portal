package com.web.labportalbackend;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
public class LabPortalBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(LabPortalBackendApplication.class, args);
    }

    /**
     * Force JVM timezone to UTC to ensure consistent date/time handling
     * across all environments regardless of server locale.
     */
    @PostConstruct
    void setUTCTimezone() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }
}
