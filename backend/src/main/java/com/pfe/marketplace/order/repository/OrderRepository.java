package com.pfe.marketplace.order.repository;

import com.pfe.marketplace.order.entity.Order;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Page<Order> findByCustomerId(Long customerId, Pageable pageable);

    @EntityGraph(attributePaths = "customer")
    Optional<Order> findWithCustomerById(Long id);

    Optional<Order> findByIdAndCustomerId(Long id, Long customerId);

    long countByCustomerId(Long customerId);
}
