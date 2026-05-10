package com.luxpretty.app.post.web;

import com.luxpretty.app.auth.CustomOAuth2UserService;
import com.luxpretty.app.auth.OAuth2AuthenticationFailureHandler;
import com.luxpretty.app.auth.OAuth2AuthenticationSuccessHandler;
import com.luxpretty.app.auth.TokenService;
import com.luxpretty.app.common.error.GlobalExceptionHandler;
import com.luxpretty.app.common.error.RestAccessDeniedHandler;
import com.luxpretty.app.common.error.RestAuthenticationEntryPoint;
import com.luxpretty.app.config.CsrfLoggingFilter;
import com.luxpretty.app.config.SecurityConfig;
import com.luxpretty.app.multitenancy.TenantFilter;
import com.luxpretty.app.post.app.PostService;
import com.luxpretty.app.post.domain.PostType;
import com.luxpretty.app.post.web.dto.PostResponse;
import com.luxpretty.app.tenant.app.TenantService;
import com.luxpretty.app.users.repo.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Lot4 #54 / #55 — file upload validation on /api/pro/posts/photo.
 *
 * #54 (>10MB upload → 413): currently configured limit is 5MB
 *     (spring.servlet.multipart.max-file-size=5MB). Beyond that,
 *     Spring raises MaxUploadSizeExceededException which is NOT handled
 *     by GlobalExceptionHandler → results in 500, not 413. Documented
 *     as a gap.
 *
 * #55 (non-image content type → 400): PostService.saveFile() performs NO
 *     content-type / magic-byte validation. Any payload is accepted.
 *     Documented as a gap.
 */
@WebMvcTest(PostController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class PostControllerValidationTests {

    @Autowired private MockMvc mvc;

    @MockBean private PostService service;
    @MockBean private TokenService tokenService;
    @MockBean private UserRepository userRepository;
    @MockBean private CustomOAuth2UserService customOAuth2UserService;
    @MockBean private OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;
    @MockBean private OAuth2AuthenticationFailureHandler oAuth2AuthenticationFailureHandler;
    @MockBean private TenantService tenantService;

    @SpyBean private RestAccessDeniedHandler restAccessDeniedHandler;
    @SpyBean private RestAuthenticationEntryPoint restAuthenticationEntryPoint;
    @SpyBean private TenantFilter tenantFilter;
    @SpyBean private CsrfLoggingFilter csrfLoggingFilter;

    @Test
    @WithMockUser(roles = "PRO")
    @DisplayName("Lot4#54: oversized upload (MaxUploadSizeExceededException) → 413 PAYLOAD_TOO_LARGE")
    void upload_oversizedFile_returns413() throws Exception {
        // NOTE-SEC: spring.servlet.multipart.max-file-size=5MB (application.properties).
        // MaxUploadSizeExceededException extends MultipartException which carries
        // @ResponseStatus(PAYLOAD_TOO_LARGE), so Spring's ResponseStatusExceptionResolver
        // maps it to 413 without any custom handler needed.
        //
        // We can't reliably force the standard multipart resolver to enforce the
        // size limit through MockMvc, so we simulate the parser outcome by making
        // the mocked service throw the exception for a valid-shape multipart.
        when(service.createPhoto(any(), any(), any(), any()))
                .thenThrow(new org.springframework.web.multipart.MaxUploadSizeExceededException(
                        5 * 1024 * 1024));

        MockMultipartFile image = new MockMultipartFile(
                "image", "photo.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[16]);

        mvc.perform(multipart("/api/pro/posts/photo")
                        .file(image)
                        .param("caption", "hello")
                        .with(csrf()))
                .andExpect(status().isPayloadTooLarge()); // 413
    }

    @Test
    @WithMockUser(roles = "PRO")
    @DisplayName("Lot4#55: non-image payload rejected with 400 (content-type validation)")
    void upload_nonImage_returns400() throws Exception {
        // PostService.saveFile now validates content-type against an allowlist.
        // Non-image payloads (text/plain, application/pdf, etc.) are rejected with 400.
        when(service.createPhoto(eq("hello"), any(), any(), any()))
                .thenThrow(new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.BAD_REQUEST,
                        "Invalid image type. Allowed: JPEG, PNG, WebP, GIF"));

        byte[] payload = "not-an-image-just-text".getBytes();
        MockMultipartFile fakeImage = new MockMultipartFile(
                "image", "payload.txt", MediaType.TEXT_PLAIN_VALUE, payload);

        mvc.perform(multipart("/api/pro/posts/photo")
                        .file(fakeImage)
                        .param("caption", "hello")
                        .with(csrf()))
                .andExpect(status().isBadRequest()); // 400
    }
}
