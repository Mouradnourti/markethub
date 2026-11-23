package com.pfe.marketplace.settings.service;

import com.pfe.marketplace.config.AppProperties;
import com.pfe.marketplace.settings.dto.SettingsDtos.PlatformSettingsDto;
import com.pfe.marketplace.settings.dto.SettingsDtos.PublicConfigDto;
import com.pfe.marketplace.settings.entity.PlatformSetting;
import com.pfe.marketplace.settings.repository.PlatformSettingRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SettingsService {

    static final String DEFAULT_COMMISSION = "commission.default_rate";
    static final String SHIPPING_FLAT_FEE = "shipping.flat_fee";
    static final String FREE_SHIPPING_THRESHOLD = "shipping.free_threshold";

    private final PlatformSettingRepository repository;
    private final AppProperties properties;

    @Transactional(readOnly = true)
    public PlatformSettingsDto get() {
        return new PlatformSettingsDto(decimal(DEFAULT_COMMISSION, "10"), decimal(SHIPPING_FLAT_FEE, "0"),
                decimal(FREE_SHIPPING_THRESHOLD, "0"));
    }

    @Transactional
    public PlatformSettingsDto update(PlatformSettingsDto settings) {
        save(DEFAULT_COMMISSION, settings.defaultCommissionRate());
        save(SHIPPING_FLAT_FEE, settings.shippingFlatFee());
        save(FREE_SHIPPING_THRESHOLD, settings.freeShippingThreshold());
        return get();
    }

    @Transactional(readOnly = true)
    public PublicConfigDto publicConfig() {
        PlatformSettingsDto settings = get();
        AppProperties.Stripe stripe = properties.stripe();
        boolean stripeEnabled = stripe != null && stripe.enabled();
        return new PublicConfigDto(properties.currency(), stripeEnabled,
                stripeEnabled ? stripe.publishableKey() : null,
                settings.shippingFlatFee(), settings.freeShippingThreshold());
    }

    /** Flat fee per checkout, waived above the free-shipping threshold. */
    @Transactional(readOnly = true)
    public BigDecimal shippingFeeFor(BigDecimal subtotal) {
        PlatformSettingsDto settings = get();
        if (subtotal.signum() == 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        boolean free = settings.freeShippingThreshold().signum() > 0
                && subtotal.compareTo(settings.freeShippingThreshold()) >= 0;
        return free ? BigDecimal.ZERO.setScale(2) : settings.shippingFlatFee().setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal decimal(String key, String fallback) {
        return new BigDecimal(repository.findById(key).map(PlatformSetting::getValue).orElse(fallback));
    }

    private void save(String key, BigDecimal value) {
        repository.save(new PlatformSetting(key, value.setScale(2, RoundingMode.HALF_UP).toPlainString()));
    }
}
