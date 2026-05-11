package com.luxpretty.app.tenant.app;

import com.luxpretty.app.employee.app.EmployeeService;
import com.luxpretty.app.multitenancy.TenantContext;
import com.luxpretty.app.multitenancy.TenantSchemaManager;
import com.luxpretty.app.tenant.domain.Tenant;
import com.luxpretty.app.tenant.domain.TenantStatus;
import com.luxpretty.app.tenant.repo.TenantRepository;
import com.luxpretty.app.users.domain.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantProvisioningService {

    private static final Logger logger = LoggerFactory.getLogger(TenantProvisioningService.class);

    private final TenantRepository tenantRepository;
    private final TenantSchemaManager tenantSchemaManager;
    private final EmployeeService employeeService;

    public TenantProvisioningService(
            TenantRepository tenantRepository,
            TenantSchemaManager tenantSchemaManager,
            EmployeeService employeeService) {
        this.tenantRepository = tenantRepository;
        this.tenantSchemaManager = tenantSchemaManager;
        this.employeeService = employeeService;
    }

    @Transactional
    public Tenant provision(User owner) {
        String baseSlug = SlugUtils.toSlug(owner.getName());
        String slug = ensureUniqueSlug(baseSlug);

        logger.info("Provisioning tenant for user {} with slug {}", owner.getId(), slug);

        tenantSchemaManager.provisionSchema(slug);

        // New tenants land in DRAFT: the pro completes the onboarding checklist
        // (name, first care, opening hours) before publishing to the public storefront.
        Tenant tenant = Tenant.builder()
                .slug(slug)
                .name(null)
                .ownerId(owner.getId())
                .status(TenantStatus.DRAFT)
                .build();

        Tenant saved = tenantRepository.save(tenant);

        // Seed the "pro-self" employee inside the new tenant's schema so the
        // owner can immediately appear as a bookable practitioner. The context
        // is cleared in a finally block to guarantee callers never see leak.
        TenantContext.setCurrentTenant(slug);
        try {
            employeeService.createSelfEmployee(owner);
        } finally {
            TenantContext.clear();
        }

        logger.info("Tenant {} provisioned successfully (id={})", slug, saved.getId());
        return saved;
    }

    private String ensureUniqueSlug(String baseSlug) {
        if (!tenantRepository.existsBySlug(baseSlug)) {
            return baseSlug;
        }
        int counter = 2;
        String candidate;
        do {
            candidate = baseSlug + "-" + counter;
            counter++;
        } while (tenantRepository.existsBySlug(candidate));
        return candidate;
    }
}
