package com.prettyface.app.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link OAuth2AuthenticationFailureHandler}.
 * Lot 6 — Authentication / session tests.
 */
class OAuth2AuthenticationFailureHandlerTests {

    private static final String REDIRECT_URI = "http://localhost:4300/oauth2/redirect";

    private OAuth2AuthenticationFailureHandler handler;

    @BeforeEach
    void setUp() {
        handler = new OAuth2AuthenticationFailureHandler();
        ReflectionTestUtils.setField(handler, "authorizedRedirectUri", REDIRECT_URI);
    }

    // Lot6: OAuth2 failure redirects to configured URI with URL-encoded error message
    @Test
    void onAuthenticationFailure_redirectsWithErrorMessage() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AuthenticationException ex = new BadCredentialsException("oauth2_user_canceled");

        handler.onAuthenticationFailure(request, response, ex);

        String location = response.getRedirectedUrl();
        assertThat(location).isNotNull();
        assertThat(location).startsWith(REDIRECT_URI);
        // error query param should carry the localized message
        assertThat(location).contains("error=");
        assertThat(location).contains("oauth2_user_canceled");
    }
}
