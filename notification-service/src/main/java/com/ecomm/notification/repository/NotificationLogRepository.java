package com.ecomm.notification.repository;

import com.ecomm.notification.entity.NotificationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Data access for {@link NotificationLog}.
 *
 * <p>{@link #findByRecipientOrderByCreatedAtDesc} is used by the admin
 * endpoint to retrieve notification history for a given recipient
 * (email address or phone number).
 */
@Repository
public interface NotificationLogRepository extends JpaRepository<NotificationLog, UUID> {

    /**
     * Returns all notification log entries for the given recipient,
     * most-recent first.
     *
     * @param recipient email address or phone number to look up
     * @return ordered list of log entries, may be empty
     */
    List<NotificationLog> findByRecipientOrderByCreatedAtDesc(String recipient);
}
