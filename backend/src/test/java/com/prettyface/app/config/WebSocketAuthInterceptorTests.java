package com.prettyface.app.config;

import com.prettyface.app.auth.TokenService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebSocketAuthInterceptorTests {

    @Mock private TokenService tokenService;
    @Mock private MessageChannel channel;

    @InjectMocks
    private WebSocketAuthInterceptor interceptor;

    private static Message<?> buildMessage(StompHeaderAccessor accessor) {
        // setLeaveMutable(true) keeps MutableMessageHeaders writable after MessageBuilder
        // freezes them, so the interceptor can call accessor.setUser() inside preSend().
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    @Test
    @DisplayName("valid token on CONNECT sets user principal with userId")
    void preSend_validToken_setsUserPrincipal() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.addNativeHeader("Authorization", "Bearer valid-jwt-token");
        Message<?> message = buildMessage(accessor);

        when(tokenService.validateToken("valid-jwt-token")).thenReturn(true);
        when(tokenService.getUserIdFromToken("valid-jwt-token")).thenReturn(42L);

        Message<?> result = interceptor.preSend(message, channel);

        StompHeaderAccessor resultAccessor = StompHeaderAccessor.wrap(result);
        assertThat(resultAccessor.getUser()).isNotNull();
        assertThat(resultAccessor.getUser().getName()).isEqualTo("42");
    }

    @Test
    @DisplayName("invalid token on CONNECT throws exception")
    void preSend_invalidToken_throwsException() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.addNativeHeader("Authorization", "Bearer invalid-token");
        Message<?> message = buildMessage(accessor);

        when(tokenService.validateToken("invalid-token")).thenReturn(false);

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid JWT token");
    }

    @Test
    @DisplayName("missing Authorization header on CONNECT throws exception")
    void preSend_missingAuthHeader_throwsException() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        Message<?> message = buildMessage(accessor);

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Missing Authorization header");
    }

    @Test
    @DisplayName("non-CONNECT command passes through without auth")
    void preSend_nonConnectCommand_passesThrough() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        Message<?> message = buildMessage(accessor);

        Message<?> result = interceptor.preSend(message, channel);

        assertThat(result).isEqualTo(message);
        verifyNoInteractions(tokenService);
    }
}
