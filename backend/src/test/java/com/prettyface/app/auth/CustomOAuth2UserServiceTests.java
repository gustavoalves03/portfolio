package com.prettyface.app.auth;

import com.prettyface.app.tenant.app.TenantProvisioningService;
import com.prettyface.app.tenant.domain.Tenant;
import com.prettyface.app.tenant.repo.TenantRepository;
import com.prettyface.app.users.domain.AuthProvider;
import com.prettyface.app.users.domain.Role;
import com.prettyface.app.users.domain.User;
import com.prettyface.app.users.repo.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CustomOAuth2UserService.
 *
 * Uses a testable subclass to bypass the parent class DefaultOAuth2UserService.loadUser()
 * which makes real HTTP calls to the OAuth2 provider during the standard flow.
 */
@ExtendWith(MockitoExtension.class)
class CustomOAuth2UserServiceTests {

    @Mock
    private UserRepository userRepository;

    @Mock
    private TenantProvisioningService tenantProvisioningService;

    @Mock
    private TenantRepository tenantRepository;

    /**
     * Subclass that bypasses the DefaultOAuth2UserService HTTP call by overriding loadUser()
     * to use a pre-set OAuth2User. Delegates to the real processOAuth2User (now protected).
     */
    private class StubCustomOAuth2UserService extends CustomOAuth2UserService {

        private OAuth2User stubbedUser;

        StubCustomOAuth2UserService(UserRepository userRepository,
                                     TenantProvisioningService tenantProvisioningService,
                                     TenantRepository tenantRepository) {
            super(userRepository, tenantProvisioningService, tenantRepository);
        }

        void setStubbedUser(OAuth2User user) {
            this.stubbedUser = user;
        }

        @Override
        public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
            String registrationId = userRequest.getClientRegistration().getRegistrationId();
            OAuth2UserInfo oAuth2UserInfo = OAuth2UserInfoFactory.getOAuth2UserInfo(
                registrationId, stubbedUser.getAttributes()
            );

            if (!StringUtils.hasText(oAuth2UserInfo.getEmail())) {
                throw new OAuth2AuthenticationException("Email not found from OAuth2 provider");
            }

