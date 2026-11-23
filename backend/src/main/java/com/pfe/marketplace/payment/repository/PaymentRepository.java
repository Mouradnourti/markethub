package com.pfe.marketplace.payment.repository;

import com.pfe.marketplace.payment.entity.Payment;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findFirstByOrderIdOrderByIdDesc(Long orderId);

    @EntityGraph(attributePaths = "order")
    Optional<Payment> findByProviderReference(String providerReference);
}
