package com.web.labportalbackend.booking.outbox;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface BookingOutboxRepository extends JpaRepository<BookingOutboxEvent, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT event FROM BookingOutboxEvent event
            WHERE (event.status = com.web.labportalbackend.booking.outbox.OutboxStatus.PENDING
                    AND event.nextAttemptAt <= :now)
               OR (event.status = com.web.labportalbackend.booking.outbox.OutboxStatus.PROCESSING
                    AND event.lockedAt <= :staleBefore)
            ORDER BY event.createdAt, event.eventId
            """)
    List<BookingOutboxEvent> findReadyForUpdate(
            @Param("now") Instant now,
            @Param("staleBefore") Instant staleBefore,
            Pageable pageable);

    long countByStatus(OutboxStatus status);

    @Modifying
    @Query("DELETE FROM BookingOutboxEvent event WHERE event.status = :status AND event.deliveredAt < :cutoff")
    int deleteDeliveredBefore(@Param("status") OutboxStatus status, @Param("cutoff") Instant cutoff);
}
