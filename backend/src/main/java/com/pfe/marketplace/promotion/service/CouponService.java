package com.pfe.marketplace.promotion.service;

import com.pfe.marketplace.common.exception.BusinessException;
import com.pfe.marketplace.common.exception.ConflictException;
import com.pfe.marketplace.common.exception.NotFoundException;
import com.pfe.marketplace.common.util.MoneyFormat;
import com.pfe.marketplace.config.AppProperties;
import com.pfe.marketplace.promotion.dto.PromotionDtos.AppliedCouponDto;
import com.pfe.marketplace.promotion.dto.PromotionDtos.CouponDto;
import com.pfe.marketplace.promotion.dto.PromotionDtos.CouponRequest;
import com.pfe.marketplace.promotion.dto.PromotionDtos.CouponStatus;
import com.pfe.marketplace.promotion.dto.PromotionDtos.OfferDto;
import com.pfe.marketplace.promotion.entity.Coupon;
import com.pfe.marketplace.promotion.entity.CouponType;
import com.pfe.marketplace.promotion.repository.CouponRepository;
import com.pfe.marketplace.shop.entity.Shop;
import com.pfe.marketplace.shop.service.ShopService;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Seller discount codes: management, validation against a cart, and redemption at checkout. */
@Service
@RequiredArgsConstructor
public class CouponService {

    private final CouponRepository repository;
    private final ShopService shopService;
    private final AppProperties properties;
    private final Clock clock;
    private final MeterRegistry metrics;

    /** One cart line as seen by the coupon rules. */
    public record Line(Long shopId, BigDecimal total) {
    }

    /** A valid code for this cart and the amount it takes off the shop's items. */
    public record Quote(Coupon coupon, BigDecimal discount) {
        public AppliedCouponDto toDto(String currencySymbol) {
            return new AppliedCouponDto(coupon.getCode(), coupon.getShop().getId(), coupon.getShop().getName(),
                    coupon.label(currencySymbol), discount);
        }
    }

    @Transactional(readOnly = true)
    public List<CouponDto> list(Long sellerId) {
        Shop shop = shopService.requireApprovedShop(sellerId);
        return repository.findByShopIdOrderByCreatedAtDesc(shop.getId()).stream().map(this::toDto).toList();
    }

    @Transactional
    public CouponDto create(Long sellerId, CouponRequest request) {
        Shop shop = shopService.requireApprovedShop(sellerId);
        String code = normalize(request.code());
        if (repository.existsByCode(code)) {
            throw new ConflictException("The code " + code + " is already taken");
        }
        Coupon coupon = new Coupon();
        coupon.setShop(shop);
        coupon.setCode(code);
        apply(coupon, request);
        return toDto(repository.save(coupon));
    }

    @Transactional
    public CouponDto update(Long sellerId, Long couponId, CouponRequest request) {
        Coupon coupon = findOwned(sellerId, couponId);
        String code = normalize(request.code());
        if (!code.equals(coupon.getCode())) {
            if (coupon.getUsedCount() > 0) {
                throw new BusinessException("A code that was already used cannot be renamed");
            }
            if (repository.existsByCode(code)) {
                throw new ConflictException("The code " + code + " is already taken");
            }
            coupon.setCode(code);
        }
        apply(coupon, request);
        return toDto(coupon);
    }

    @Transactional
    public void delete(Long sellerId, Long couponId) {
        repository.delete(findOwned(sellerId, couponId));
    }

    /** Featured, currently valid codes of a shop, best first. */
    @Transactional(readOnly = true)
    public List<OfferDto> offers(Long shopId) {
        Instant now = clock.instant();
        return repository.findByShopIdAndFeaturedTrueAndActiveTrue(shopId).stream()
                .filter(c -> c.isLiveAt(now))
                .sorted(Comparator.comparing(Coupon::getType).thenComparing(Coupon::getValue, Comparator.reverseOrder()))
                .map(c -> new OfferDto(c.getCode(), c.label(currencySymbol()), c.getShop().getName(), c.getMinSubtotal(),
                        c.getEndsAt()))
                .toList();
    }

