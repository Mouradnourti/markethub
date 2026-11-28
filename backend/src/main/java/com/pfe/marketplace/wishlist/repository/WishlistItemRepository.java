package com.pfe.marketplace.wishlist.repository;

import com.pfe.marketplace.wishlist.entity.WishlistItem;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WishlistItemRepository extends JpaRepository<WishlistItem, Long> {

    @Query("""
            select w from WishlistItem w
            join fetch w.product p join fetch p.shop join fetch p.category
            where w.user.id = :userId order by w.createdAt desc, w.id desc
            """)
    List<WishlistItem> findByUserIdWithProduct(@Param("userId") Long userId);

    @Query("select w.product.id from WishlistItem w where w.user.id = :userId")
    List<Long> findProductIds(@Param("userId") Long userId);

    @Query("select w from WishlistItem w join fetch w.user where w.product.id = :productId")
    List<WishlistItem> findByProductIdWithUser(@Param("productId") Long productId);

    Optional<WishlistItem> findByUserIdAndProductId(Long userId, Long productId);

    long countByProductId(Long productId);

    @Modifying
    @Query("delete from WishlistItem w where w.user.id = :userId and w.product.id = :productId")
    int deleteItem(@Param("userId") Long userId, @Param("productId") Long productId);
}
