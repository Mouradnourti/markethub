package com.pfe.marketplace.order.repository;

import com.pfe.marketplace.order.entity.Order;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Page<Order> findByCustomerId(Long customerId, Pageable pageable);

    @EntityGraph(attributePaths = "customer")
    Optional<Order> findWithCustomerById(Long id);

    Optional<Order> findByIdAndCustomerId(Long id, Long customerId);

    long countByCustomerId(Long customerId);

    @EntityGraph(attributePaths = "customer")
    @Query("""
            select o from Order o
            where o.paymentMethod = com.pfe.marketplace.order.entity.PaymentMethod.COD and o.returnRiskScore is null
              and exists (select 1 from ShopOrder so where so.order = o and so.status in (
                  com.pfe.marketplace.order.entity.OrderStatus.PENDING,
                  com.pfe.marketplace.order.entity.OrderStatus.CONFIRMED,
                  com.pfe.marketplace.order.entity.OrderStatus.SHIPPED))
            order by o.createdAt desc
            """)
    List<Order> findOpenCodOrdersWithoutRiskScore(Pageable pageable);
}
