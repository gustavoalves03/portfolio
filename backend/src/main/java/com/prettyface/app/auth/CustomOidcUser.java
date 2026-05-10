package com.prettyface.app.auth;

import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

/**
 * OIDC counterpart of {@link CustomOAuth2User}, used for providers that
 * speak OpenID Connect (Google requests {@code openid} scope, so Spring
 * Security uses the OIDC pipeline and produces a {@link OidcUser} rather
 * than a plain {@code OAuth2User}). Carries the local DB user id so the
 * success handler can mint a JWT.
 */
public class CustomOidcUser extends DefaultOidcUser implements OAuth2UserWithId {

    private final Long userId;

    public CustomOidcUser(OidcUser delegate, Long userId) {
        super(delegate.getAuthorities(), delegate.getIdToken(), delegate.getUserInfo());
        this.userId = userId;
    }

    public CustomOidcUser(OidcIdToken idToken, OidcUserInfo userInfo, Long userId) {
        super(null, idToken, userInfo);
        this.userId = userId;
    }

    @Override
    public Long getUserId() {
        return userId;
    }
}
