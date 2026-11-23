package com.pfe.marketplace.stats.service;

import com.pfe.marketplace.stats.dto.StatsDtos.MonthlyPoint;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/** Shared helpers for aggregate SQL: count maps and zero-filled monthly series. */
@Component
class StatsQueries {

    /** Shop-order statuses that count as a sale (money is or will be collected). */
    static final String SOLD_STATUSES = "('PENDING','CONFIRMED','SHIPPED','DELIVERED')";

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    StatsQueries(NamedParameterJdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    NamedParameterJdbcTemplate jdbc() {
        return jdbc;
    }

    Map<String, Long> countBy(String sql, MapSqlParameterSource params) {
        Map<String, Long> result = new LinkedHashMap<>();
        jdbc.query(sql, params, rs -> {
            result.put(rs.getString(1), rs.getLong(2));
        });
        return result;
    }

    BigDecimal decimal(String sql, MapSqlParameterSource params) {
        BigDecimal value = jdbc.queryForObject(sql, params, BigDecimal.class);
        return value == null ? BigDecimal.ZERO : value;
    }

    long count(String sql, MapSqlParameterSource params) {
        Long value = jdbc.queryForObject(sql, params, Long.class);
        return value == null ? 0 : value;
    }

    /** Runs a query returning (month 'YYYY-MM', revenue, commission, orders) and fills missing months with zeros. */
    List<MonthlyPoint> lastTwelveMonths(String sql, MapSqlParameterSource params) {
        Map<String, MonthlyPoint> byMonth = new LinkedHashMap<>();
        jdbc.query(sql, params, rs -> {
            String month = rs.getString("month");
            byMonth.put(month, new MonthlyPoint(month, rs.getBigDecimal("revenue"), rs.getBigDecimal("commission"),
                    rs.getLong("orders")));
        });
        List<MonthlyPoint> series = new ArrayList<>(12);
        YearMonth current = YearMonth.now(clock.withZone(ZoneOffset.UTC));
        for (int i = 11; i >= 0; i--) {
            String month = current.minusMonths(i).toString();
            series.add(byMonth.getOrDefault(month, new MonthlyPoint(month, BigDecimal.ZERO, BigDecimal.ZERO, 0)));
        }
        return series;
    }
}
