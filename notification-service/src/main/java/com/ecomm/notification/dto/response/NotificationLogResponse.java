package com.ecomm.notification.dto.response;

import com.ecomm.notification.entity.NotificationStatus;
import com.ecomm.notification.entity.NotificationType;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response DTO for the admin notification history endpoint
 * ({@code GET /notifications?userId=}).
 *
 * <p>The raw {@code payload} JSONB column is deliberately excluded here —
 * it is available via direct DB query for debugging and would bloat the
 * API response unnecessarily.
 */
@Data
@Builder
public class NotificationLogResponse {

    private UUID               id;
    private NotificationType   type;
    private String             recipient;
    private String             subject;
    private NotificationStatus status;
    private OffsetDateTime     createdAt;
}
