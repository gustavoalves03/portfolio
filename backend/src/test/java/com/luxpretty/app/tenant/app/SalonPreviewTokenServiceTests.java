package com.luxpretty.app.tenant.app;

import com.luxpretty.app.tenant.domain.SalonPreviewToken;
import com.luxpretty.app.tenant.repo.SalonPreviewTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SalonPreviewTokenServiceTests {

    @Mock private SalonPreviewTokenRepository repository;

    @InjectMocks private SalonPreviewTokenService service;

    private static final long TENANT_ID = 42L;

    @BeforeEach
    void setUp() {
        // Save returns the entity passed in (Mockito default behaviour after stubbing).
        org.mockito.Mockito.lenient()
                .when(repository.save(org.mockito.ArgumentMatchers.any(SalonPreviewToken.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void createGeneratesAUniqueTokenAndPersistsTheEntity() {
        SalonPreviewToken result = service.create(TENANT_ID);

        assertThat(result.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(result.getToken()).isNotBlank();
        assertThat(result.getCreatedAt()).isNotNull();
        assertThat(result.getRevokedAt()).isNull();
        verify(repository).save(org.mockito.ArgumentMatchers.any(SalonPreviewToken.class));
    }

    @Test
    void listReturnsTheTenantTokensOrderedByCreationDesc() {
        SalonPreviewToken a = SalonPreviewToken.builder().id(1L).tenantId(TENANT_ID).token("a").build();
        SalonPreviewToken b = SalonPreviewToken.builder().id(2L).tenantId(TENANT_ID).token("b").build();
        when(repository.findByTenantIdOrderByCreatedAtDesc(TENANT_ID)).thenReturn(List.of(a, b));

        List<SalonPreviewToken> tokens = service.listByTenant(TENANT_ID);

        assertThat(tokens).extracting(SalonPreviewToken::getToken).containsExactly("a", "b");
    }

    @Test
    void revokeMarksTheTokenAndPersistsIt() {
        SalonPreviewToken existing = SalonPreviewToken.builder()
                .id(99L).tenantId(TENANT_ID).token("t").createdAt(LocalDateTime.now()).build();
        when(repository.findById(99L)).thenReturn(Optional.of(existing));

        service.revoke(99L, TENANT_ID);

        ArgumentCaptor<SalonPreviewToken> captor = ArgumentCaptor.forClass(SalonPreviewToken.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getRevokedAt()).isNotNull();
    }

    @Test
    void revokeRefusesToRevokeATokenFromAnotherTenant() {
        SalonPreviewToken existing = SalonPreviewToken.builder()
                .id(99L).tenantId(TENANT_ID + 1).token("t").createdAt(LocalDateTime.now()).build();
        when(repository.findById(99L)).thenReturn(Optional.of(existing));

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> service.revoke(99L, TENANT_ID));

        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void validateReturnsTrueForActiveTokens() {
        SalonPreviewToken active = SalonPreviewToken.builder()
                .id(1L).tenantId(TENANT_ID).token("good")
                .createdAt(LocalDateTime.now())
                .build();
        when(repository.findByToken("good")).thenReturn(Optional.of(active));

        boolean ok = service.isValidForTenant("good", TENANT_ID);

        assertThat(ok).isTrue();
    }

    @Test
    void validateReturnsFalseForExpiredTokens() {
        SalonPreviewToken expired = SalonPreviewToken.builder()
                .id(1L).tenantId(TENANT_ID).token("old")
                .createdAt(LocalDateTime.now().minusDays(2))
                .expiresAt(LocalDateTime.now().minusDays(1))
                .build();
        when(repository.findByToken("old")).thenReturn(Optional.of(expired));

        boolean ok = service.isValidForTenant("old", TENANT_ID);

        assertThat(ok).isFalse();
    }

    @Test
    void validateReturnsFalseForRevokedTokens() {
        SalonPreviewToken revoked = SalonPreviewToken.builder()
                .id(1L).tenantId(TENANT_ID).token("dead")
                .createdAt(LocalDateTime.now())
                .revokedAt(LocalDateTime.now())
                .build();
        when(repository.findByToken("dead")).thenReturn(Optional.of(revoked));

        boolean ok = service.isValidForTenant("dead", TENANT_ID);

        assertThat(ok).isFalse();
    }

    @Test
    void validateReturnsFalseForTokenMismatchedToTenant() {
        SalonPreviewToken otherTenant = SalonPreviewToken.builder()
                .id(1L).tenantId(TENANT_ID + 1).token("t")
                .createdAt(LocalDateTime.now())
                .build();
        when(repository.findByToken("t")).thenReturn(Optional.of(otherTenant));

        boolean ok = service.isValidForTenant("t", TENANT_ID);

        assertThat(ok).isFalse();
    }

    @Test
    void validateReturnsFalseForUnknownTokens() {
        when(repository.findByToken("nope")).thenReturn(Optional.empty());

        boolean ok = service.isValidForTenant("nope", TENANT_ID);

        assertThat(ok).isFalse();
    }
}
