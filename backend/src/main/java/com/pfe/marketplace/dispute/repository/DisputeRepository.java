package com.pfe.marketplace.dispute.repository;

import com.pfe.marketplace.dispute.entity.Dispute;
import com.pfe.marketplace.dispute.entity.DisputeStatus;
import java.util.Collection;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DisputeRepository extends JpaRepository<Dispute, Long> {

    @EntityGraph(attributePaths = {"customer", "shopOrder", "shopOrder.shop", "shopOrder.order"})
    @Query("select d from Dispute d where (:status is null or d.status = :status)")
    Page<Dispute> search(@Param("status") DisputeStatus status, Pageable pageable);

    boolean existsByShopOrderIdAndStatus(Long shopOrderId, DisputeStatus status);

    long countByStatus(DisputeStatus status);

    @Query("""
            select d.shopOrder.id from Dispute d
            where d.status = com.pfe.marketplace.dispute.entity.DisputeStatus.OPEN and d.shopOrder.id in :ids
            """)
    Set<Long> findShopOrdersWithOpenDispute(@Param("ids") Collection<Long> ids);
}
