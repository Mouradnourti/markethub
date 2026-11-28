package com.pfe.marketplace.stats.service;

import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/** Counts product page views per day, off the request thread. Only aggregated counts are stored. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductViewService {

    private final JdbcTemplate jdbc;
    private final Clock clock;

    @Async
    public void record(Long productId) {
        try {
            jdbc.update("""
                    insert into product_views (product_id, day, views) values (?, ?, 1)
                    on conflict (product_id, day) do update set views = product_views.views + 1""",
                    productId, Date.valueOf(LocalDate.now(clock.withZone(ZoneOffset.UTC))));
        } catch (RuntimeException e) {
            log.debug("Could not record a view of product {}: {}", productId, e.getMessage());
        }
    }
}
