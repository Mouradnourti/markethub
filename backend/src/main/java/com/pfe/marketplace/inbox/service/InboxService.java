package com.pfe.marketplace.inbox.service;

import com.pfe.marketplace.common.dto.PageResponse;
import com.pfe.marketplace.common.exception.NotFoundException;
import com.pfe.marketplace.inbox.dto.InboxDtos.LiveNotificationEvent;
import com.pfe.marketplace.inbox.dto.InboxDtos.NotificationDto;
import com.pfe.marketplace.inbox.entity.NotificationType;
import com.pfe.marketplace.inbox.entity.UserNotification;
import com.pfe.marketplace.inbox.repository.UserNotificationRepository;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** In-app notifications: stored for the bell menu and pushed live to the user's open tabs. */
@Service
@RequiredArgsConstructor
public class InboxService {

    public static final String LIVE_EVENT = "notification";

    private final UserNotificationRepository repository;
    private final LiveEventHub hub;
    private final Clock clock;

    @Transactional
    public NotificationDto notify(Long userId, NotificationType type, String title, String body, String link) {
        UserNotification notification = new UserNotification();
        notification.setUserId(userId);
        notification.setType(type);
        notification.setTitle(truncate(title, 160));
        notification.setBody(truncate(body, 500));
        notification.setLink(link);
        NotificationDto dto = toDto(repository.saveAndFlush(notification));
        afterCommit(() -> hub.publish(userId, LIVE_EVENT,
                new LiveNotificationEvent(dto, repository.countByUserIdAndReadAtIsNull(userId))));
        return dto;
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationDto> list(Long userId, int page, int size) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 50),
                Sort.by("createdAt").descending().and(Sort.by("id").descending()));
        return PageResponse.of(repository.findByUserId(userId, pageable), InboxService::toDto);
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        return repository.countByUserIdAndReadAtIsNull(userId);
    }

    @Transactional
    public NotificationDto markRead(Long userId, Long notificationId) {
        UserNotification notification = repository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new NotFoundException("Notification", notificationId));
        if (notification.getReadAt() == null) {
            notification.setReadAt(clock.instant());
        }
        return toDto(notification);
    }

    @Transactional
    public void markAllRead(Long userId) {
        repository.markAllRead(userId, clock.instant());
    }

    private static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }

    private static NotificationDto toDto(UserNotification n) {
        return new NotificationDto(n.getId(), n.getType(), n.getTitle(), n.getBody(), n.getLink(), n.getReadAt() != null,
                n.getCreatedAt());
    }
}
