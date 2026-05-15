package com.luxpretty.app.multitenancy;

import com.luxpretty.app.auth.TokenService;
import com.luxpretty.app.tenant.app.TenantService;
import com.luxpretty.app.users.domain.User;
import com.luxpretty.app.users.repo.UserRepository;
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
    private final UserRepository userRepository;

    public TenantFilter(TenantService tenantService, UserRepository userRepository) {
        this.tenantService = tenantService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        try {
            // Respect any TenantContext already set upstream:
            // - JwtAuthenticationFilter sets it from the JWT's activeTenantId claim
            //   (post scoped-RBAC migration — supports users with multiple tenants
            //   and the /api/me/switch-tenant flow).
            // - Public /api/salon/{slug}/** handlers set it inline from the URL.
            // Only fall back to the legacy ownership lookup when neither path ran.
            if (TenantContext.getCurrentTenant() == null) {
                Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                if (auth != null && auth.isAuthenticated()
                        && auth.getPrincipal() instanceof com.luxpretty.app.auth.UserPrincipal principal) {
                    boolean tenantSet = tenantService.findByOwnerId(principal.getId())
                            .map(tenant -> {
                                TenantContext.setCurrentTenant(tenant.getSlug());
                                logger.debug("TenantContext set to {} for user {}", tenant.getSlug(), principal.getId());
                                return true;
                            })
                            .orElse(false);

                    if (!tenantSet) {
                        User user = userRepository.findById(principal.getId()).orElse(null);
                        if (user != null && user.getTenantSlug() != null) {
                            TenantContext.setCurrentTenant(user.getTenantSlug());
                            logger.debug("TenantContext set to {} for employee user {}",
                                    user.getTenantSlug(), principal.getId());
                        }
                    }
                }
            }
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
