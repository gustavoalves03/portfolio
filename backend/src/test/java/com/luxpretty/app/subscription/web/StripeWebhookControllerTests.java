package com.luxpretty.app.subscription.web;

import com.luxpretty.app.subscription.app.SubscriptionEventHandler;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.stripe.webhook-secret=whsec_test")
class StripeWebhookControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SubscriptionEventHandler handler;

    @Test
    void webhook_returnsBadRequest_onInvalidSignature() throws Exception {
        try (MockedStatic<Webhook> mocked = mockStatic(Webhook.class)) {
            mocked.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString()))
                    .thenThrow(new SignatureVerificationException("invalid signature", "sig_bad"));

            mockMvc.perform(post("/api/webhooks/stripe")
                            .with(csrf())
                            .header("Stripe-Signature", "sig_bad")
                            .contentType("application/json")
                            .content("{\"id\":\"evt_x\"}"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void webhook_returns200_onValidEvent() throws Exception {
        Event event = mock(Event.class);
        try (MockedStatic<Webhook> mocked = mockStatic(Webhook.class)) {
            mocked.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString()))
                    .thenReturn(event);
            doNothing().when(handler).handle(any());

            mockMvc.perform(post("/api/webhooks/stripe")
                            .with(csrf())
                            .header("Stripe-Signature", "sig_ok")
                            .contentType("application/json")
                            .content("{\"id\":\"evt_ok\"}"))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void webhook_returns500_onHandlerException() throws Exception {
        Event event = mock(Event.class);
        try (MockedStatic<Webhook> mocked = mockStatic(Webhook.class)) {
            mocked.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString()))
                    .thenReturn(event);
            doThrow(new RuntimeException("downstream boom")).when(handler).handle(any());

            mockMvc.perform(post("/api/webhooks/stripe")
                            .with(csrf())
                            .header("Stripe-Signature", "sig_ok")
                            .contentType("application/json")
                            .content("{\"id\":\"evt_err\"}"))
                    .andExpect(status().isInternalServerError());
        }
    }
}
