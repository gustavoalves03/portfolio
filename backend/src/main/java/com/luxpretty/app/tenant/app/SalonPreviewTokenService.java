package com.luxpretty.app.tenant.app;

import com.luxpretty.app.tenant.domain.SalonPreviewToken;
import com.luxpretty.app.tenant.repo.SalonPreviewTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Mints, lists, revokes, and validates URL-safe storefront preview tokens.
 *
 * Security model: possession of the token === access (classic share-link
 * pattern). Tokens are stored in plaintext because the URL itself is the
 * credential; revocation uses the {@code revoked_at} timestamp.
 */
@Service
public class SalonPreviewTokenService {

    private final SalonPreviewTokenRepository repository;

    public SalonPreviewTokenService(SalonPreviewTokenRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public SalonPreviewToken create(Long tenantId) {
        SalonPreviewToken token = SalonPreviewToken.builder()
                .tenantId(tenantId)
                .token(UUID.randomUUID().toString())
                .createdAt(LocalDateTime.now())
                .build();
        return repository.save(token);
    }

    @Transactional(readOnly = true)
    public List<SalonPreviewToken> listByTenant(Long tenantId) {
        return repository.findByTenantIdOrderByCreatedAtDesc(tenantId);
    }

    @Transactional
    public void revoke(Long tokenId, Long tenantId) {
        SalonPreviewToken token = repository.findById(tokenId)
                .orElseThrow(() -> new IllegalArgumentException("Token not found"));
        if (!token.getTenantId().equals(tenantId)) {
            throw new IllegalArgumentException("Token belongs to another tenant");
        }
        token.setRevokedAt(LocalDateTime.now());
        repository.save(token);
    }

    @Transactional(readOnly = true)
    public boolean isValidForTenant(String token, Long tenantId) {
        Optional<SalonPreviewToken> maybe = repository.findByToken(token);
        if (maybe.isEmpty()) return false;
        SalonPreviewToken t = maybe.get();
        if (!t.getTenantId().equals(tenantId)) return false;
        if (t.getRevokedAt() != null) return false;
        if (t.getExpiresAt() != null && t.getExpiresAt().isBefore(LocalDateTime.now())) return false;
        return true;
    }
}
