package com.luxpretty.app.auth;

import com.luxpretty.app.multitenancy.TenantContext;
import com.luxpretty.app.tenant.domain.Tenant;
import com.luxpretty.app.tenant.repo.TenantRepository;
import com.luxpretty.app.users.domain.User;
import com.luxpretty.app.users.repo.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for the Task 6 refactor: JwtAuthenticationFilter parses roles[] and
 * activeTenantId from the JWT directly, sets multiple authorities, and primes
 * TenantContext for the request lifecycle.
 */
class JwtAuthenticationFilterTests {

    private TokenService tokenService;
    private UserRepository userRepository;
    private TenantRepository tenantRepository;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        tokenService = mock(TokenService.class);
        userRepository = mock(UserRepository.class);
        tenantRepository = mock(TenantRepository.class);
        filter = new JwtAuthenticationFilter(tokenService, userRepository, tenantRepository);
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    private HttpServletRequest mockRequestWithBearer(String jwt) {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader("Authorization")).thenReturn("Bearer " + jwt);
        return req;
    }

    @Test
    void filter_setsMultipleAuthorities_fromRolesClaim() throws Exception {
        HttpServletRequest req = mockRequestWithBearer("x.y.z");
        HttpServletResponse res = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        when(tokenService.validateToken("x.y.z")).thenReturn(true);
        when(tokenService.getUserIdFromToken("x.y.z")).thenReturn(1L);
        when(tokenService.getActiveTenantIdFromToken("x.y.z")).thenReturn(null);
        when(tokenService.getRolesFromToken("x.y.z")).thenReturn(List.of("PRO", "COMMERCIAL"));
        when(userRepository.findById(1L)).thenReturn(Optional.of(User.builder().id(1L).email("a@a.com").build()));

        filter.doFilter(req, res, chain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_PRO", "ROLE_COMMERCIAL");
    }

    @Test
    void filter_setsTenantContext_whenActiveTenantIdPresent() throws Exception {
        HttpServletRequest req = mockRequestWithBearer("x.y.z");
        HttpServletResponse res = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        when(tokenService.validateToken("x.y.z")).thenReturn(true);
        when(tokenService.getUserIdFromToken("x.y.z")).thenReturn(1L);
        when(tokenService.getActiveTenantIdFromToken("x.y.z")).thenReturn(42L);
        when(tokenService.getRolesFromToken("x.y.z")).thenReturn(List.of("PRO"));
        when(userRepository.findById(1L)).thenReturn(Optional.of(User.builder().id(1L).email("a@a.com").build()));
        Tenant t = new Tenant();
        t.setId(42L);
        t.setSlug("salon-x");
        when(tenantRepository.findById(42L)).thenReturn(Optional.of(t));

        // Capture the TenantContext seen by the downstream chain.
        String[] seen = {"NOT_SET"};
        doAnswer(inv -> { seen[0] = TenantContext.getCurrentTenant(); return null; })
                .when(chain).doFilter(any(), any());

        filter.doFilter(req, res, chain);

        assertThat(seen[0]).isEqualTo("salon-x");
    }

    @Test
    void filter_clearsTenantContext_inFinally() throws Exception {
        HttpServletRequest req = mockRequestWithBearer("x.y.z");
        HttpServletResponse res = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        when(tokenService.validateToken("x.y.z")).thenReturn(true);
        when(tokenService.getUserIdFromToken("x.y.z")).thenReturn(1L);
        when(tokenService.getActiveTenantIdFromToken("x.y.z")).thenReturn(42L);
        when(tokenService.getRolesFromToken("x.y.z")).thenReturn(List.of("PRO"));
        when(userRepository.findById(1L)).thenReturn(Optional.of(User.builder().id(1L).email("a@a.com").build()));
        Tenant t = new Tenant();
        t.setId(42L);
        t.setSlug("salon-x");
        when(tenantRepository.findById(42L)).thenReturn(Optional.of(t));

        filter.doFilter(req, res, chain);

        assertThat(TenantContext.getCurrentTenant()).isNull();
    }

    @Test
    void filter_skipsTenantContext_whenActiveTenantIdNull() throws Exception {
        HttpServletRequest req = mockRequestWithBearer("x.y.z");
        HttpServletResponse res = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        when(tokenService.validateToken("x.y.z")).thenReturn(true);
        when(tokenService.getUserIdFromToken("x.y.z")).thenReturn(1L);
        when(tokenService.getActiveTenantIdFromToken("x.y.z")).thenReturn(null);
        when(tokenService.getRolesFromToken("x.y.z")).thenReturn(List.of());
        when(userRepository.findById(1L)).thenReturn(Optional.of(User.builder().id(1L).email("client@a.com").build()));

        String[] seen = {"NOT_SET"};
        doAnswer(inv -> { seen[0] = TenantContext.getCurrentTenant(); return null; })
                .when(chain).doFilter(any(), any());

        filter.doFilter(req, res, chain);

        assertThat(seen[0]).isNull();
        verify(tenantRepository, never()).findById(any());
    }
}
