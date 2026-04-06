package com.prettyface.app.tenant.repo;

import com.prettyface.app.tenant.domain.Tenant;
import com.prettyface.app.tenant.domain.TenantStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TenantRepository extends JpaRepository<Tenant, Long> {
    Optional<Tenant> findBySlug(String slug);
    Optional<Tenant> findByOwnerId(Long ownerId);
    boolean existsBySlug(String slug);

    List<Tenant> findByStatus(TenantStatus status);
    List<Tenant> findByStatusAndCategorySlugsContaining(TenantStatus status, String slug);
    List<Tenant> findByStatusAndNameContainingIgnoreCase(TenantStatus status, String name);

    @Query("SELECT t FROM Tenant t WHERE t.status = :status AND (" +
           "LOWER(t.name) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(t.description) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(t.categoryNames) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(t.addressCity) LIKE LOWER(CONCAT('%', :q, '%')))")
    List<Tenant> searchByKeyword(@Param("status") TenantStatus status, @Param("q") String q);
}
