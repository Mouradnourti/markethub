package com.pfe.marketplace.catalog.repository;

import com.pfe.marketplace.catalog.entity.Product;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    @EntityGraph(attributePaths = {"shop", "category"})
    Optional<Product> findWithShopAndCategoryById(Long id);

    @EntityGraph(attributePaths = {"category"})
    Page<Product> findByShopId(Long shopId, Pageable pageable);

    Optional<Product> findByIdAndShopId(Long id, Long shopId);

    @EntityGraph(attributePaths = {"shop", "category"})
    List<Product> findByIdIn(Collection<Long> ids);

    long countByShopId(Long shopId);

    long countByShopIdAndStockLessThan(Long shopId, int threshold);

    /**
     * Atomically reserves stock. Returns 0 when not enough stock is left, so concurrent checkouts can never
     * oversell. The version is bumped so stale seller edits fail with an optimistic-lock error.
     */
    @Modifying(flushAutomatically = true)
    @Query("""
            update Product p set p.stock = p.stock - :qty, p.soldCount = p.soldCount + :qty, p.version = p.version + 1
            where p.id = :id and p.stock >= :qty
            """)
    int reserveStock(@Param("id") Long id, @Param("qty") int qty);

    @Modifying(flushAutomatically = true)
    @Query("""
            update Product p set p.stock = p.stock + :qty, p.soldCount = greatest(p.soldCount - :qty, 0),
                p.version = p.version + 1
            where p.id = :id
            """)
    int releaseStock(@Param("id") Long id, @Param("qty") int qty);

    @Modifying(flushAutomatically = true)
    @Query("""
            update Product p set p.avgRating = :avg, p.reviewCount = :count, p.version = p.version + 1
            where p.id = :id
            """)
    void updateRating(@Param("id") Long id, @Param("avg") java.math.BigDecimal avg, @Param("count") int count);
}
