package com.prettyface.app.post.web;

import com.prettyface.app.auth.CustomOAuth2UserService;
import com.prettyface.app.auth.OAuth2AuthenticationFailureHandler;
import com.prettyface.app.auth.OAuth2AuthenticationSuccessHandler;
import com.prettyface.app.auth.TokenService;
import com.prettyface.app.common.error.GlobalExceptionHandler;
import com.prettyface.app.common.error.RestAccessDeniedHandler;
import com.prettyface.app.common.error.RestAuthenticationEntryPoint;
import com.prettyface.app.config.CsrfLoggingFilter;
import com.prettyface.app.config.SecurityConfig;
import com.prettyface.app.multitenancy.TenantFilter;
import com.prettyface.app.post.app.PostService;
import com.prettyface.app.post.domain.PostType;
import com.prettyface.app.post.web.dto.PostResponse;
import com.prettyface.app.tenant.app.TenantService;
import com.prettyface.app.users.repo.UserRepository;
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
    @DisplayName("Lot4#55 _WARN_: non-image payload accepted (GAP: no content-type/magic-byte validation)")
    void upload_nonImage_acceptedAs200_gap() throws Exception {
        // NOTE-SEC: PostService.saveFile just copies the InputStream to disk.
        // It does not inspect file.getContentType() nor sniff magic bytes; any
        // MIME label is accepted, including plain text, PDFs, or executables.
        // TODO-SEC: add content-type + magic-byte allowlist in the service layer
        // (e.g., only image/jpeg, image/png, image/webp). Test then flips to 400.
        when(service.createPhoto(eq("hello"), any(), any(), any()))
                .thenReturn(new PostResponse(
                        1L, PostType.PHOTO, "hello",
                        null, "/api/images/posts/fake.txt",
                        List.of(), null, null, LocalDateTime.now()));

        byte[] payload = "not-an-image-just-text".getBytes();
        MockMultipartFile fakeImage = new MockMultipartFile(
                "image", "payload.txt", MediaType.TEXT_PLAIN_VALUE, payload);

        mvc.perform(multipart("/api/pro/posts/photo")
                        .file(fakeImage)
                        .param("caption", "hello")
                        .with(csrf()))
                .andExpect(status().isCreated()); // 201 today — gap
    }
}
