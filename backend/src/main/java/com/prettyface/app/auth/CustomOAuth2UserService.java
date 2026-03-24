package com.prettyface.app.auth;

import com.prettyface.app.tenant.app.TenantProvisioningService;
import com.prettyface.app.tenant.repo.TenantRepository;
import com.prettyface.app.users.domain.AuthProvider;
import com.prettyface.app.users.domain.Role;
import com.prettyface.app.users.domain.User;
import com.prettyface.app.users.repo.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;

@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private static final Logger logger = LoggerFactory.getLogger(CustomOAuth2UserService.class);

    private final UserRepository userRepository;
    private final TenantProvisioningService tenantProvisioningService;
    private final TenantRepository tenantRepository;

    public CustomOAuth2UserService(UserRepository userRepository,
                                   TenantProvisioningService tenantProvisioningService,
                                   TenantRepository tenantRepository) {
        this.userRepository = userRepository;
        this.tenantProvisioningService = tenantProvisioningService;
        this.tenantRepository = tenantRepository;
    }

    private boolean isClientRoleHint() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) return false;
            HttpServletRequest request = attrs.getRequest();
            if (request.getCookies() == null) return false;
            for (Cookie cookie : request.getCookies()) {
                if (OAuth2RoleHintFilter.ROLE_HINT_COOKIE.equals(cookie.getName())) {
                    return "client".equalsIgnoreCase(cookie.getValue());
                }
            }
        } catch (Exception e) {
            logger.warn("Could not read role hint cookie", e);
        }
        return false;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oauth2User = super.loadUser(userRequest);

        String registrationId = userRequest.getClientRegistration().getRegistrationId();

        OAuth2UserInfo oAuth2UserInfo = OAuth2UserInfoFactory.getOAuth2UserInfo(
            registrationId,
            oauth2User.getAttributes()
        );

        if (!StringUtils.hasText(oAuth2UserInfo.getEmail())) {
            throw new OAuth2AuthenticationException("Email not found from OAuth2 provider");
        }

        User user = processOAuth2User(registrationId, oAuth2UserInfo);

        return new CustomOAuth2User(oauth2User, user.getId());
    }

    @Transactional
    protected User processOAuth2User(String registrationId, OAuth2UserInfo oAuth2UserInfo) {
        AuthProvider provider = AuthProvider.valueOf(registrationId.toUpperCase());

        // Check if a user already exists with this specific provider + providerId (returning OAuth2 user)
        Optional<User> userByProviderId = userRepository.findByProviderAndProviderId(
            provider,
            oAuth2UserInfo.getId()
        );

        if (userByProviderId.isPresent()) {
            User user = userByProviderId.get();
            user = updateExistingUser(user, oAuth2UserInfo);
            return userRepository.save(user);
        }

        // Check if a user exists with the same email (possible account linking scenario)
        Optional<User> userByEmail = userRepository.findByEmail(oAuth2UserInfo.getEmail());
        if (userByEmail.isPresent()) {
            // AC: 2 — Link Google OAuth2 to existing account instead of throwing
            User existing = userByEmail.get();
            logger.info("Linking {} OAuth2 provider to existing account for email: {}",
                provider, oAuth2UserInfo.getEmail());
            existing.setProvider(provider);
            existing.setProviderId(oAuth2UserInfo.getId());
            existing.setImageUrl(oAuth2UserInfo.getImageUrl());
            existing.setEmailVerified(true);
            return userRepository.save(existing);
        }

        // New user — create account; role depends on context (client vs pro)
        boolean isClient = isClientRoleHint();
        User newUser = createNewUser(provider, oAuth2UserInfo, isClient ? Role.USER : Role.PRO);
        User savedUser = userRepository.save(newUser);

        // Only provision tenant for PRO users
        if (!isClient) {
            boolean tenantExists = tenantRepository.findByOwnerId(savedUser.getId()).isPresent();
            if (!tenantExists) {
                logger.info("Provisioning tenant for new OAuth2 user: {}", savedUser.getEmail());
                tenantProvisioningService.provision(savedUser);
            }
        }

        return savedUser;
    }

    private User createNewUser(AuthProvider provider, OAuth2UserInfo oAuth2UserInfo, Role role) {
        return User.builder()
            .name(oAuth2UserInfo.getName())
            .email(oAuth2UserInfo.getEmail())
            .imageUrl(oAuth2UserInfo.getImageUrl())
            .provider(provider)
            .providerId(oAuth2UserInfo.getId())
            .role(role)
            .emailVerified(true) // OAuth2 providers verify emails
            .build();
    }

    private User updateExistingUser(User existingUser, OAuth2UserInfo oAuth2UserInfo) {
        existingUser.setName(oAuth2UserInfo.getName());
        existingUser.setImageUrl(oAuth2UserInfo.getImageUrl());
        return existingUser;
    }
}
