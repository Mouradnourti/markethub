package com.pfe.marketplace.user.repository;

import com.pfe.marketplace.user.entity.Role;
import com.pfe.marketplace.user.entity.User;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByRole(Role role);

    long countByRole(Role role);

    @Query("""
            select u from User u
            where (:role is null or u.role = :role)
              and (:q is null or lower(u.email) like :q or lower(u.firstName) like :q or lower(u.lastName) like :q)
            """)
    Page<User> search(@Param("role") Role role, @Param("q") String q, Pageable pageable);
}
