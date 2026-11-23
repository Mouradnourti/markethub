package com.pfe.marketplace.cart.repository;

import com.pfe.marketplace.cart.entity.CartItem;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    @Query("""
            select ci from CartItem ci
            join fetch ci.product p join fetch p.shop join fetch p.category
            where ci.user.id = :userId order by ci.addedAt asc, ci.id asc
            """)
    List<CartItem> findByUserIdWithProduct(@Param("userId") Long userId);

    Optional<CartItem> findByUserIdAndProductId(Long userId, Long productId);

    @Modifying
    @Query("delete from CartItem ci where ci.user.id = :userId")
    void deleteAllForUser(@Param("userId") Long userId);

    @Modifying
    @Query("delete from CartItem ci where ci.user.id = :userId and ci.product.id = :productId")
    int deleteItem(@Param("userId") Long userId, @Param("productId") Long productId);
}
