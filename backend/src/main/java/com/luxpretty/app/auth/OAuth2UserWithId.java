package com.luxpretty.app.auth;

/**
 * Common contract implemented by both {@link CustomOAuth2User} (plain OAuth2,
 * e.g. Facebook) and {@link CustomOidcUser} (OIDC, e.g. Google with scope
 * openid). The success handler relies on this to extract the local DB user
 * id without caring which provider flavor authenticated the request.
 */
public interface OAuth2UserWithId {
    Long getUserId();
}
