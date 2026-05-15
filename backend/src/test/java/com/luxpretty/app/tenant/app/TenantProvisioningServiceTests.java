package com.luxpretty.app.tenant.app;

import com.luxpretty.app.employee.app.EmployeeService;
import com.luxpretty.app.multitenancy.TenantSchemaManager;
import com.luxpretty.app.tenant.domain.Tenant;
import com.luxpretty.app.tenant.domain.TenantStatus;
import com.luxpretty.app.tenant.repo.TenantRepository;
import com.luxpretty.app.users.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantProvisioningServiceTests {

    @Mock private TenantRepository tenantRepository;
    @Mock private TenantSchemaManager schemaManager;
    @Mock private EmployeeService employeeService;
    @Mock private com.luxpretty.app.users.app.UserRoleService userRoleService;

    private TenantProvisioningService service;

    @BeforeEach
    void setUp() {
        service = new TenantProvisioningService(tenantRepository, schemaManager, employeeService, userRoleService);
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> {
            Tenant t = inv.getArgument(0);
            if (t.getId() == null) t.setId(42L);
            return t;
        });
    }

    private User user(long id, String name) {
        User u = new User();
        u.setId(id);
        u.setName(name);
        return u;
    }

    @Test
    void provision_setsStatusToDraft() {
        when(tenantRepository.existsBySlug(anyString())).thenReturn(false);

        Tenant result = service.provision(user(1L, "Sophie Martin"));

        assertThat(result.getStatus()).isEqualTo(TenantStatus.DRAFT);
    }

    @Test
    void provision_persistsTenantWithDraftStatus() {
        when(tenantRepository.existsBySlug(anyString())).thenReturn(false);

        service.provision(user(1L, "Sophie Martin"));

        ArgumentCaptor<Tenant> captor = ArgumentCaptor.forClass(Tenant.class);
        org.mockito.Mockito.verify(tenantRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(TenantStatus.DRAFT);
    }

    @Test
    void provision_buildsSlugFromOwnerName() {
        when(tenantRepository.existsBySlug(anyString())).thenReturn(false);

        Tenant result = service.provision(user(1L, "Sophie Martin"));

        assertThat(result.getSlug()).isEqualTo("sophie-martin");
        assertThat(result.getOwnerId()).isEqualTo(1L);
    }

    @Test
    void provision_appendsCounterWhenSlugTaken() {
        when(tenantRepository.existsBySlug("sophie-martin")).thenReturn(true);
        when(tenantRepository.existsBySlug("sophie-martin-2")).thenReturn(false);

        Tenant result = service.provision(user(1L, "Sophie Martin"));

        assertThat(result.getSlug()).isEqualTo("sophie-martin-2");
    }

    @Test
    void provision_leavesNameNullSoProMustConfirmInWizard() {
        when(tenantRepository.existsBySlug(anyString())).thenReturn(false);

        Tenant result = service.provision(user(42L, "Sophie Martin"));

        assertThat(result.getName()).isNull();
        ArgumentCaptor<Tenant> captor = ArgumentCaptor.forClass(Tenant.class);
        org.mockito.Mockito.verify(tenantRepository).save(captor.capture());
        assertThat(captor.getValue().getName()).isNull();
    }

    @Test
    void provision_createsSelfEmployee_forNewTenant() {
        when(tenantRepository.existsBySlug(anyString())).thenReturn(false);

        User owner = user(7L, "Alice");
        Tenant result = service.provision(owner);

        assertThat(result.getOwnerId()).isEqualTo(7L);
        ArgumentCaptor<User> ownerCaptor = ArgumentCaptor.forClass(User.class);
        verify(employeeService).createSelfEmployee(ownerCaptor.capture());
        assertThat(ownerCaptor.getValue().getId()).isEqualTo(7L);
    }

    @Test
    void provision_assignsProAndEmployeeRoles() {
        User owner = user(7L, "Alice");

        service.provision(owner);

        verify(userRoleService).assignOnTenant(7L,
                com.luxpretty.app.users.domain.Role.PRO, 42L);
        verify(userRoleService).assignOnTenant(7L,
                com.luxpretty.app.users.domain.Role.EMPLOYEE, 42L);
    }
}
