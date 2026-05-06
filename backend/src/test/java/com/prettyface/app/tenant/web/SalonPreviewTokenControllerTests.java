package com.prettyface.app.tenant.web;

import com.prettyface.app.auth.UserPrincipal;
import com.prettyface.app.tenant.app.SalonPreviewTokenService;
import com.prettyface.app.tenant.app.TenantService;
import com.prettyface.app.tenant.domain.SalonPreviewToken;
import com.prettyface.app.tenant.domain.Tenant;
import com.prettyface.app.tenant.web.dto.PreviewTokenResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SalonPreviewTokenControllerTests {

    @Mock private SalonPreviewTokenService tokenService;
    @Mock private TenantService tenantService;

    @InjectMocks private SalonPreviewTokenController controller;

    private static final long OWNER_ID = 42L;
    private static final long TENANT_ID = 7L;

    private UserPrincipal principal() {
        return new UserPrincipal(OWNER_ID, "u@example.com", "User", null);
    }

    private Tenant tenant() {
        return Tenant.builder().id(TENANT_ID).slug("demo").ownerId(OWNER_ID).build();
    }

    @Test
    void createReturnsTheTokenWithAShareUrl() {
        when(tenantService.findByOwnerId(OWNER_ID)).thenReturn(Optional.of(tenant()));
        SalonPreviewToken minted = SalonPreviewToken.builder()
                .id(1L).tenantId(TENANT_ID).token("abc")
                .createdAt(LocalDateTime.now()).build();
        when(tokenService.create(TENANT_ID)).thenReturn(minted);

        ResponseEntity<PreviewTokenResponse> response = controller.create(principal());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().token()).isEqualTo("abc");
        assertThat(response.getBody().shareUrl()).contains("/salon/demo");
        assertThat(response.getBody().shareUrl()).contains("preview=abc");
    }

    @Test
    void listReturnsTokensForTheCurrentTenantOnly() {
        when(tenantService.findByOwnerId(OWNER_ID)).thenReturn(Optional.of(tenant()));
        SalonPreviewToken t = SalonPreviewToken.builder()
                .id(1L).tenantId(TENANT_ID).token("a")
                .createdAt(LocalDateTime.now()).build();
        when(tokenService.listByTenant(TENANT_ID)).thenReturn(List.of(t));

        ResponseEntity<List<PreviewTokenResponse>> response = controller.list(principal());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).token()).isEqualTo("a");
    }

    @Test
    void deleteRevokesTheTokenAndReturnsNoContent() {
        when(tenantService.findByOwnerId(OWNER_ID)).thenReturn(Optional.of(tenant()));

        ResponseEntity<Void> response = controller.delete(99L, principal());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(tokenService).revoke(99L, TENANT_ID);
    }

    @Test
    void createReturns404WhenTheUserHasNoTenant() {
        when(tenantService.findByOwnerId(OWNER_ID)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> controller.create(principal()));
        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
