package com.luxpretty.app.auth;

import com.luxpretty.app.users.app.UserRoleService;
import com.luxpretty.app.users.domain.Role;
import com.luxpretty.app.users.domain.User;
import com.luxpretty.app.users.repo.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.io.IOException;
import java.util.List;

public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final TokenService tokenService;
    private final UserRepository userRepository;
    private final UserRoleService userRoleService;

    public JwtAuthenticationFilter(TokenService tokenService,
                                   UserRepository userRepository,
                                   UserRoleService userRoleService) {
        this.tokenService = tokenService;
        this.userRepository = userRepository;
        this.userRoleService = userRoleService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String jwt = getJwtFromRequest(request);

            if (StringUtils.hasText(jwt) && tokenService.validateToken(jwt)) {
                Long userId = tokenService.getUserIdFromToken(jwt);

                User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));

                UserPrincipal userPrincipal = UserPrincipal.create(user);
                // Task 4 shim: expose every role the user has anywhere so existing
                // hasRole(...) authorizations keep working. Task 6 will replace this
                // by reading roles[] + activeTenantId from the JWT claims.
                java.util.Set<Role> roles = new java.util.LinkedHashSet<>(userRoleService.resolveRoles(user.getId(), null));
                for (Long tid : userRoleService.findUserTenantIds(user.getId())) {
                    roles.addAll(userRoleService.resolveRoles(user.getId(), tid));
                }
                List<SimpleGrantedAuthority> authorities = roles.stream()
                        .map(r -> new SimpleGrantedAuthority("ROLE_" + r.name()))
                        .toList();
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    userPrincipal,
                    null,
                    authorities
                );
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (Exception ex) {
            logger.error("Could not set user authentication in security context", ex);
        }

        filterChain.doFilter(request, response);
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
