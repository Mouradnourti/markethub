package com.pfe.marketplace.promotion.repository;

import com.pfe.marketplace.promotion.entity.Coupon;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CouponRepository extends JpaRepository<Coupon, Long> {

    @EntityGraph(attributePaths = "shop")
    Optional<Coupon> findByCode(String code);

    List<Coupon> findByShopIdOrderByCreatedAtDesc(Long shopId);

    Optional<Coupon> findByIdAndShopId(Long id, Long shopId);

    boolean existsByCode(String code);

    List<Coupon> findByShopIdAndFeaturedTrueAndActiveTrue(Long shopId);

    /** Counts one redemption; returns 0 when the usage limit was reached concurrently. */
    @Modifying(flushAutomatically = true)
    @Query("""
            update Coupon c set c.usedCount = c.usedCount + 1
            where c.id = :id and (c.maxUses is null or c.usedCount < c.maxUses)
            """)
    int redeem(@Param("id") Long id);

    @Modifying(flushAutomatically = true)
    @Query("update Coupon c set c.usedCount = greatest(c.usedCount - 1, 0) where c.id = :id")
    void release(@Param("id") Long id);

    /** Whether the customer already used this code on a parcel that was not cancelled. */
    @Query("""
            select count(so) > 0 from ShopOrder so
            where so.couponId = :couponId and so.order.customer.id = :customerId
              and so.status <> com.pfe.marketplace.order.entity.OrderStatus.CANCELLED
            """)
    boolean usedByCustomer(@Param("couponId") Long couponId, @Param("customerId") Long customerId);
}
