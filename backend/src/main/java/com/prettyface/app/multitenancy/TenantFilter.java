package com.prettyface.app.multitenancy;

import com.prettyface.app.auth.TokenService;
import com.prettyface.app.tenant.app.TenantService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Servlet filter that sets the TenantContext for every request based on the
 * authenticated user's tenant. Must run after JwtAuthenticationFilter.
 */
@Component
public class TenantFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(TenantFilter.class);

    private final TenantService tenantService;

    public TenantFilter(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof com.prettyface.app.auth.UserPrincipal principal) {
                tenantService.findByOwnerId(principal.getId()).ifPresent(tenant -> {
                    TenantContext.setCurrentTenant(tenant.getSlug());
                    logger.debug("TenantContext set to {} for user {}", tenant.getSlug(), principal.getId());
                });
            }
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
