package com.pfe.marketplace.wishlist.entity;

import com.pfe.marketplace.catalog.entity.Product;
import com.pfe.marketplace.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "wishlist_items")
@Getter
@Setter
@NoArgsConstructor
public class WishlistItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "price_when_added", nullable = false, precision = 12, scale = 2)
    private BigDecimal priceWhenAdded;

    @Column(name = "last_alert_price", precision = 12, scale = 2)
    private BigDecimal lastAlertPrice;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** The price a drop is measured against: the lowest price the customer was already told about. */
    public BigDecimal referencePrice() {
        return lastAlertPrice == null ? priceWhenAdded : lastAlertPrice.min(priceWhenAdded);
    }
}
