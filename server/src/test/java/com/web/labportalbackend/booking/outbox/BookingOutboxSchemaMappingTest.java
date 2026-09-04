package com.web.labportalbackend.booking.outbox;

import jakarta.persistence.Column;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class BookingOutboxSchemaMappingTest {

    @Test
    void eventIdMappingMatchesFlywayMigration() throws NoSuchFieldException, IOException {
        Column mapping = BookingOutboxEvent.class
                .getDeclaredField("eventId")
                .getAnnotation(Column.class);

        assertThat(mapping.columnDefinition()).isEqualTo("CHAR(36)");

        try (InputStream migrationStream = getClass().getClassLoader()
                .getResourceAsStream("db/migration/V68__add_booking_outbox.sql")) {
            assertThat(migrationStream).isNotNull();
            String migration = new String(migrationStream.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(migration).contains("event_id CHAR(36) PRIMARY KEY");
        }
    }
}
