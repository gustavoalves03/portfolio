package com.luxpretty.app.mail.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luxpretty.app.auth.CustomOAuth2UserService;
import com.luxpretty.app.auth.CustomOidcUserService;
import com.luxpretty.app.auth.OAuth2AuthenticationFailureHandler;
import com.luxpretty.app.auth.OAuth2AuthenticationSuccessHandler;
import com.luxpretty.app.auth.TokenService;
import com.luxpretty.app.common.error.RestAccessDeniedHandler;
import com.luxpretty.app.common.error.RestAuthenticationEntryPoint;
import com.luxpretty.app.config.CsrfLoggingFilter;
import com.luxpretty.app.config.SecurityConfig;
import com.luxpretty.app.mail.domain.MailOutbox;
import com.luxpretty.app.mail.domain.MailStatus;
import com.luxpretty.app.mail.repo.MailOutboxRepository;
import com.luxpretty.app.mail.web.dto.PostmarkWebhookPayload;
import com.luxpretty.app.multitenancy.TenantFilter;
import com.luxpretty.app.tenant.app.TenantService;
import com.luxpretty.app.users.repo.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PostmarkWebhookController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "app.mail.postmark.webhook-secret=test-secret")
class PostmarkWebhookControllerTests {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    @MockBean UserRepository userRepo;
    @MockBean MailOutboxRepository mailRepo;

    // Required by SecurityConfig (mirrors ProInvoiceControllerTests)
    @MockBean TokenService tokenService;
    @MockBean CustomOAuth2UserService customOAuth2UserService;
    @MockBean CustomOidcUserService customOidcUserService;
    @MockBean OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;
    @MockBean OAuth2AuthenticationFailureHandler oAuth2AuthenticationFailureHandler;
    @MockBean TenantService tenantService;
    @SpyBean RestAccessDeniedHandler restAccessDeniedHandler;
    @SpyBean RestAuthenticationEntryPoint restAuthenticationEntryPoint;
    @SpyBean TenantFilter tenantFilter;
    @SpyBean CsrfLoggingFilter csrfLoggingFilter;

    @Test
    void missing_secret_returns_401() throws Exception {
        PostmarkWebhookPayload p = new PostmarkWebhookPayload("Delivery", null, "x@y.com", "msg-1");
        mvc.perform(post("/api/webhooks/postmark")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(p)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void wrong_secret_returns_401() throws Exception {
        PostmarkWebhookPayload p = new PostmarkWebhookPayload("Delivery", null, "x@y.com", "msg-1");
        mvc.perform(post("/api/webhooks/postmark")
                .header("X-Postmark-Webhook-Token", "wrong")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(p)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void hard_bounce_blocks_user_and_marks_mail_failed() throws Exception {
        MailOutbox m = new MailOutbox();
        m.setProviderMessageId("msg-1");
        m.setStatus(MailStatus.SENT);
        when(mailRepo.findByProviderMessageId("msg-1")).thenReturn(Optional.of(m));

        PostmarkWebhookPayload p = new PostmarkWebhookPayload("Bounce", "HardBounce", "x@y.com", "msg-1");
        mvc.perform(post("/api/webhooks/postmark")
                .header("X-Postmark-Webhook-Token", "test-secret")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(p)))
            .andExpect(status().isOk());

        verify(userRepo).markEmailBlocked("x@y.com");
    }

    @Test
    void soft_bounce_does_not_block() throws Exception {
        PostmarkWebhookPayload p = new PostmarkWebhookPayload("Bounce", "SoftBounce", "x@y.com", "msg-1");
        mvc.perform(post("/api/webhooks/postmark")
                .header("X-Postmark-Webhook-Token", "test-secret")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(p)))
            .andExpect(status().isOk());
        verify(userRepo, never()).markEmailBlocked(anyString());
    }

    @Test
    void spam_complaint_blocks_user() throws Exception {
        PostmarkWebhookPayload p = new PostmarkWebhookPayload("SpamComplaint", null, "x@y.com", "msg-1");
        mvc.perform(post("/api/webhooks/postmark")
                .header("X-Postmark-Webhook-Token", "test-secret")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(p)))
            .andExpect(status().isOk());
        verify(userRepo).markEmailBlocked("x@y.com");
    }

    @Test
    void delivery_sets_delivered_at() throws Exception {
        MailOutbox m = new MailOutbox();
        m.setProviderMessageId("msg-1");
        when(mailRepo.findByProviderMessageId("msg-1")).thenReturn(Optional.of(m));

        PostmarkWebhookPayload p = new PostmarkWebhookPayload("Delivery", null, "x@y.com", "msg-1");
        mvc.perform(post("/api/webhooks/postmark")
                .header("X-Postmark-Webhook-Token", "test-secret")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(p)))
            .andExpect(status().isOk());

        verify(mailRepo).save(m);
    }
}
