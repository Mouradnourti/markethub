package com.pfe.marketplace.order.entity;

import com.pfe.marketplace.shop.entity.Shop;
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

/** The part of an order fulfilled by one shop. Sellers only ever see their own shop orders. */
@Entity
@Table(name = "shop_orders")
@Getter
@Setter
@NoArgsConstructor
public class ShopOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shop_id", nullable = false)
    private Shop shop;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "commission_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal commissionRate;

    @Column(name = "commission_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal commissionAmount;

    /** Coupon used on this parcel; the code is copied so history survives the coupon being deleted. */
    @Column(name = "coupon_id")
    private Long couponId;

    @Column(name = "coupon_code", length = 30)
    private String couponCode;

    /** Amount taken off the items by the coupon. {@link #subtotal} is already net of it. */
    @Column(name = "discount_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "tracking_number", length = 60)
    private String trackingNumber;

    @OneToMany(mappedBy = "shopOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id asc")
    private List<OrderItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "shopOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("changedAt asc, id asc")
    private List<OrderStatusHistory> history = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public void addItem(OrderItem item) {
        item.setShopOrder(this);
        items.add(item);
    }

    /** Changes the status and appends an audit entry. Transition rules are enforced by the services. */
    public void moveTo(OrderStatus next, String note, User changedBy) {
        this.status = next;
        OrderStatusHistory entry = new OrderStatusHistory();
        entry.setShopOrder(this);
        entry.setStatus(next);
        entry.setNote(note);
        entry.setChangedBy(changedBy);
        entry.setChangedAt(Instant.now());
        history.add(entry);
    }

    public BigDecimal getNetAmount() {
        return subtotal.subtract(commissionAmount);
    }
}
