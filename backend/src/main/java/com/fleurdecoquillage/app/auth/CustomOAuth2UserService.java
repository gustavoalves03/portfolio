package com.fleurdecoquillage.app.auth;

import com.fleurdecoquillage.app.users.domain.AuthProvider;
import com.fleurdecoquillage.app.users.domain.User;
import com.fleurdecoquillage.app.users.repo.UserRepository;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Optional;

@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;

    public CustomOAuth2UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oauth2User = super.loadUser(userRequest);

        // Get provider (google, facebook, apple)
        String registrationId = userRequest.getClientRegistration().getRegistrationId();

        // Extract user info from OAuth2 provider
        OAuth2UserInfo oAuth2UserInfo = OAuth2UserInfoFactory.getOAuth2UserInfo(
            registrationId,
            oauth2User.getAttributes()
        );

        if (!StringUtils.hasText(oAuth2UserInfo.getEmail())) {
            throw new OAuth2AuthenticationException("Email not found from OAuth2 provider");
        }

        // Find or create user
        User user = processOAuth2User(registrationId, oAuth2UserInfo);

        return new CustomOAuth2User(oauth2User, user.getId());
    }

    private User processOAuth2User(String registrationId, OAuth2UserInfo oAuth2UserInfo) {
        AuthProvider provider = AuthProvider.valueOf(registrationId.toUpperCase());

        Optional<User> userOptional = userRepository.findByProviderAndProviderId(
            provider,
            oAuth2UserInfo.getId()
        );

        User user;
        if (userOptional.isPresent()) {
            user = userOptional.get();
            // Update existing user info
            user = updateExistingUser(user, oAuth2UserInfo);
        } else {
            // Check if user exists with same email but different provider
            Optional<User> existingUser = userRepository.findByEmail(oAuth2UserInfo.getEmail());
            if (existingUser.isPresent()) {
                throw new OAuth2AuthenticationException(
                    "Email already registered with " + existingUser.get().getProvider() + " provider"
                );
            }
            // Create new user
            user = createNewUser(provider, oAuth2UserInfo);
        }

        return userRepository.save(user);
    }

    private User createNewUser(AuthProvider provider, OAuth2UserInfo oAuth2UserInfo) {
        return User.builder()
            .name(oAuth2UserInfo.getName())
            .email(oAuth2UserInfo.getEmail())
            .imageUrl(oAuth2UserInfo.getImageUrl())
            .provider(provider)
            .providerId(oAuth2UserInfo.getId())
            .emailVerified(true) // OAuth2 providers verify emails
            .build();
    }

    private User updateExistingUser(User existingUser, OAuth2UserInfo oAuth2UserInfo) {
        existingUser.setName(oAuth2UserInfo.getName());
        existingUser.setImageUrl(oAuth2UserInfo.getImageUrl());
        return existingUser;
    }
}
