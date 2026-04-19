package com.prettyface.app.tracking.app;

import com.prettyface.app.multitenancy.ApplicationSchemaExecutor;
import com.prettyface.app.tracking.domain.SalonClient;
import com.prettyface.app.tracking.repo.SalonClientRepository;
import com.prettyface.app.tracking.web.dto.SalonClientResponse;
import com.prettyface.app.users.repo.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SalonClientServiceTests {

    @Mock private SalonClientRepository salonClientRepo;
    @Mock private UserRepository userRepository;
    @Mock private ApplicationSchemaExecutor applicationSchemaExecutor;

    @InjectMocks
    private SalonClientService service;

    @BeforeEach
    void setUp() {
        lenient().when(applicationSchemaExecutor.call(any()))
                .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(0)).get());
    }

    @Test
    @DisplayName("getById — returns mapped SalonClient when it exists")
    void getById_returnsMappedClient() {
        SalonClient client = new SalonClient();
        client.setId(10L);
        client.setName("Claire");
        client.setPhone("+33600000000");
        when(salonClientRepo.findById(10L)).thenReturn(Optional.of(client));

        SalonClientResponse result = service.getById(10L);

        assertThat(result.id()).isEqualTo(10L);
        assertThat(result.name()).isEqualTo("Claire");
    }

    @Test
    @DisplayName("getById — throws 404 when the SalonClient does not exist")
    void getById_notFound_throws() {
        when(salonClientRepo.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(999L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    // ══════════════════════════════════════════════════════════════
    // ── Lot2 Sec1: Cross-tenant IDOR on SalonClient read ──
    // ══════════════════════════════════════════════════════════════
    // NOTE-SEC: SalonClient has no tenantSlug/tenantId column. It lives in the
    // per-tenant schema routed by TenantContext. SalonClientService.getById
    // performs NO tenant check — it simply calls repo.findById(id). If the
    // schema router is bypassed, a pro/employee from salon A could read the
    // name/phone/email/notes of any SalonClient whose id they can guess.

    @Test
    @DisplayName("Lot2#45: getById_WARN_doesNotCheckTenantOwnership — NO tenant check (FINDING)")
    void getById_WARN_doesNotCheckTenantOwnership() {
        // TODO-SEC: SalonClientService.getById performs no tenant ownership
        // verification. The SalonClient's PII (phone, email, notes, DOB) is
        // returned to any authenticated caller whose schema context resolves
        // that id. Documents the gap.
        SalonClient crossTenantClient = new SalonClient();
        crossTenantClient.setId(1234L);
        crossTenantClient.setName("Victim Client");
        crossTenantClient.setPhone("+33700000000");
        crossTenantClient.setEmail("victim@example.com");
        crossTenantClient.setNotes("sensitive medical notes");
        when(salonClientRepo.findById(1234L)).thenReturn(Optional.of(crossTenantClient));

        // Service returns the row without asking "is this client in the caller's tenant?"
        SalonClientResponse result = service.getById(1234L);

        assertThat(result.name()).isEqualTo("Victim Client");
        assertThat(result.phone()).isEqualTo("+33700000000");
        assertThat(result.email()).isEqualTo("victim@example.com");
        assertThat(result.notes()).isEqualTo("sensitive medical notes");
        // No tenant/caller scope check — documents the gap.
    }
}
