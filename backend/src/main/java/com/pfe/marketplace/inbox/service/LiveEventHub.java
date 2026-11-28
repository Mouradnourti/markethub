package com.pfe.marketplace.inbox.service;

import java.io.IOException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Server-sent event connections per user. Browsers open the stream with a short-lived, single-use ticket obtained
 * with their JWT, so the token never appears in a URL. Connections are kept in memory: with several backend
 * instances, a Redis pub/sub relay would be needed (documented in docs/architecture.md).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LiveEventHub {

    static final Duration TICKET_TTL = Duration.ofSeconds(60);
    private static final Duration STREAM_TIMEOUT = Duration.ofMinutes(30);
    private static final int MAX_STREAMS_PER_USER = 5;

    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Ticket> tickets = new ConcurrentHashMap<>();
    private final Map<Long, List<SseEmitter>> streams = new ConcurrentHashMap<>();

    private record Ticket(Long userId, Instant expiresAt) {
    }

    public String issueTicket(Long userId) {
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tickets.put(ticket, new Ticket(userId, clock.instant().plus(TICKET_TTL)));
        return ticket;
    }

    /** Consumes the ticket and returns its user, or empty when it is unknown, used or expired. */
    public Optional<Long> redeem(String ticket) {
        if (ticket == null) {
            return Optional.empty();
        }
        Ticket found = tickets.remove(ticket);
        return found == null || found.expiresAt().isBefore(clock.instant())
                ? Optional.empty() : Optional.of(found.userId());
    }

    public SseEmitter open(Long userId) {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT.toMillis());
        List<SseEmitter> userStreams = streams.computeIfAbsent(userId, id -> new CopyOnWriteArrayList<>());
        while (userStreams.size() >= MAX_STREAMS_PER_USER) {
            userStreams.removeFirst().complete();
        }
        userStreams.add(emitter);
        Runnable remove = () -> userStreams.remove(emitter);
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(e -> remove.run());
        send(userId, emitter, "ready", Map.of("connected", true));
        return emitter;
    }

    public void publish(Long userId, String eventName, Object payload) {
        streams.getOrDefault(userId, List.of()).forEach(emitter -> send(userId, emitter, eventName, payload));
    }

    public int totalConnections() {
        return streams.values().stream().mapToInt(List::size).sum();
    }

    public int connectionCount(Long userId) {
        return streams.getOrDefault(userId, List.of()).size();
    }

    /** Keeps idle connections open through proxies and drops expired tickets. */
    @Scheduled(fixedRate = 25_000)
    void heartbeat() {
        Instant now = clock.instant();
        tickets.values().removeIf(t -> t.expiresAt().isBefore(now));
        streams.forEach((userId, emitters) -> emitters.forEach(emitter -> {
            try {
                emitter.send(SseEmitter.event().comment("ping"));
            } catch (IOException | IllegalStateException e) {
                emitters.remove(emitter);
            }
        }));
    }

    private void send(Long userId, SseEmitter emitter, String eventName, Object payload) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(payload));
        } catch (IOException | IllegalStateException e) {
            log.debug("Dropping a closed event stream of user {}", userId);
            streams.getOrDefault(userId, List.of()).remove(emitter);
        }
    }
}
