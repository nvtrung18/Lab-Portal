package com.web.labportalbackend.common.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;

/**
 * Automatically rolls back and seeds the large development database dataset
 * on application startup if enabled via application properties.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DevDataSeeder implements ApplicationRunner {

    private final DataSource dataSource;

    @Value("${app.dev-seed.enabled:false}")
    private boolean devSeedEnabled;

    @Override
    public void run(ApplicationArguments args) {
        if (!devSeedEnabled) {
            log.info("Development data seeder is disabled. Enable it by setting 'app.dev-seed.enabled=true' in application configuration.");
            return;
        }

        try {
            log.info("-----------------------------------------------------------------");
            log.info("DEV DATA SEEDER: Running automatic database rollback...");
            ResourceDatabasePopulator rollbackPopulator = new ResourceDatabasePopulator();
            rollbackPopulator.addScript(new ClassPathResource("db/dev/rollback-dev-large-data.sql"));
            rollbackPopulator.setSqlScriptEncoding(StandardCharsets.UTF_8.name());
            rollbackPopulator.execute(dataSource);
            log.info("DEV DATA SEEDER: Database rollback completed successfully.");

            log.info("DEV DATA SEEDER: Running automatic database seeding...");
            ResourceDatabasePopulator seedPopulator = new ResourceDatabasePopulator();
            seedPopulator.addScript(new ClassPathResource("db/dev/seed-dev-large-data.sql"));
            seedPopulator.setSqlScriptEncoding(StandardCharsets.UTF_8.name());
            seedPopulator.execute(dataSource);
            log.info("DEV DATA SEEDER: Database seeding completed successfully with UTF-8 encoding!");
            log.info("-----------------------------------------------------------------");
        } catch (Exception e) {
            log.error("DEV DATA SEEDER: Failed to execute automatic dev seeding!", e);
        }
    }
}
