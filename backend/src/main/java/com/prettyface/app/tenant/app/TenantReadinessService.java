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
        boolean name = tenant.getName() != null && !tenant.getName().isBlank();
        boolean hasCategory = categoryRepository.count() > 0;
        boolean hasActiveCare = careRepository.countByStatus(CareStatus.ACTIVE) > 0;
        boolean hasOpeningHours = openingHourRepository.count() > 0;
        boolean canPublish = name && hasActiveCare && hasOpeningHours;

        int annualLeaveDays = tenant.getAnnualLeaveDays() != null ? tenant.getAnnualLeaveDays() : 25;

        return new TenantReadinessResponse(
            tenant.getSlug(),
            name, hasCategory, hasActiveCare, hasOpeningHours,
            canPublish, tenant.getStatus().name(),
            Boolean.TRUE.equals(tenant.getEmployeesEnabled()),
            annualLeaveDays
        );
    }

    public List<String> getMissingConditions(Tenant tenant) {
        TenantReadinessResponse r = getReadiness(tenant);
        List<String> missing = new ArrayList<>();
        if (!r.name()) missing.add("name");
        if (!r.hasActiveCare()) missing.add("hasActiveCare");
        if (!r.hasOpeningHours()) missing.add("hasOpeningHours");
        return missing;
    }
}
