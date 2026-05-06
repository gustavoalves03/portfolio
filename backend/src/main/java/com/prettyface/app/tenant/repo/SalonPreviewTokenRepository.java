package com.prettyface.app.tenant.repo;

import com.prettyface.app.tenant.domain.SalonPreviewToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SalonPreviewTokenRepository extends JpaRepository<SalonPreviewToken, Long> {
    Optional<SalonPreviewToken> findByToken(String token);
    List<SalonPreviewToken> findByTenantIdOrderByCreatedAtDesc(Long tenantId);
}
