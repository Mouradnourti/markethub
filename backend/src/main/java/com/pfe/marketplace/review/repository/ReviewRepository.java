package com.pfe.marketplace.review.repository;

import com.pfe.marketplace.review.entity.Review;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    @EntityGraph(attributePaths = "user")
    Page<Review> findByProductId(Long productId, Pageable pageable);

    @EntityGraph(attributePaths = "user")
    Page<Review> findByProductIdAndRating(Long productId, int rating, Pageable pageable);

    @EntityGraph(attributePaths = {"user", "product"})
    Optional<Review> findWithUserById(Long id);

    boolean existsByProductIdAndUserId(Long productId, Long userId);

    long countByProductId(Long productId);

    @Query("select coalesce(avg(r.rating), 0) from Review r where r.product.id = :productId")
    double averageRating(@Param("productId") Long productId);

    @Query("select r.rating, count(r) from Review r where r.product.id = :productId group by r.rating")
    List<Object[]> countByRating(@Param("productId") Long productId);

    @Query(value = "select count(*) from reviews where product_id = :productId and jsonb_array_length(photo_urls) > 0",
            nativeQuery = true)
    long countWithPhotos(@Param("productId") Long productId);

    @EntityGraph(attributePaths = {"user", "product"})
    @Query("""
            select r from Review r where r.product.shop.id = :shopId
              and (:unanswered = false or r.sellerReply is null)
            """)
    Page<Review> findForShop(@Param("shopId") Long shopId, @Param("unanswered") boolean unanswered, Pageable pageable);

    @Modifying
    @Query(value = "insert into review_votes (review_id, user_id) values (:reviewId, :userId) on conflict do nothing",
            nativeQuery = true)
    int addVote(@Param("reviewId") Long reviewId, @Param("userId") Long userId);

    @Modifying
    @Query(value = "delete from review_votes where review_id = :reviewId and user_id = :userId", nativeQuery = true)
    int removeVote(@Param("reviewId") Long reviewId, @Param("userId") Long userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            update reviews set helpful_count = (select count(*) from review_votes v where v.review_id = :reviewId)
            where id = :reviewId
            """, nativeQuery = true)
    void refreshHelpfulCount(@Param("reviewId") Long reviewId);

    @Query(value = "select review_id from review_votes where user_id = :userId and review_id in (:reviewIds)",
            nativeQuery = true)
    List<Long> findVotedReviewIds(@Param("userId") Long userId, @Param("reviewIds") Collection<Long> reviewIds);
}
