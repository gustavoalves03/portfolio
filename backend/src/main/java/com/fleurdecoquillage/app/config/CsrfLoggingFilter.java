package com.fleurdecoquillage.app.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;

@Component
public class CsrfLoggingFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(CsrfLoggingFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String method = request.getMethod();
        String uri = request.getRequestURI();

        logger.info("====== CSRF Filter START ======");
        logger.info("Request: {} {}", method, uri);

        // Log cookies
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            logger.info("Cookies present:");
            Arrays.stream(cookies)
                .filter(c -> c.getName().equals("XSRF-TOKEN"))
                .forEach(c -> logger.info("  - Cookie XSRF-TOKEN: {}", c.getValue()));
        } else {
            logger.warn("No cookies in request");
        }

        // Log CSRF header
        String csrfHeader = request.getHeader("X-XSRF-TOKEN");
        if (csrfHeader != null) {
            logger.info("Header X-XSRF-TOKEN: {}", csrfHeader);
        } else {
            logger.warn("No X-XSRF-TOKEN header in request");
        }

        // Log CSRF token from request attribute (set by Spring Security)
        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (csrfToken != null) {
            logger.info("CsrfToken from request attribute:");
            logger.info("  - Token value: {}", csrfToken.getToken());
            logger.info("  - Header name: {}", csrfToken.getHeaderName());
            logger.info("  - Parameter name: {}", csrfToken.getParameterName());
        } else {
            logger.warn("No CsrfToken in request attributes");
        }

        // Check if tokens match
        if (csrfToken != null && csrfHeader != null) {
            boolean matches = csrfToken.getToken().equals(csrfHeader);
            logger.info("Token comparison: cookie={}, header={}, matches={}",
                csrfToken.getToken(), csrfHeader, matches);
        }

        logger.info("====== CSRF Filter BEFORE filterChain.doFilter ======");

        try {
            filterChain.doFilter(request, response);
            logger.info("====== CSRF Filter AFTER filterChain.doFilter (SUCCESS) ======");
        } catch (Exception e) {
            logger.error("====== CSRF Filter AFTER filterChain.doFilter (ERROR) ======");
            logger.error("Exception: {}", e.getMessage());
            throw e;
        }
    }
}
