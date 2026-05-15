package com.luxpretty.app.config;

import com.luxpretty.app.auth.UserPrincipal;
import com.luxpretty.app.subscription.domain.SubscriptionStatus;
import com.luxpretty.app.tenant.repo.TenantRepository;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@Component
@Order(5)
public class SubscriptionGuard implements Filter {

    private static final List<String> EXEMPT_PREFIXES = List.of(
        "/api/auth/",
        "/api/webhooks/",
        "/api/pro/subscription/",  // user must be able to view/start subscription
        "/api/pro/tenant"          // GET tenant info OK without active sub
    );

    private final TenantRepository tenantRepository;

    public SubscriptionGuard(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest http = (HttpServletRequest) req;
        String path = http.getRequestURI();

        // Only gate /api/pro/* endpoints.
        if (!path.startsWith("/api/pro/") || EXEMPT_PREFIXES.stream().anyMatch(path::startsWith)) {
            chain.doFilter(req, res);
            return;
        }

        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal principal)) {
            chain.doFilter(req, res); // let SecurityConfig reject downstream
            return;
        }

        var tenantOpt = tenantRepository.findByOwnerId(principal.getId());
        if (tenantOpt.isEmpty()) {
            chain.doFilter(req, res); // no tenant context, downstream decides
            return;
        }

        SubscriptionStatus status = tenantOpt.get().getSubscriptionStatus();
        if (status == null || !status.grantsAccess()) {
            HttpServletResponse httpRes = (HttpServletResponse) res;
            httpRes.setStatus(402); // Payment Required
            httpRes.setContentType("application/json");
            httpRes.getWriter().write(
                "{\"error\":\"SUBSCRIPTION_REQUIRED\",\"redirect\":\"/pro/onboarding/payment\"}");
            return;
        }

        chain.doFilter(req, res);
    }
}
