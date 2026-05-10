package com.luxpretty.app.tenant.app;

import com.luxpretty.app.availability.repo.OpeningHourRepository;
import com.luxpretty.app.care.domain.CareStatus;
import com.luxpretty.app.care.repo.CareRepository;
import com.luxpretty.app.category.repo.CategoryRepository;
import com.luxpretty.app.tenant.domain.Tenant;
import com.luxpretty.app.tenant.domain.TenantStatus;
import com.luxpretty.app.tenant.web.dto.TenantReadinessResponse;
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
        // Default: no legacy Category rows — new tenants see hasCategory=false unless categorySlugs is set.
        lenient().when(categoryRepository.count()).thenReturn(0L);
    }

    @Test
    void getReadiness_allConditionsMet_canPublish() {
        var tenant = Tenant.builder()
                .slug("mon-salon")
                .name("Mon Salon")
                .status(TenantStatus.DRAFT)
                .categorySlugs("facial")
                .logoPath("uploads/logo.png")
                .addressStreet("1 rue X")
                .addressPostalCode("75011")
                .addressCity("Paris")
                .addressCountry("FR")
                .phone("0102030405")
                .build();
        when(careRepository.countByStatus(CareStatus.ACTIVE)).thenReturn(3L);
        when(openingHourRepository.count()).thenReturn(5L);

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

        TenantReadinessResponse result = service.getReadiness(tenant);

        assertThat(result.hasActiveCare()).isFalse();
        assertThat(result.canPublish()).isFalse();
    }

    @Test
    void getReadiness_blankName_cannotPublish() {
        var tenant = Tenant.builder().slug("mon-salon").name("  ").status(TenantStatus.ACTIVE).build();
        when(careRepository.countByStatus(CareStatus.ACTIVE)).thenReturn(1L);
        when(openingHourRepository.count()).thenReturn(1L);

        TenantReadinessResponse result = service.getReadiness(tenant);

        assertThat(result.name()).isFalse();
        assertThat(result.canPublish()).isFalse();
    }

    @Test
    void getReadiness_emptyTenant_hasCategoryFalse() {
        // Brand-new tenant (no categorySlugs set): hasCategory must be false
        // so the frontend Quickstart block can decide to show its persona templates.
        var tenant = Tenant.builder().slug("mon-salon").name("Mon Salon").status(TenantStatus.DRAFT).build();
        when(careRepository.countByStatus(CareStatus.ACTIVE)).thenReturn(0L);
        when(openingHourRepository.count()).thenReturn(0L);

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

        var missing = service.getMissingConditions(tenant);

        assertThat(missing).containsExactly("hasContact", "hasLogo", "hasCategory", "hasActiveCare", "hasOpeningHours");
    }

    // --- New tests for hasContact, hasLogo, hasCategory (from categorySlugs), and updated canPublish ---

    @Test
    void hasCategory_readsCategorySlugsFieldNotGlobalCount() {
        Tenant t = baseTenant();
        t.setCategorySlugs("facial,hair");
        assertThat(service.getReadiness(t).hasCategory()).isTrue();
    }

    @Test
    void hasCategory_falseWhenCategorySlugsBlank() {
        Tenant t = baseTenant();
        t.setCategorySlugs("");
        assertThat(service.getReadiness(t).hasCategory()).isFalse();
    }

    @Test
    void hasContact_falseWhenAddressIncomplete() {
        Tenant t = baseTenant();
        t.setAddressStreet("1 rue X");
        t.setAddressCity("Paris");
        t.setPhone("0102030405");
        assertThat(service.getReadiness(t).hasContact()).isFalse();
    }

    @Test
    void hasContact_falseWhenAddressFullButNeitherPhoneNorEmail() {
        Tenant t = fullAddress();
        assertThat(service.getReadiness(t).hasContact()).isFalse();
    }

    @Test
    void hasContact_trueWithPhoneOnly() {
        Tenant t = fullAddress();
        t.setPhone("0102030405");
        assertThat(service.getReadiness(t).hasContact()).isTrue();
    }

    @Test
    void hasContact_trueWithEmailOnly() {
        Tenant t = fullAddress();
        t.setContactEmail("hi@x.fr");
        assertThat(service.getReadiness(t).hasContact()).isTrue();
    }

    @Test
    void hasLogo_trueWhenLogoPathSet() {
        Tenant t = baseTenant();
        t.setLogoPath("uploads/x.png");
        assertThat(service.getReadiness(t).hasLogo()).isTrue();
    }

    @Test
    void hasLogo_falseWhenLogoPathNullOrBlank() {
        Tenant t = baseTenant();
        assertThat(service.getReadiness(t).hasLogo()).isFalse();
        t.setLogoPath("");
        assertThat(service.getReadiness(t).hasLogo()).isFalse();
    }

    @Test
    void canPublish_trueOnlyWhenAllSixConditionsMet() {
        Tenant t = fullyReady();
        when(careRepository.countByStatus(CareStatus.ACTIVE)).thenReturn(1L);
        when(openingHourRepository.count()).thenReturn(7L);
        assertThat(service.getReadiness(t).canPublish()).isTrue();
    }

    @Test
    void canPublish_falseWhenLogoMissing() {
        Tenant t = fullyReady();
        t.setLogoPath(null);
        when(careRepository.countByStatus(CareStatus.ACTIVE)).thenReturn(1L);
        when(openingHourRepository.count()).thenReturn(7L);
        assertThat(service.getReadiness(t).canPublish()).isFalse();
    }

    @Test
    void getMissingConditions_returnsKeysInWizardOrder() {
        Tenant t = baseTenant();
        when(careRepository.countByStatus(CareStatus.ACTIVE)).thenReturn(0L);
        when(openingHourRepository.count()).thenReturn(0L);
        assertThat(service.getMissingConditions(t))
                .containsExactly("name", "hasContact", "hasLogo", "hasCategory", "hasActiveCare", "hasOpeningHours");
    }

    // --- Fallback: legacy CategoryRepository.count() ---

    @Test
    void hasCategory_trueWhenCategorySlugsBlankButLegacyCategoryRowsExist() {
        Tenant t = baseTenant();
        // categorySlugs is null/blank but the tenant still has Category rows
        // from before syncTenantCategories existed. Fallback should kick in.
        when(categoryRepository.count()).thenReturn(2L);
        assertThat(service.getReadiness(t).hasCategory()).isTrue();
    }

    @Test
    void hasCategory_falseWhenBothCategorySlugsBlankAndNoCategoryRows() {
        Tenant t = baseTenant();
        // categorySlugs blank AND no Category rows — truly empty tenant.
        when(categoryRepository.count()).thenReturn(0L);
        assertThat(service.getReadiness(t).hasCategory()).isFalse();
    }

    // --- Helpers ---

    private Tenant baseTenant() {
        return Tenant.builder()
                .slug("salon")
                .status(TenantStatus.DRAFT)
                .annualLeaveDays(25)
                .build();
    }

    private Tenant fullAddress() {
        Tenant t = baseTenant();
        t.setAddressStreet("1 rue X");
        t.setAddressPostalCode("75011");
        t.setAddressCity("Paris");
        t.setAddressCountry("FR");
        return t;
    }

    private Tenant fullyReady() {
        Tenant t = fullAddress();
        t.setName("Belle de Nuit");
        t.setCategorySlugs("facial");
        t.setPhone("0102030405");
        t.setLogoPath("uploads/x.png");
        return t;
    }
}
