package com.itsm.backend.notification;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByRecipientId(String recipientId, Pageable pageable);

    Page<Notification> findByRecipientIdAndReadAtIsNull(String recipientId, Pageable pageable);

    long countByRecipientIdAndReadAtIsNull(String recipientId);

    Optional<Notification> findByIdAndRecipientId(Long id, String recipientId);

    @Modifying
    @Query("update Notification n set n.readAt = :now where n.recipientId = :recipientId and n.readAt is null")
    int markAllRead(@Param("recipientId") String recipientId, @Param("now") Instant now);
}
