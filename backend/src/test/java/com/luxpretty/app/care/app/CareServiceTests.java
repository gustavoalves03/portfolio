package com.luxpretty.app.care.app;

import com.luxpretty.app.bookings.repo.CareBookingRepository;
import com.luxpretty.app.care.domain.Care;
import com.luxpretty.app.care.domain.CareStatus;
import com.luxpretty.app.care.repo.CareRepository;
import com.luxpretty.app.care.web.dto.CareRequest;
import com.luxpretty.app.category.domain.Category;
import com.luxpretty.app.category.repo.CategoryRepository;
import com.luxpretty.app.common.storage.FileStorageService;
import com.luxpretty.app.employee.domain.Employee;
import com.luxpretty.app.employee.repo.EmployeeRepository;
import com.luxpretty.app.multitenancy.TenantContext;
import com.luxpretty.app.tenant.domain.Tenant;
import com.luxpretty.app.tenant.repo.TenantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CareServiceTests {

    @Mock CareRepository repo;
    @Mock CategoryRepository categoryRepository;
    @Mock FileStorageService fileStorageService;
    @Mock CareBookingRepository careBookingRepository;
    @Mock EmployeeRepository employeeRepository;
    @Mock TenantRepository tenantRepository;

    @InjectMocks CareService service;

    @BeforeEach
    void setupTenant() {
        TenantContext.setCurrentTenant("salon-sophie");
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    private CareRequest sampleRequest() {
        return new CareRequest(
                "Soin visage",
                5000,
                "Description",
                60,
                CareStatus.ACTIVE,
                1L,
                null  // images
        );
    }

    private Tenant tenantWithOwner(long ownerId) {
        Tenant t = new Tenant();
        t.setSlug("salon-sophie");
        t.setOwnerId(ownerId);
        return t;
    }

    private Employee employee(long id, long userId) {
        Employee e = new Employee();
        e.setId(id);
        e.setUserId(userId);
        e.setName("Pro Self");
        e.setActive(true);
        return e;
    }

    @Test
    void create_attachesProSelfEmployee_whenCareHasNoEmployees() {
        Category cat = new Category();
        cat.setId(1L);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(cat));

        Care saved = new Care();
        saved.setId(42L);
        when(repo.save(any(Care.class))).thenReturn(saved);

        when(tenantRepository.findBySlug("salon-sophie"))
                .thenReturn(Optional.of(tenantWithOwner(7L)));
        Employee proSelf = employee(99L, 7L);
        when(employeeRepository.findByUserId(7L)).thenReturn(Optional.of(proSelf));

        service.create(sampleRequest());

        // Pro-self employee gets the new care added to its assignedCares set,
        // and is then saved (the Employee owns the M:N relation).
        assertThat(proSelf.getAssignedCares()).contains(saved);
        verify(employeeRepository).save(proSelf);
    }

    @Test
    void create_skipsFallback_whenNoProSelfEmployeeExists() {
        // Legacy tenant edge case: tenant exists, but no employee with userId == ownerId.
        // The fallback should silently skip — care is saved without a link.
        Category cat = new Category();
        cat.setId(1L);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(cat));

        Care saved = new Care();
        saved.setId(42L);
        when(repo.save(any(Care.class))).thenReturn(saved);

        when(tenantRepository.findBySlug("salon-sophie"))
                .thenReturn(Optional.of(tenantWithOwner(7L)));
        when(employeeRepository.findByUserId(7L)).thenReturn(Optional.empty());

        service.create(sampleRequest());

        // No Employee save attempted.
        verify(employeeRepository, never()).save(any(Employee.class));
    }
}
