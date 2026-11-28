package com.pfe.marketplace.promotion.entity;

import com.pfe.marketplace.shop.entity.Shop;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

/** A discount code created by a seller. It only discounts items sold by that seller's shop. */
@Entity
@Table(name = "coupons")
@Getter
@Setter
@NoArgsConstructor
public class Coupon {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shop_id", nullable = false)
    private Shop shop;

    @Column(nullable = false, length = 30)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private CouponType type;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal value;

    @Column(name = "min_subtotal", precision = 12, scale = 2)
    private BigDecimal minSubtotal;

    @Column(name = "starts_at")
    private Instant startsAt;

    @Column(name = "ends_at")
    private Instant endsAt;

    @Column(name = "max_uses")
    private Integer maxUses;

    @Column(name = "used_count", nullable = false)
    private int usedCount;

    @Column(name = "once_per_customer", nullable = false)
    private boolean oncePerCustomer = true;

    /** Featured codes are advertised on the shop's product pages. */
    @Column(nullable = false)
    private boolean featured;

    @Column(nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public boolean isLiveAt(Instant now) {
        return active
                && (startsAt == null || !now.isBefore(startsAt))
                && (endsAt == null || now.isBefore(endsAt))
                && !isExhausted();
    }

    public boolean isExhausted() {
        return maxUses != null && usedCount >= maxUses;
    }

    /** Discount on the given shop subtotal, never more than the subtotal itself. */
    public BigDecimal discountOn(BigDecimal eligibleSubtotal) {
        BigDecimal discount = type == CouponType.PERCENT
                ? eligibleSubtotal.multiply(value).divide(HUNDRED, 2, RoundingMode.HALF_UP)
                : value;
        return discount.min(eligibleSubtotal).setScale(2, RoundingMode.HALF_UP);
    }

    /** Short customer-facing label, e.g. "10% off" or "€5.00 off". */
    public String label(String currencySymbol) {
        return type == CouponType.PERCENT
                ? value.stripTrailingZeros().toPlainString() + "% off"
                : currencySymbol + value.setScale(2, RoundingMode.HALF_UP).toPlainString() + " off";
    }
}