            User user = processOAuth2User(registrationId, oAuth2UserInfo);
            return new CustomOAuth2User(stubbedUser, user.getId());
        }
    }

    private StubCustomOAuth2UserService service;

    private OAuth2UserRequest buildGoogleUserRequest() {
        ClientRegistration registration = ClientRegistration.withRegistrationId("google")
            .clientId("client-id")
            .clientSecret("client-secret")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
            .authorizationUri("https://accounts.google.com/o/oauth2/auth")
            .tokenUri("https://oauth2.googleapis.com/token")
            .userInfoUri("https://www.googleapis.com/oauth2/v3/userinfo")
            .userNameAttributeName("sub")
            .build();

        OAuth2AccessToken accessToken = new OAuth2AccessToken(
            OAuth2AccessToken.TokenType.BEARER, "token", Instant.now(), Instant.now().plusSeconds(3600)
        );
        return new OAuth2UserRequest(registration, accessToken);
    }

    private OAuth2User mockGoogleUser(String sub, String name, String email) {
        OAuth2User user = mock(OAuth2User.class);
        java.util.HashMap<String, Object> attrs = new java.util.HashMap<>();
        attrs.put("sub", sub);
        attrs.put("name", name);
        if (email != null) {
            attrs.put("email", email);
            attrs.put("picture", "https://photo.url");
        }
        when(user.getAttributes()).thenReturn(attrs);
        return user;
    }

    @BeforeEach
    void setUp() {
        service = new StubCustomOAuth2UserService(userRepository, tenantProvisioningService, tenantRepository);
    }

    // B4.1: New Google user → creates user with PRO role and provisions tenant schema
    @Test
    void loadUser_newGoogleUser_createsUserWithProRoleAndProvisionsTenant() {
        OAuth2UserRequest request = buildGoogleUserRequest();
        service.setStubbedUser(mockGoogleUser("sub-123", "Sophie Martin", "sophie@salon.fr"));

        when(userRepository.findByProviderAndProviderId(AuthProvider.GOOGLE, "sub-123"))
            .thenReturn(Optional.empty());
        when(userRepository.findByEmail("sophie@salon.fr")).thenReturn(Optional.empty());

        User savedUser = User.builder().id(1L).name("Sophie Martin").email("sophie@salon.fr")
            .provider(AuthProvider.GOOGLE).providerId("sub-123").role(Role.PRO).emailVerified(true).build();
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(tenantRepository.findByOwnerId(1L)).thenReturn(Optional.empty());
        when(tenantProvisioningService.provision(any(User.class))).thenReturn(mock(Tenant.class));

        CustomOAuth2User result = (CustomOAuth2User) service.loadUser(request);

        assertThat(result.getUserId()).isEqualTo(1L);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getRole()).isEqualTo(Role.PRO);
        assertThat(captor.getValue().getEmailVerified()).isTrue();
        assertThat(captor.getValue().getProvider()).isEqualTo(AuthProvider.GOOGLE);

        verify(tenantProvisioningService, times(1)).provision(any(User.class));
    }

    // B4.2: Existing Google user → updates name + imageUrl, no tenant provisioning
    @Test
    void loadUser_existingGoogleUser_updatesNameAndImageUrl() {
        OAuth2UserRequest request = buildGoogleUserRequest();
        service.setStubbedUser(mockGoogleUser("sub-123", "Sophie Updated", "sophie@salon.fr"));

        User existingUser = User.builder().id(1L).name("Sophie Old")
            .provider(AuthProvider.GOOGLE).providerId("sub-123").role(Role.PRO)
            .imageUrl("https://old.url").build();
        when(userRepository.findByProviderAndProviderId(AuthProvider.GOOGLE, "sub-123"))
            .thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(User.class))).thenReturn(existingUser);

        CustomOAuth2User result = (CustomOAuth2User) service.loadUser(request);

        assertThat(result.getUserId()).isEqualTo(1L);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("Sophie Updated");

        verify(tenantProvisioningService, never()).provision(any());
        verify(tenantRepository, never()).findByOwnerId(any());
    }

    // B4.3: LOCAL account with same email → provider linked to Google (account linking / AC: 2)
    @Test
    void loadUser_localAccountWithSameEmail_linksGoogleProvider() {
        OAuth2UserRequest request = buildGoogleUserRequest();
        service.setStubbedUser(mockGoogleUser("sub-456", "Sophie Martin", "sophie@salon.fr"));

        when(userRepository.findByProviderAndProviderId(AuthProvider.GOOGLE, "sub-456"))
            .thenReturn(Optional.empty());

        User localUser = User.builder().id(2L).name("Sophie Martin").email("sophie@salon.fr")
            .provider(AuthProvider.LOCAL).password("$2a$hash").role(Role.PRO).emailVerified(false).build();
        when(userRepository.findByEmail("sophie@salon.fr")).thenReturn(Optional.of(localUser));
        when(userRepository.save(any(User.class))).thenReturn(localUser);

        CustomOAuth2User result = (CustomOAuth2User) service.loadUser(request);

        assertThat(result.getUserId()).isEqualTo(2L);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getProvider()).isEqualTo(AuthProvider.GOOGLE);
        assertThat(captor.getValue().getProviderId()).isEqualTo("sub-456");
        assertThat(captor.getValue().getEmailVerified()).isTrue();

        verify(tenantProvisioningService, never()).provision(any());
    }

    // B4.4: Email missing from Google payload → throws OAuth2AuthenticationException
    @Test
    void loadUser_missingEmail_throwsOAuth2Exception() {
        OAuth2UserRequest request = buildGoogleUserRequest();
        service.setStubbedUser(mockGoogleUser("sub-789", "No Email User", null));

        assertThatThrownBy(() -> service.loadUser(request))
            .isInstanceOf(OAuth2AuthenticationException.class)
            .satisfies(ex -> {
                OAuth2AuthenticationException oauthEx = (OAuth2AuthenticationException) ex;
                String errorCode = oauthEx.getError().getErrorCode();
                assertThat(errorCode).contains("Email not found");
            });

        verifyNoInteractions(userRepository, tenantProvisioningService, tenantRepository);
    }
}
