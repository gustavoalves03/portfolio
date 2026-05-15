package com.luxpretty.app.auth;

import com.luxpretty.app.multitenancy.TenantContext;
import com.luxpretty.app.tenant.repo.TenantRepository;
import com.luxpretty.app.users.domain.User;
import com.luxpretty.app.users.repo.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final TokenService tokenService;
    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;

    public JwtAuthenticationFilter(TokenService tokenService,
                                   UserRepository userRepository,
                                   TenantRepository tenantRepository) {
        this.tokenService = tokenService;
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        boolean tenantContextSet = false;
        try {
            String jwt = getJwtFromRequest(request);

            if (StringUtils.hasText(jwt) && tokenService.validateToken(jwt)) {
                Long userId = tokenService.getUserIdFromToken(jwt);

                User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));

                UserPrincipal userPrincipal = UserPrincipal.create(user);
                List<String> roleNames = tokenService.getRolesFromToken(jwt);
                List<GrantedAuthority> authorities = roleNames.stream()
                        .map(n -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + n))
                        .toList();
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    userPrincipal,
                    null,
                    authorities
                );
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);

                // Public /api/salon/{slug}/** endpoints rely on TenantFilter setting the
                // slug from the URL — don't override that. Otherwise, JWT-driven path:
                // resolve the tenant id to its slug and prime TenantContext for
                // /api/pro/** + /api/me/** + any tenant-schema services.
                if (TenantContext.getCurrentTenant() == null) {
                    Long activeTenantId = tokenService.getActiveTenantIdFromToken(jwt);
                    if (activeTenantId != null) {
                        var tenant = tenantRepository.findById(activeTenantId).orElse(null);
                        if (tenant != null && tenant.getSlug() != null) {
                            TenantContext.setCurrentTenant(tenant.getSlug());
                            tenantContextSet = true;
                        }
                    }
                }
            }

            filterChain.doFilter(request, response);
        } catch (Exception ex) {
            logger.error("Could not set user authentication in security context", ex);
            filterChain.doFilter(request, response);
        } finally {
            if (tenantContextSet) {
                TenantContext.clear();
            }
        }
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
