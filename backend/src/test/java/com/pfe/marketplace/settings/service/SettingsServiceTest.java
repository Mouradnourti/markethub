package com.pfe.marketplace.settings.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.pfe.marketplace.config.AppProperties;
import com.pfe.marketplace.settings.entity.PlatformSetting;
import com.pfe.marketplace.settings.repository.PlatformSettingRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SettingsServiceTest {

    @Mock PlatformSettingRepository repository;
    SettingsService service;

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties("eur", null, "key", null, null, null,
                new AppProperties.Stripe("sk_test_x", "pk_test_x", ""), null, null, null);
        service = new SettingsService(repository, properties);
        when(repository.findById(SettingsService.SHIPPING_FLAT_FEE))
                .thenReturn(Optional.of(new PlatformSetting(SettingsService.SHIPPING_FLAT_FEE, "4.99")));
        when(repository.findById(SettingsService.FREE_SHIPPING_THRESHOLD))
                .thenReturn(Optional.of(new PlatformSetting(SettingsService.FREE_SHIPPING_THRESHOLD, "50.00")));
    }

    @Test
    void shippingIsFlatBelowThresholdAndFreeAbove() {
        assertThat(service.shippingFeeFor(new BigDecimal("49.99"))).isEqualByComparingTo("4.99");
        assertThat(service.shippingFeeFor(new BigDecimal("50.00"))).isEqualByComparingTo("0");
        assertThat(service.shippingFeeFor(BigDecimal.ZERO)).isEqualByComparingTo("0");
    }

    @Test
    void missingCommissionFallsBackToTenPercent() {
        assertThat(service.get().defaultCommissionRate()).isEqualByComparingTo("10");
    }

    @Test
    void publicConfigExposesPublishableKeyOnlyWhenStripeIsEnabled() {
        var config = service.publicConfig();
        assertThat(config.stripeEnabled()).isTrue();
        assertThat(config.stripePublishableKey()).isEqualTo("pk_test_x");
        assertThat(config.currency()).isEqualTo("eur");
    }
}
