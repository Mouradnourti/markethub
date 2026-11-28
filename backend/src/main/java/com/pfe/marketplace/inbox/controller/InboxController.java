package com.pfe.marketplace.inbox.controller;

import com.pfe.marketplace.common.dto.PageResponse;
import com.pfe.marketplace.common.exception.UnauthorizedException;
import com.pfe.marketplace.inbox.dto.InboxDtos.NotificationDto;
import com.pfe.marketplace.inbox.dto.InboxDtos.StreamTicketDto;
import com.pfe.marketplace.inbox.dto.InboxDtos.UnreadCountDto;
import com.pfe.marketplace.inbox.service.InboxService;
import com.pfe.marketplace.inbox.service.LiveEventHub;
import com.pfe.marketplace.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Tag(name = "Notifications")
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class InboxController {

    private final InboxService inbox;
    private final LiveEventHub hub;

    @GetMapping
    public PageResponse<NotificationDto> list(@AuthenticationPrincipal AuthUser user,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "15") int size) {
        return inbox.list(user.id(), page, size);
    }

    @GetMapping("/unread-count")
    public UnreadCountDto unread(@AuthenticationPrincipal AuthUser user) {
        return new UnreadCountDto(inbox.unreadCount(user.id()));
    }

    @PostMapping("/{id}/read")
    public NotificationDto markRead(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return inbox.markRead(user.id(), id);
    }

    @PostMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAllRead(@AuthenticationPrincipal AuthUser user) {
        inbox.markAllRead(user.id());
    }

    @Operation(summary = "One-time ticket (valid 60 s) to open the live event stream")
    @PostMapping("/stream-ticket")
    public StreamTicketDto ticket(@AuthenticationPrincipal AuthUser user) {
        return new StreamTicketDto(hub.issueTicket(user.id()), 60);
    }

    @Operation(summary = "Server-sent events: 'notification' events for the ticket's user")
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestParam String ticket) {
        Long userId = hub.redeem(ticket)
                .orElseThrow(() -> new UnauthorizedException("This stream ticket is invalid or expired"));
        return hub.open(userId);
    }
}
