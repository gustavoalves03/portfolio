package com.fleurdecoquillage.app.tenant.repo;

import com.fleurdecoquillage.app.tenant.domain.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TenantRepository extends JpaRepository<Tenant, Long> {
    Optional<Tenant> findBySlug(String slug);
    Optional<Tenant> findByOwnerId(Long ownerId);
    boolean existsBySlug(String slug);
}
