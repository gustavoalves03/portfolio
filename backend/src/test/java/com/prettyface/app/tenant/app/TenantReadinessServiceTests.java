package com.prettyface.app.tenant.app;

import com.prettyface.app.availability.repo.OpeningHourRepository;
import com.prettyface.app.care.domain.CareStatus;
import com.prettyface.app.care.repo.CareRepository;
import com.prettyface.app.category.repo.CategoryRepository;
import com.prettyface.app.tenant.domain.Tenant;
import com.prettyface.app.tenant.domain.TenantStatus;
import com.prettyface.app.tenant.web.dto.TenantReadinessResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantReadinessServiceTests {

    @Mock private CareRepository careRepository;
    @Mock private OpeningHourRepository openingHourRepository;
    @Mock private CategoryRepository categoryRepository;

    private TenantReadinessService service;

    @BeforeEach
    void setUp() {
        service = new TenantReadinessService(careRepository, openingHourRepository, categoryRepository);
    }

    @Test
    void getReadiness_allConditionsMet_canPublish() {
        var tenant = Tenant.builder().slug("mon-salon").name("Mon Salon").status(TenantStatus.DRAFT).build();
        when(careRepository.countByStatus(CareStatus.ACTIVE)).thenReturn(3L);
        when(openingHourRepository.count()).thenReturn(5L);
        when(categoryRepository.count()).thenReturn(2L);

        TenantReadinessResponse result = service.getReadiness(tenant);

        assertThat(result.slug()).isEqualTo("mon-salon");
        assertThat(result.name()).isTrue();
        assertThat(result.hasCategory()).isTrue();
        assertThat(result.hasActiveCare()).isTrue();
        assertThat(result.hasOpeningHours()).isTrue();
        assertThat(result.canPublish()).isTrue();
        assertThat(result.status()).isEqualTo("DRAFT");
    }

    @Test
    void getReadiness_missingCare_cannotPublish() {
        var tenant = Tenant.builder().slug("mon-salon").name("Mon Salon").status(TenantStatus.DRAFT).build();
        when(careRepository.countByStatus(CareStatus.ACTIVE)).thenReturn(0L);
        when(openingHourRepository.count()).thenReturn(3L);
        lenient().when(categoryRepository.count()).thenReturn(1L);

        TenantReadinessResponse result = service.getReadiness(tenant);

        assertThat(result.hasActiveCare()).isFalse();
        assertThat(result.canPublish()).isFalse();
    }

    @Test
    void getReadiness_blankName_cannotPublish() {
        var tenant = Tenant.builder().slug("mon-salon").name("  ").status(TenantStatus.ACTIVE).build();
        when(careRepository.countByStatus(CareStatus.ACTIVE)).thenReturn(1L);
        when(openingHourRepository.count()).thenReturn(1L);
        lenient().when(categoryRepository.count()).thenReturn(1L);

        TenantReadinessResponse result = service.getReadiness(tenant);

        assertThat(result.name()).isFalse();
        assertThat(result.canPublish()).isFalse();
    }

    @Test
    void getReadiness_emptyTenant_hasCategoryFalse() {
        // Brand-new tenant (no schema-seeded categories): hasCategory must be false
        // so the frontend Quickstart block can decide to show its persona templates.
        var tenant = Tenant.builder().slug("mon-salon").name("Mon Salon").status(TenantStatus.DRAFT).build();
        when(careRepository.countByStatus(CareStatus.ACTIVE)).thenReturn(0L);
        when(openingHourRepository.count()).thenReturn(0L);
        when(categoryRepository.count()).thenReturn(0L);

        TenantReadinessResponse result = service.getReadiness(tenant);

        assertThat(result.hasCategory()).isFalse();
        assertThat(result.hasActiveCare()).isFalse();
        assertThat(result.canPublish()).isFalse();
    }

    @Test
    void getMissingConditions_returnsList() {
        var tenant = Tenant.builder().slug("mon-salon").name("Salon").status(TenantStatus.DRAFT).build();
        when(careRepository.countByStatus(CareStatus.ACTIVE)).thenReturn(0L);
        when(openingHourRepository.count()).thenReturn(0L);
        lenient().when(categoryRepository.count()).thenReturn(0L);

        var missing = service.getMissingConditions(tenant);

        assertThat(missing).containsExactly("hasActiveCare", "hasOpeningHours");
    }
}
