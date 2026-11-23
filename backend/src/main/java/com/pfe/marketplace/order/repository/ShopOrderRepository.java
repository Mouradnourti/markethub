package com.pfe.marketplace.order.repository;

import com.pfe.marketplace.order.entity.OrderStatus;
import com.pfe.marketplace.order.entity.ShopOrder;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShopOrderRepository extends JpaRepository<ShopOrder, Long> {

    @EntityGraph(attributePaths = {"order", "order.customer"})
    @Query("""
            select so from ShopOrder so
            where so.shop.id = :shopId and (:status is null or so.status = :status)
            """)
    Page<ShopOrder> findForShop(@Param("shopId") Long shopId, @Param("status") OrderStatus status, Pageable pageable);

    @EntityGraph(attributePaths = "order")
    List<ShopOrder> findByStatusAndCreatedAtBefore(OrderStatus status, Instant before);

    @EntityGraph(attributePaths = {"order", "order.customer", "shop"})
    Optional<ShopOrder> findByIdAndShopId(Long id, Long shopId);

    @EntityGraph(attributePaths = {"order", "order.customer", "shop"})
    @Query("select so from ShopOrder so where so.id = :id and so.order.customer.id = :customerId")
    Optional<ShopOrder> findForCustomer(@Param("id") Long id, @Param("customerId") Long customerId);

    @Query("""
            select count(so) > 0 from ShopOrder so join so.items i
            where so.order.customer.id = :customerId and i.product.id = :productId
              and so.status = com.pfe.marketplace.order.entity.OrderStatus.DELIVERED
            """)
    boolean customerReceivedProduct(@Param("customerId") Long customerId, @Param("productId") Long productId);
}
