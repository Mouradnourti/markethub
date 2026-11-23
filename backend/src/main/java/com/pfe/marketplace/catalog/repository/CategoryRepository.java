package com.pfe.marketplace.catalog.repository;

import com.pfe.marketplace.catalog.entity.Category;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    boolean existsBySlug(String slug);

    boolean existsByParentId(Long parentId);

    @Query("select c from Category c left join fetch c.parent order by c.name")
    List<Category> findAllWithParent();
}
