package com.pfe.marketplace.ai.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.pfe.marketplace.ai.dto.AiDtos.ReturnRiskFeatures;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ReturnRiskFeatureBuilderTest {

    private static final Instant ACCOUNT = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void firstOrderHasNoHistory() {
        ReturnRiskFeatures f = new ReturnRiskFeatureBuilder().features(80, 2, 1,
                Instant.parse("2026-01-10T23:30:00Z"), ACCOUNT, null);

        assertThat(f.firstOrder()).isEqualTo(1);
        assertThat(f.priorOrders()).isZero();
        assertThat(f.priorReturnRate()).isZero();
        assertThat(f.accountAgeDays()).isEqualTo(9);
        assertThat(f.hour()).isEqualTo(23);
        assertThat(f.isWeekend()).isEqualTo(1); // 2026-01-10 is a Saturday
        assertThat(f.city()).isEqualTo("unknown");
    }

    @Test
    void onlyFinalCodOrdersCountTowardsTheReturnRate() {
        ReturnRiskFeatureBuilder builder = new ReturnRiskFeatureBuilder();
        builder.record(outcome(true, true, true, false));   // refused COD parcel
        builder.record(outcome(true, false, true, false));  // delivered COD parcel
        builder.record(outcome(true, false, false, false)); // still in transit: ignored for the rate
        builder.record(outcome(false, false, true, false)); // card order: ignored for the rate
        builder.record(outcome(true, false, true, true));   // cancelled before shipping: ignored

        ReturnRiskFeatures f = builder.features(30, 1, 1, Instant.parse("2026-02-02T10:00:00Z"), ACCOUNT, "Lyon");

        assertThat(f.priorOrders()).isEqualTo(5);
        assertThat(f.priorReturns()).isEqualTo(1);
        assertThat(f.priorReturnRate()).isEqualTo(0.5);
        assertThat(f.firstOrder()).isZero();
        assertThat(f.isWeekend()).isZero();
    }

    private static CustomerHistoryQueries.OrderOutcome outcome(boolean cod, boolean returned, boolean finalState,
                                                               boolean allCancelled) {
        return new CustomerHistoryQueries.OrderOutcome(1, 1, ACCOUNT, ACCOUNT, cod, 10, 1, 1, "Lyon", returned,
                finalState, allCancelled);
    }
}
