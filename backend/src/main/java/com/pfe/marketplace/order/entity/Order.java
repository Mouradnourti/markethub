package com.pfe.marketplace.order.entity;

import com.pfe.marketplace.user.entity.User;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** A customer checkout: one payment, split into one {@link ShopOrder} per seller. */
@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String reference;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 10)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 20)
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    /** Sum of the coupon discounts of the parcels. Total = subtotal - discountTotal + shippingFee. */
    @Column(name = "discount_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountTotal = BigDecimal.ZERO;

    @Column(name = "shipping_fee", nullable = false, precision = 12, scale = 2)
    private BigDecimal shippingFee;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    @Column(name = "shipping_full_name", nullable = false, length = 160)
    private String shippingFullName;

    @Column(name = "shipping_phone", nullable = false, length = 30)
    private String shippingPhone;

    @Column(name = "shipping_address_line", nullable = false)
    private String shippingAddressLine;

    @Column(name = "shipping_city", nullable = false, length = 100)
    private String shippingCity;

    @Column(name = "shipping_postal_code", nullable = false, length = 20)
    private String shippingPostalCode;

    @Column(name = "shipping_country", nullable = false, length = 80)
    private String shippingCountry;

    /** Probability (0..1) that a COD order is refused or returned, computed by the AI service. */
    @Column(name = "return_risk_score", precision = 5, scale = 4)
    private BigDecimal returnRiskScore;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id asc")
    private List<ShopOrder> shopOrders = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public void addShopOrder(ShopOrder shopOrder) {
        shopOrder.setOrder(this);
        shopOrders.add(shopOrder);
    }

    /**
     * Derives the payment status from the shop orders. COD is paid once any parcel is delivered
     * (cash collected); a paid card order whose parcels were all cancelled or returned is refunded.
     */
    public void recomputePaymentStatus() {
        boolean allReleased = shopOrders.stream().allMatch(so -> so.getStatus().releasesStock());
        boolean anyDelivered = shopOrders.stream().anyMatch(so -> so.getStatus() == OrderStatus.DELIVERED);
        if (paymentMethod == PaymentMethod.COD) {
            if (anyDelivered) {
                paymentStatus = PaymentStatus.PAID;
            } else if (allReleased) {
                paymentStatus = PaymentStatus.FAILED;
            }
        } else if (allReleased && paymentStatus == PaymentStatus.PAID) {
            paymentStatus = PaymentStatus.REFUNDED;
        } else if (allReleased && paymentStatus == PaymentStatus.PENDING) {
            paymentStatus = PaymentStatus.FAILED;
        }
    }
}
