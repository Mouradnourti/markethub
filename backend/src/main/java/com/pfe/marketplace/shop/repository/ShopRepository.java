package com.pfe.marketplace.shop.repository;

import com.pfe.marketplace.shop.entity.Shop;
import com.pfe.marketplace.shop.entity.ShopStatus;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShopRepository extends JpaRepository<Shop, Long> {

    Optional<Shop> findByOwnerId(Long ownerId);

    Optional<Shop> findBySlug(String slug);

    boolean existsBySlug(String slug);

    long countByStatus(ShopStatus status);

    @EntityGraph(attributePaths = "owner")
    Page<Shop> findByStatus(ShopStatus status, Pageable pageable);

    @EntityGraph(attributePaths = "owner")
    Page<Shop> findAllBy(Pageable pageable);
}
