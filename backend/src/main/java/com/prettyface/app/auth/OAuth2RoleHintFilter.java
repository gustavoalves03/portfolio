package com.prettyface.app.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Captures the role_hint query parameter from OAuth2 authorization requests
 * and stores it in a short-lived cookie for later retrieval by CustomOAuth2UserService.
 */
@Component
public class OAuth2RoleHintFilter extends OncePerRequestFilter {

    public static final String ROLE_HINT_COOKIE = "oauth2_role_hint";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (request.getRequestURI().startsWith("/oauth2/authorization/")) {
            String roleHint = request.getParameter("role_hint");
            if (roleHint != null) {
                Cookie cookie = new Cookie(ROLE_HINT_COOKIE, roleHint);
                cookie.setPath("/");
                cookie.setHttpOnly(true);
                cookie.setMaxAge(300); // 5 minutes
                response.addCookie(cookie);
            }
        }
        filterChain.doFilter(request, response);
    }
}
