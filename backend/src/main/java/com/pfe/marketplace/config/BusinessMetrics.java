package com.pfe.marketplace.config;

import com.pfe.marketplace.inbox.service.LiveEventHub;
import com.pfe.marketplace.order.event.OrderEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Locale;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Marketplace metrics next to the technical ones (HTTP latency, JVM, connection pool) on /actuator/prometheus:
 * order lifecycle events and open live-update connections. Shown on the Grafana dashboard in monitoring/.
 */
@Component
public class BusinessMetrics {

    private final MeterRegistry registry;

    public BusinessMetrics(MeterRegistry registry, LiveEventHub hub) {
        this.registry = registry;
        Gauge.builder("markethub.live.connections", hub, LiveEventHub::totalConnections)
                .description("Open server-sent event streams")
                .register(registry);
    }

    @EventListener
    public void onOrderEvent(OrderEvent event) {
        Counter.builder("markethub.orders.events")
                .description("Order lifecycle events (placed, shipped, delivered, cancelled, returned)")
                .tag("type", event.type().name().replace("ORDER_", "").toLowerCase(Locale.ROOT))
                .register(registry)
                .increment();
    }
}
