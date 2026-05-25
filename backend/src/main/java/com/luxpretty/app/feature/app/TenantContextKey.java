package com.luxpretty.app.feature.app;

import com.luxpretty.app.multitenancy.TenantContext;
import org.springframework.stereotype.Component;

@Component("tenantContextKey")
public class TenantContextKey {
    public String current() {
        String slug = TenantContext.getCurrentTenant();
        return slug != null ? slug : "_none";
    }
}
