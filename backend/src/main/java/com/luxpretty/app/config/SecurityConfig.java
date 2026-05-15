package com.luxpretty.app.config;

import com.luxpretty.app.auth.CustomOAuth2UserService;
import com.luxpretty.app.auth.CustomOidcUserService;
import com.luxpretty.app.auth.JwtAuthenticationFilter;
import com.luxpretty.app.auth.OAuth2AuthenticationFailureHandler;
import com.luxpretty.app.auth.OAuth2AuthenticationSuccessHandler;
import com.luxpretty.app.auth.OAuth2RoleHintFilter;
import com.luxpretty.app.auth.TokenService;
import com.luxpretty.app.common.error.RestAccessDeniedHandler;
import com.luxpretty.app.common.error.RestAuthenticationEntryPoint;
import com.luxpretty.app.multitenancy.TenantFilter;
import com.luxpretty.app.users.repo.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;

import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

@Configuration
public class SecurityConfig {

    @Value("${app.cors.allowed-origins}")
    private String allowedOriginsConfig;

    private final RestAccessDeniedHandler accessDeniedHandler;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final CsrfLoggingFilter csrfLoggingFilter;
    private final CustomOAuth2UserService customOAuth2UserService;
    private final CustomOidcUserService customOidcUserService;
    private final OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;
    private final OAuth2AuthenticationFailureHandler oAuth2AuthenticationFailureHandler;
    private final TokenService tokenService;
    private final UserRepository userRepository;
    private final TenantFilter tenantFilter;
    private final OAuth2RoleHintFilter oAuth2RoleHintFilter;
    private final com.luxpretty.app.users.app.UserRoleService userRoleService;
    private final com.luxpretty.app.tenant.repo.TenantRepository tenantRepository;
    private final SubscriptionGuard subscriptionGuard;

    public SecurityConfig(RestAccessDeniedHandler accessDeniedHandler,
                          RestAuthenticationEntryPoint authenticationEntryPoint,
                          CsrfLoggingFilter csrfLoggingFilter,
                          CustomOAuth2UserService customOAuth2UserService,
                          CustomOidcUserService customOidcUserService,
                          OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler,
                          OAuth2AuthenticationFailureHandler oAuth2AuthenticationFailureHandler,
                          TokenService tokenService,
                          UserRepository userRepository,
                          TenantFilter tenantFilter,
                          OAuth2RoleHintFilter oAuth2RoleHintFilter,
                          com.luxpretty.app.users.app.UserRoleService userRoleService,
                          com.luxpretty.app.tenant.repo.TenantRepository tenantRepository,
                          SubscriptionGuard subscriptionGuard) {
        this.accessDeniedHandler = accessDeniedHandler;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.csrfLoggingFilter = csrfLoggingFilter;
        this.customOAuth2UserService = customOAuth2UserService;
        this.customOidcUserService = customOidcUserService;
        this.oAuth2AuthenticationSuccessHandler = oAuth2AuthenticationSuccessHandler;
        this.oAuth2AuthenticationFailureHandler = oAuth2AuthenticationFailureHandler;
        this.tokenService = tokenService;
        this.userRepository = userRepository;
        this.tenantFilter = tenantFilter;
        this.oAuth2RoleHintFilter = oAuth2RoleHintFilter;
        this.userRoleService = userRoleService;
        this.tenantRepository = tenantRepository;
        this.subscriptionGuard = subscriptionGuard;
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // Configure CSRF with cookie-based tokens for SPA (Angular)
        CookieCsrfTokenRepository tokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        tokenRepository.setCookiePath("/");
        tokenRepository.setCookieName("XSRF-TOKEN");

        var delegate = new XorCsrfTokenRequestAttributeHandler();
        delegate.setCsrfRequestAttributeName("_csrf");

        var attributeHandler = new CsrfTokenRequestAttributeHandler();
        attributeHandler.setCsrfRequestAttributeName("_csrf");

        CsrfTokenRequestHandler requestHandler = new CsrfTokenRequestHandler() {
            @Override
            public void handle(HttpServletRequest request, HttpServletResponse response, Supplier<CsrfToken> csrfToken) {
                delegate.handle(request, response, csrfToken);
                attributeHandler.handle(request, response, csrfToken);
            }

            @Override
            public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
                String headerValue = request.getHeader(csrfToken.getHeaderName());
                if (headerValue != null) {
                    return headerValue;
                }
                return attributeHandler.resolveCsrfTokenValue(request, csrfToken);
            }
        };