    /**
     * Checks a code against the cart and returns the discount. Every refusal explains itself, so the customer
     * knows what to change.
     */
    @Transactional(readOnly = true)
    public Quote quote(String rawCode, Long customerId, Collection<Line> lines) {
        String code = normalize(rawCode);
        Coupon coupon = repository.findByCode(code)
                .orElseThrow(() -> new BusinessException("The code " + code + " does not exist"));
        Instant now = clock.instant();
        if (!coupon.isActive()) {
            throw new BusinessException("The code " + code + " is no longer active");
        }
        if (coupon.getStartsAt() != null && now.isBefore(coupon.getStartsAt())) {
            throw new BusinessException("The code " + code + " is not valid yet");
        }
        if (coupon.getEndsAt() != null && !now.isBefore(coupon.getEndsAt())) {
            throw new BusinessException("The code " + code + " has expired");
        }
        if (coupon.isExhausted()) {
            throw new BusinessException("The code " + code + " has reached its usage limit");
        }
        if (coupon.isOncePerCustomer() && repository.usedByCustomer(coupon.getId(), customerId)) {
            throw new BusinessException("You have already used the code " + code);
        }
        Shop shop = coupon.getShop();
        BigDecimal eligible = lines.stream().filter(l -> l.shopId().equals(shop.getId()))
                .map(Line::total).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (eligible.signum() == 0) {
            throw new BusinessException("The code " + code + " only applies to products sold by " + shop.getName());
        }
        if (coupon.getMinSubtotal() != null && eligible.compareTo(coupon.getMinSubtotal()) < 0) {
            throw new BusinessException("Spend at least %s%s at %s to use the code %s".formatted(currencySymbol(),
                    coupon.getMinSubtotal().setScale(2, RoundingMode.HALF_UP).toPlainString(), shop.getName(), code));
        }
        return new Quote(coupon, coupon.discountOn(eligible));
    }

    /** Counts the redemption atomically, so a limited code can never be used more often than allowed. */
    @Transactional
    public void redeem(Coupon coupon) {
        if (repository.redeem(coupon.getId()) == 0) {
            throw new BusinessException("The code " + coupon.getCode() + " has reached its usage limit");
        }
        metrics.counter("markethub.coupons.redeemed").increment();
    }

    /** Gives a use back when every parcel that used the code was cancelled before shipping. */
    @Transactional
    public void release(Long couponId) {
        if (couponId != null) {
            repository.release(couponId);
        }
    }

    public String currencySymbol() {
        return MoneyFormat.symbol(properties.currency());
    }

    private Coupon findOwned(Long sellerId, Long couponId) {
        Shop shop = shopService.requireApprovedShop(sellerId);
        return repository.findByIdAndShopId(couponId, shop.getId())
                .orElseThrow(() -> new NotFoundException("Coupon", couponId));
    }

    private static void apply(Coupon coupon, CouponRequest request) {
        if (request.type() == CouponType.PERCENT && request.value().compareTo(BigDecimal.valueOf(90)) > 0) {
            throw new BusinessException("A percentage discount cannot exceed 90%");
        }
        if (request.startsAt() != null && request.endsAt() != null && !request.endsAt().isAfter(request.startsAt())) {
            throw new BusinessException("The end date must be after the start date");
        }
        if (request.maxUses() != null && request.maxUses() < coupon.getUsedCount()) {
            throw new BusinessException("The usage limit cannot be lower than the " + coupon.getUsedCount()
                    + " uses already made");
        }
        coupon.setType(request.type());
        coupon.setValue(request.value().setScale(2, RoundingMode.HALF_UP));
        coupon.setMinSubtotal(request.minSubtotal() == null || request.minSubtotal().signum() == 0
                ? null : request.minSubtotal().setScale(2, RoundingMode.HALF_UP));
        coupon.setStartsAt(request.startsAt());
        coupon.setEndsAt(request.endsAt());
        coupon.setMaxUses(request.maxUses());
        coupon.setOncePerCustomer(request.oncePerCustomer() == null || request.oncePerCustomer());
        coupon.setFeatured(Boolean.TRUE.equals(request.featured()));
        coupon.setActive(request.active() == null || request.active());
    }

    private CouponDto toDto(Coupon c) {
        return new CouponDto(c.getId(), c.getCode(), c.getType(), c.getValue(), c.getMinSubtotal(), c.getStartsAt(),
                c.getEndsAt(), c.getMaxUses(), c.getUsedCount(), c.isOncePerCustomer(), c.isFeatured(), c.isActive(),
                statusOf(c, clock.instant()), c.label(currencySymbol()), c.getCreatedAt());
    }

    static CouponStatus statusOf(Coupon c, Instant now) {
        if (!c.isActive()) {
            return CouponStatus.PAUSED;
        }
        if (c.getEndsAt() != null && !now.isBefore(c.getEndsAt())) {
            return CouponStatus.EXPIRED;
        }
        if (c.isExhausted()) {
            return CouponStatus.USED_UP;
        }
        if (c.getStartsAt() != null && now.isBefore(c.getStartsAt())) {
            return CouponStatus.SCHEDULED;
        }
        return CouponStatus.ACTIVE;
    }

    private static String normalize(String code) {
        if (code == null || code.isBlank()) {
            throw new BusinessException("Enter a discount code");
        }
        return code.trim().toUpperCase(Locale.ROOT);
    }
}
