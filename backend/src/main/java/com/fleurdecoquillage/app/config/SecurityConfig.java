package com.fleurdecoquillage.app.config;

import com.fleurdecoquillage.app.auth.CustomOAuth2UserService;
import com.fleurdecoquillage.app.auth.JwtAuthenticationFilter;
import com.fleurdecoquillage.app.auth.OAuth2AuthenticationFailureHandler;
import com.fleurdecoquillage.app.auth.OAuth2AuthenticationSuccessHandler;
import com.fleurdecoquillage.app.auth.TokenService;
import com.fleurdecoquillage.app.common.error.RestAccessDeniedHandler;
import com.fleurdecoquillage.app.common.error.RestAuthenticationEntryPoint;
import com.fleurdecoquillage.app.multitenancy.TenantFilter;
import com.fleurdecoquillage.app.users.repo.UserRepository;
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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
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

    @Value("${app.cors.allowed-origins:http://localhost:4300,http://localhost:4200}")
    private String allowedOriginsConfig;

    private final RestAccessDeniedHandler accessDeniedHandler;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final CsrfLoggingFilter csrfLoggingFilter;
    private final CustomOAuth2UserService customOAuth2UserService;
    private final OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;
    private final OAuth2AuthenticationFailureHandler oAuth2AuthenticationFailureHandler;
    private final TokenService tokenService;
    private final UserRepository userRepository;
    private final TenantFilter tenantFilter;

    public SecurityConfig(RestAccessDeniedHandler accessDeniedHandler,
                          RestAuthenticationEntryPoint authenticationEntryPoint,
                          CsrfLoggingFilter csrfLoggingFilter,
                          CustomOAuth2UserService customOAuth2UserService,
                          OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler,
                          OAuth2AuthenticationFailureHandler oAuth2AuthenticationFailureHandler,
                          TokenService tokenService,
                          UserRepository userRepository,
                          TenantFilter tenantFilter) {
        this.accessDeniedHandler = accessDeniedHandler;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.csrfLoggingFilter = csrfLoggingFilter;
        this.customOAuth2UserService = customOAuth2UserService;
        this.oAuth2AuthenticationSuccessHandler = oAuth2AuthenticationSuccessHandler;
        this.oAuth2AuthenticationFailureHandler = oAuth2AuthenticationFailureHandler;
        this.tokenService = tokenService;
        this.userRepository = userRepository;
        this.tenantFilter = tenantFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
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
                // Add JWT authentication filter, then tenant resolution filter
                .addFilterBefore(new JwtAuthenticationFilter(tokenService, userRepository), UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(tenantFilter, JwtAuthenticationFilter.class)
                .csrf(csrf -> csrf
                        .csrfTokenRepository(tokenRepository)
                        .csrfTokenRequestHandler(requestHandler)
                        .ignoringRequestMatchers("/oauth2/**", "/api/auth/**") // Disable CSRF for OAuth2 and auth endpoints
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
                        .requestMatchers("/actuator/health", "/ping").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/csrf").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/images/**").permitAll()
                        .requestMatchers("/oauth2/**", "/api/auth/**").permitAll() // OAuth2 and auth endpoints
                        .requestMatchers(HttpMethod.GET, "/api/care/**").permitAll() // Public cares browsing
                        .requestMatchers(HttpMethod.GET, "/api/categories/**").permitAll() // Public categories
                        .requestMatchers(HttpMethod.GET, "/api/salon/**").permitAll() // Public salon storefront
                        .requestMatchers(HttpMethod.GET, "/api/public/**").permitAll() // Public discovery
                        // Admin-only endpoints (create, update, delete)
                        .requestMatchers(HttpMethod.POST, "/api/care/**", "/api/categories/**", "/api/users/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/care/**", "/api/categories/**", "/api/users/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/care/**", "/api/categories/**", "/api/users/**").hasRole("ADMIN")
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
                        )
                        .successHandler(oAuth2AuthenticationSuccessHandler)
                        .failureHandler(oAuth2AuthenticationFailureHandler)
                )
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable);

        return http.build();
    }
}

