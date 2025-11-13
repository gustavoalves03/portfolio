package com.fleurdecoquillage.app.config;

import com.fleurdecoquillage.app.common.error.RestAccessDeniedHandler;
import com.fleurdecoquillage.app.common.error.RestAuthenticationEntryPoint;
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
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;

import java.util.List;
import java.util.function.Supplier;

@Configuration
public class SecurityConfig {

    private final RestAccessDeniedHandler accessDeniedHandler;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final CsrfLoggingFilter csrfLoggingFilter;

    public SecurityConfig(RestAccessDeniedHandler accessDeniedHandler,
                          RestAuthenticationEntryPoint authenticationEntryPoint,
                          CsrfLoggingFilter csrfLoggingFilter) {
        this.accessDeniedHandler = accessDeniedHandler;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.csrfLoggingFilter = csrfLoggingFilter;
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // Configure CSRF with cookie-based tokens for SPA (Angular)
        // Use XorCsrfTokenRequestAttributeHandler for better SPA support
        CookieCsrfTokenRepository tokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        tokenRepository.setCookiePath("/");
        tokenRepository.setCookieName("XSRF-TOKEN");

        // Handler combo: XorCsrfTokenRequestAttributeHandler prevents BREACH-style attacks,
        // CsrfTokenRequestAttributeHandler ensures headers are resolved for SPA requests
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
                .addFilterBefore(csrfLoggingFilter, BasicAuthenticationFilter.class)
                .csrf(csrf -> csrf
                        .csrfTokenRepository(tokenRepository)
                        .csrfTokenRequestHandler(requestHandler)
                )
                .cors(cors -> cors.configurationSource(request -> {
                    var c = new CorsConfiguration();
                    c.setAllowedOrigins(List.of("http://localhost:4300", "http://localhost:4200")); // front dev
                    c.setAllowedMethods(List.of("GET","POST","PUT","DELETE","PATCH","OPTIONS"));
                    c.setAllowedHeaders(List.of("*"));
                    c.setAllowCredentials(true);
                    c.setExposedHeaders(List.of("X-XSRF-TOKEN")); // Expose CSRF token header for Angular
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
                        .requestMatchers(HttpMethod.GET, "/api/csrf").permitAll() // CSRF token endpoint
                        .requestMatchers(HttpMethod.GET, "/api/images/**").permitAll() // Public image access
                        .requestMatchers("/api/**").authenticated() // protégé en Basic en dev
                        .anyRequest().denyAll()
                )
                .httpBasic(Customizer.withDefaults())      // Auth Basic pour l'API
                .formLogin(AbstractHttpConfigurer::disable);        // ❌ pas de page de login

        return http.build();
    }
}
