package com.luxpretty.app.auth;

import com.luxpretty.app.users.domain.User;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * OIDC variant of {@link CustomOAuth2UserService}. Wired in
 * {@code SecurityConfig.oauth2Login(...).userInfoEndpoint().oidcUserService(this)}.
 *
 * <p>Google requests the {@code openid} scope, so Spring routes its callback
 * through the OIDC pipeline ({@link OidcUserService}) instead of the plain
 * OAuth2 one. Without an OIDC service of our own, Spring would build a
 * {@code DefaultOidcUser} that the success handler cannot cast to
 * {@code CustomOAuth2User}.
 *
 * <p>The user-persistence logic (find-or-link-or-create + tenant
 * provisioning) lives in {@link CustomOAuth2UserService#processOAuth2User}
 * so both flavors stay in sync.
 */
@Service
public class CustomOidcUserService extends OidcUserService {

    private final CustomOAuth2UserService delegate;

    public CustomOidcUserService(CustomOAuth2UserService delegate) {
        this.delegate = delegate;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser oidcUser = super.loadUser(userRequest);

        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        OAuth2UserInfo info = OAuth2UserInfoFactory.getOAuth2UserInfo(
                registrationId, oidcUser.getAttributes());

        if (!StringUtils.hasText(info.getEmail())) {
            throw new OAuth2AuthenticationException("Email not found from OIDC provider");
        }

        User user = delegate.processOAuth2User(registrationId, info);
        return new CustomOidcUser(oidcUser, user.getId());
    }
}
