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

    private final CategoryRepository categoryRepository;
    private final CareRepository careRepository;
    private final OpeningHourRepository openingHourRepository;

    public TenantReadinessService(CategoryRepository categoryRepository,
                                   CareRepository careRepository,
                                   OpeningHourRepository openingHourRepository) {
        this.categoryRepository = categoryRepository;
        this.careRepository = careRepository;
        this.openingHourRepository = openingHourRepository;
    }

    public TenantReadinessResponse getReadiness(Tenant tenant) {
        boolean name = tenant.getName() != null && !tenant.getName().isBlank();
        boolean hasCategory = categoryRepository.count() > 0;
        boolean hasActiveCare = careRepository.countByStatus(CareStatus.ACTIVE) > 0;
        boolean hasOpeningHours = openingHourRepository.count() > 0;
        boolean canPublish = name && hasCategory && hasActiveCare && hasOpeningHours;

        return new TenantReadinessResponse(
            tenant.getSlug(),
            name, hasCategory, hasActiveCare, hasOpeningHours,
            canPublish, tenant.getStatus().name()
        );
    }

    public List<String> getMissingConditions(Tenant tenant) {
        TenantReadinessResponse r = getReadiness(tenant);
        List<String> missing = new ArrayList<>();
        if (!r.name()) missing.add("name");
        if (!r.hasCategory()) missing.add("hasCategory");
        if (!r.hasActiveCare()) missing.add("hasActiveCare");
        if (!r.hasOpeningHours()) missing.add("hasOpeningHours");
        return missing;
    }
}
