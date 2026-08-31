package com.web.labportalbackend.notification.repository;

import com.web.labportalbackend.notification.entity.NotificationEntity;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<NotificationEntity, Long> {

    Page<NotificationEntity> findByRecipientIdOrderByCreatedAtDescIdDesc(Long recipientId, Pageable pageable);

    Optional<NotificationEntity> findByIdAndRecipientId(Long id, Long recipientId);

    long countByRecipientIdAndReadFalse(Long recipientId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update NotificationEntity n set n.read = true "
            + "where n.recipient.id = :recipientId and n.read = false")
    int markAllReadByRecipientId(@Param("recipientId") Long recipientId);
}