        http
                // Add OAuth2 role hint filter, JWT authentication filter, then tenant resolution filter, then subscription guard
                .addFilterBefore(oAuth2RoleHintFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new JwtAuthenticationFilter(tokenService, userRepository, tenantRepository), UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(tenantFilter, JwtAuthenticationFilter.class)
                .addFilterAfter(subscriptionGuard, JwtAuthenticationFilter.class)
                .csrf(csrf -> csrf
                        .csrfTokenRepository(tokenRepository)
                        .csrfTokenRequestHandler(requestHandler)
                        // /api/test/** is only mapped when the `smoke-test` profile is active
                        // (see SmokeTestSeedController). Exemption is inert otherwise.
                        .ignoringRequestMatchers("/oauth2/**", "/api/auth/**", "/ws/**", "/api/test/**", "/api/webhooks/postmark", "/api/webhooks/stripe")
                )
                .cors(cors -> cors.configurationSource(request -> {
                    var c = new CorsConfiguration();
                    c.setAllowedOrigins(Arrays.asList(allowedOriginsConfig.split(",")));
                    c.setAllowedMethods(List.of("GET","POST","PUT","DELETE","PATCH","OPTIONS"));
                    c.setAllowedHeaders(List.of("*"));
                    c.setAllowCredentials(true);
                    c.setExposedHeaders(List.of("X-XSRF-TOKEN"));
                    return c;
                }))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .accessDeniedHandler(accessDeniedHandler)
                        .authenticationEntryPoint(authenticationEntryPoint)
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/ping", "/api/webhooks/postmark", "/api/webhooks/stripe").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/csrf").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/images/**").permitAll()
                        .requestMatchers("/api/auth/me").authenticated() // Must come before /api/auth/** permitAll
                        .requestMatchers("/oauth2/**", "/api/auth/**").permitAll() // OAuth2 and auth endpoints
                        .requestMatchers("/ws/**").permitAll() // WebSocket handshake (auth handled by WebSocketAuthInterceptor)
                        // Smoke-test-only seed endpoints (controller is @Profile("smoke-test"))
                        .requestMatchers("/api/test/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/care/**").permitAll() // Public cares browsing
                        .requestMatchers(HttpMethod.GET, "/api/categories/**").permitAll() // Public categories
                        .requestMatchers(HttpMethod.POST, "/api/salon/*/book").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/salon/**").permitAll() // Public salon storefront
                        .requestMatchers(HttpMethod.GET, "/api/public/**").permitAll() // Public discovery
                        // Care & category management (PRO manages their own via tenant schema)
                        .requestMatchers(HttpMethod.POST, "/api/care/**", "/api/categories/**").hasAnyRole("PRO", "ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/care/**", "/api/categories/**").hasAnyRole("PRO", "ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/care/**", "/api/categories/**").hasAnyRole("PRO", "ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/care/**").hasAnyRole("PRO", "ADMIN")
                        // Admin-only user management
                        .requestMatchers(HttpMethod.POST, "/api/users/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/users/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/users/**").hasRole("ADMIN")
                        .requestMatchers("/api/client/**").authenticated()
                        // Employee endpoints (staff access)
                        .requestMatchers("/api/employee/**").hasAnyRole("EMPLOYEE", "PRO", "ADMIN")
                        // Pro-only endpoints (salon management)
                        .requestMatchers("/api/pro/**").hasAnyRole("PRO", "ADMIN")
                        // User endpoints (bookings, profile)
                        .requestMatchers("/api/bookings/**").authenticated()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll() // Allow all other requests (frontend static files, etc.)
                )
                .oauth2Login(oauth2 -> oauth2
                        .userInfoEndpoint(userInfo -> userInfo
                                .userService(customOAuth2UserService)
                                .oidcUserService(customOidcUserService)
                        )
                        .successHandler(oAuth2AuthenticationSuccessHandler)
                        .failureHandler(oAuth2AuthenticationFailureHandler)
                )
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable);

        return http.build();
    }
}

