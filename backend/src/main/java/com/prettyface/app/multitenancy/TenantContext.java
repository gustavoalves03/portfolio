package com.prettyface.app.multitenancy;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class TenantContext {

    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {}

    public static String getCurrentTenant() {
        return CURRENT_TENANT.get();
    }

    public static void setCurrentTenant(String tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }

    /**
     * Defense-in-depth guard: verify an active tenant is set before performing
     * sensitive operations. If the Hibernate schema router fails (bug, missing
     * filter, background job without context), services call this to refuse to
     * operate rather than silently acting against an ambiguous schema.
     *
     * <p>Throws {@link ResponseStatusException} with status 500 because an
     * unset tenant context at service entry is a server-side bug, not a
     * client error.
     *
     * @return the current tenant slug (guaranteed non-null, non-blank)
     */
    public static String requireActive() {
        String tenant = CURRENT_TENANT.get();
        if (tenant == null || tenant.isBlank()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "No tenant context");
        }
        return tenant;
    }
}
