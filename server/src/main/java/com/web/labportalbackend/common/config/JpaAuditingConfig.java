package com.web.labportalbackend.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Enables JPA Auditing so that {@code @CreatedDate} and {@code @LastModifiedDate}
 * are automatically populated by Spring Data.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
