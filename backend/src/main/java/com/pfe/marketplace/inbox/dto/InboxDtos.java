package com.pfe.marketplace.inbox.dto;

import com.pfe.marketplace.inbox.entity.NotificationType;
import java.time.Instant;

public final class InboxDtos {

    private InboxDtos() {
    }

    public record NotificationDto(Long id, NotificationType type, String title, String body, String link, boolean read,
                                  Instant createdAt) {
    }

    public record UnreadCountDto(long unread) {
    }

    /** One-time ticket to open the event stream (EventSource cannot send an Authorization header). */
    public record StreamTicketDto(String ticket, long expiresInSeconds) {
    }

    /** Pushed on the event stream when a notification is created. */
    public record LiveNotificationEvent(NotificationDto notification, long unread) {
    }
}
