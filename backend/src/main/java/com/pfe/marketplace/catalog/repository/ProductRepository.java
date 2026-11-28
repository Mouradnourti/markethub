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

    /** Other products found in at least two of the same checkouts as this one, most frequent first. */
    @Query(value = """
            select other.product_id
            from order_items mine
            join shop_orders mine_so on mine_so.id = mine.shop_order_id
            join shop_orders other_so on other_so.order_id = mine_so.order_id
            join order_items other on other.shop_order_id = other_so.id
            where mine.product_id = :productId and other.product_id <> :productId
            group by other.product_id
            having count(distinct mine_so.order_id) >= 2
            order by count(distinct mine_so.order_id) desc, other.product_id
            limit :limit
            """, nativeQuery = true)
    List<Long> findBoughtTogetherIds(@Param("productId") Long productId, @Param("limit") int limit);

    long countByShopIdAndStockLessThan(Long shopId, int threshold);

    /** Visible products whose name contains the query or is a close typo of it, best match first. */
    @Query(value = """
            select p.id from products p join shops s on s.id = p.shop_id
            where p.active and s.status = 'APPROVED'
              and (lower(p.name) like :contains escape '!' or word_similarity(:q, lower(p.name)) > :threshold)
            order by (lower(p.name) like :prefix escape '!') desc, word_similarity(:q, lower(p.name)) desc,
                     p.sold_count desc, p.id
            limit :limit
            """, nativeQuery = true)
    List<Long> suggestIds(@Param("q") String q, @Param("contains") String contains, @Param("prefix") String prefix,
                          @Param("threshold") double threshold, @Param("limit") int limit);

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
