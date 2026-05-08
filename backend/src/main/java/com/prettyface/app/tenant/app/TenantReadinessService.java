package com.prettyface.app.tenant.app;

import com.prettyface.app.availability.repo.OpeningHourRepository;
import com.prettyface.app.care.domain.CareStatus;
import com.prettyface.app.care.repo.CareRepository;
import com.prettyface.app.category.repo.CategoryRepository;
import com.prettyface.app.tenant.domain.Tenant;
import com.prettyface.app.tenant.web.dto.TenantReadinessResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class TenantReadinessService {

    private final CareRepository careRepository;
    private final OpeningHourRepository openingHourRepository;
    private final CategoryRepository categoryRepository;

    public TenantReadinessService(CareRepository careRepository,
                                   OpeningHourRepository openingHourRepository,
                                   CategoryRepository categoryRepository) {
        this.careRepository = careRepository;
        this.openingHourRepository = openingHourRepository;
        this.categoryRepository = categoryRepository;
    }

    public TenantReadinessResponse getReadiness(Tenant tenant) {
        boolean name = notBlank(tenant.getName());
        // Prefer the denormalized categorySlugs (kept in sync by CategoryService).
        // Fall back to a tenant-scoped Category row count for legacy tenants whose
        // categorySlugs was never backfilled — avoids forcing them through the wizard
        // on every login when they already have categories in their schema.
        boolean hasCategory = notBlank(tenant.getCategorySlugs())
                || categoryRepository.count() > 0;
        boolean hasContact = computeHasContact(tenant);
        boolean hasLogo = notBlank(tenant.getLogoPath());
        boolean hasActiveCare = careRepository.countByStatus(CareStatus.ACTIVE) > 0;
        boolean hasOpeningHours = openingHourRepository.count() > 0;
        boolean canPublish = name && hasCategory && hasContact && hasLogo && hasActiveCare && hasOpeningHours;

        int annualLeaveDays = tenant.getAnnualLeaveDays() != null ? tenant.getAnnualLeaveDays() : 25;

        return new TenantReadinessResponse(
            tenant.getSlug(),
            name, hasCategory, hasContact, hasLogo, hasActiveCare, hasOpeningHours,
            canPublish, tenant.getStatus().name(),
            Boolean.TRUE.equals(tenant.getEmployeesEnabled()),
            annualLeaveDays
        );
    }

    public List<String> getMissingConditions(Tenant tenant) {
        TenantReadinessResponse r = getReadiness(tenant);
        List<String> missing = new ArrayList<>();
        if (!r.name()) missing.add("name");
        if (!r.hasContact()) missing.add("hasContact");
        if (!r.hasLogo()) missing.add("hasLogo");
        if (!r.hasCategory()) missing.add("hasCategory");
        if (!r.hasActiveCare()) missing.add("hasActiveCare");
        if (!r.hasOpeningHours()) missing.add("hasOpeningHours");
        return missing;
    }

    private boolean computeHasContact(Tenant t) {
        boolean addressFull = notBlank(t.getAddressStreet())
                && notBlank(t.getAddressPostalCode())
                && notBlank(t.getAddressCity())
                && notBlank(t.getAddressCountry());
        boolean reachable = notBlank(t.getPhone()) || notBlank(t.getContactEmail());
        return addressFull && reachable;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
