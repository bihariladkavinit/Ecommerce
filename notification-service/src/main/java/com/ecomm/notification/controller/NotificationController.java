package com.ecomm.notification.controller;

import com.ecomm.notification.dto.response.ErrorResponse;
import com.ecomm.notification.dto.response.NotificationLogResponse;
import com.ecomm.notification.entity.NotificationLog;
import com.ecomm.notification.repository.NotificationLogRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Admin-only REST controller for notification history lookup.
 *
 * <p>This is the only REST surface of the notification service.
 * All actual notification sending is Kafka-driven — this endpoint exists
 * solely for debugging and operational visibility.
 *
 * <p>Path: {@code GET /api/v1/notifications?userId=<email>}
 *
 * <p>Note: the {@code userId} query parameter is used as the recipient value
 * for the log lookup (the recipient stored in {@code notification_log} is the
 * user's email address). In a production system this would be resolved from
 * a user profile lookup; here the consumer stores the email directly.
 */
@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "Admin notification history lookup")
public class NotificationController {

    private final NotificationLogRepository notificationLogRepository;

    @Operation(
            summary = "Get notification history (Admin)",
            description = "Returns all notification log entries for the given recipient " +
                    "(email address), most-recent first. Requires ROLE_ADMIN.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Notification history returned"),
            @ApiResponse(responseCode = "403", description = "Insufficient role",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<NotificationLogResponse>> getNotifications(
            @Parameter(description = "Recipient email address to look up", required = true)
            @RequestParam String userId) {

        List<NotificationLogResponse> result =
                notificationLogRepository.findByRecipientOrderByCreatedAtDesc(userId)
                        .stream()
                        .map(this::toResponse)
                        .toList();

        return ResponseEntity.ok(result);
    }

    // ── Mapping ───────────────────────────────────────────────────────

    private NotificationLogResponse toResponse(NotificationLog log) {
        return NotificationLogResponse.builder()
                .id(log.getId())
                .type(log.getType())
                .recipient(log.getRecipient())
                .subject(log.getSubject())
                .status(log.getStatus())
                .createdAt(log.getCreatedAt())
                .build();
    }
}
