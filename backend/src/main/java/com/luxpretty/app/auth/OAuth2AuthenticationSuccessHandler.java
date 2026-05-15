package com.luxpretty.app.auth;

import com.luxpretty.app.users.app.UserRoleService;
import com.luxpretty.app.users.domain.Role;
import com.luxpretty.app.users.domain.User;
import com.luxpretty.app.users.repo.UserRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import jakarta.servlet.http.Cookie;

import java.io.IOException;
import java.util.List;
import java.util.Set;

@Component
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final TokenService tokenService;
    private final UserRepository userRepository;
    private final UserRoleService userRoleService;

    @Value("${app.oauth2.authorized-redirect-uri}")
    private String authorizedRedirectUri;

    public OAuth2AuthenticationSuccessHandler(TokenService tokenService,
                                              UserRepository userRepository,
                                              UserRoleService userRoleService) {
        this.tokenService = tokenService;
        this.userRepository = userRepository;
        this.userRoleService = userRoleService;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        String targetUrl = determineTargetUrl(request, response, authentication);

        if (response.isCommitted()) {
            logger.debug("Response has already been committed. Unable to redirect to " + targetUrl);
            return;
        }

        clearAuthenticationAttributes(request);
        // Clear role hint cookie
        Cookie cookie = new Cookie(OAuth2RoleHintFilter.ROLE_HINT_COOKIE, "");
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }

    protected String determineTargetUrl(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) {
        Object principal = authentication.getPrincipal();
        if (!(principal instanceof OAuth2UserWithId withId)) {
            throw new OAuth2AuthenticationException(
                    "Unexpected OAuth2 principal type: " + principal.getClass());
        }

        // B2.2 — Load full user to get email + role for JWT claims (TenantFilter requires them)
        User user = userRepository.findById(withId.getUserId())
            .orElseThrow(() -> new OAuth2AuthenticationException("User not found"));

        Long activeTenantId = userRoleService.findUserTenantIds(user.getId())
                .stream().findFirst().orElse(null);
        Set<Role> resolved = userRoleService.resolveRoles(user.getId(), activeTenantId);
        List<String> roleNames = resolved.stream().map(Enum::name).toList();
        String token = tokenService.generateToken(user.getId(), user.getEmail(), roleNames, activeTenantId);

        return UriComponentsBuilder.fromUriString(authorizedRedirectUri)
            .queryParam("token", token)
            .build()
            .toUriString();
    }
}
