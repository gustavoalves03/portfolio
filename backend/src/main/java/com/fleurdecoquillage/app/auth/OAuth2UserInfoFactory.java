package com.fleurdecoquillage.app.auth;

import com.fleurdecoquillage.app.users.domain.AuthProvider;

import java.util.Map;

public class OAuth2UserInfoFactory {

    public static OAuth2UserInfo getOAuth2UserInfo(String registrationId, Map<String, Object> attributes) {
        if (registrationId.equalsIgnoreCase(AuthProvider.GOOGLE.toString())) {
            return new GoogleOAuth2UserInfo(attributes);
        } else if (registrationId.equalsIgnoreCase(AuthProvider.FACEBOOK.toString())) {
            // TODO: Implement FacebookOAuth2UserInfo when you add Facebook
            throw new IllegalArgumentException("Facebook login not implemented yet");
        } else if (registrationId.equalsIgnoreCase(AuthProvider.APPLE.toString())) {
            // TODO: Implement AppleOAuth2UserInfo when you add Apple
            throw new IllegalArgumentException("Apple login not implemented yet");
        } else {
            throw new IllegalArgumentException("Login with " + registrationId + " is not supported");
        }
    }
}
