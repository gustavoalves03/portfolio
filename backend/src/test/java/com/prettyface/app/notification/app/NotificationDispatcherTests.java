package com.prettyface.app.notification.app;

import com.prettyface.app.multitenancy.ApplicationSchemaExecutor;
import com.prettyface.app.notification.domain.*;
import com.prettyface.app.notification.web.dto.NotificationResponse;
import com.prettyface.app.notification.web.mapper.NotificationMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationDispatcherTests {

    @Mock private NotificationService notificationService;
    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock private ApplicationSchemaExecutor applicationSchemaExecutor;

    @InjectMocks
    private NotificationDispatcher dispatcher;

    @Test
    @DisplayName("dispatch persists notification and sends via STOMP for each recipient")
    void dispatch_persistsAndSends() {
        when(applicationSchemaExecutor.call(any())).thenAnswer(invocation -> {
            java.util.function.Supplier<?> supplier = invocation.getArgument(0);
            return supplier.get();
        });

        Notification saved = Notification.builder()
                .id(1L)
                .recipientId(10L)
                .tenantSlug("salon-a")
                .type(NotificationType.NEW_BOOKING)
                .category(NotificationCategory.BOOKING)
                .title("Nouveau rendez-vous")
                .message("Marie Claire - Soin visage, 14h30")
                .referenceId(100L)
                .referenceType(ReferenceType.BOOKING)
                .read(false)
                .build();
        when(notificationService.save(any(Notification.class))).thenReturn(saved);

        dispatcher.dispatch(
                List.of(10L, 20L),
                "salon-a",
                NotificationType.NEW_BOOKING,
                NotificationCategory.BOOKING,
                "Nouveau rendez-vous",
                "Marie Claire - Soin visage, 14h30",
                100L,
                ReferenceType.BOOKING
        );

        verify(notificationService, times(2)).save(any(Notification.class));
        verify(messagingTemplate, times(2))
                .convertAndSendToUser(anyString(), eq("/queue/notifications"), any(NotificationResponse.class));

        ArgumentCaptor<String> userCaptor = ArgumentCaptor.forClass(String.class);
        verify(messagingTemplate, times(2))
                .convertAndSendToUser(userCaptor.capture(), eq("/queue/notifications"), any(NotificationResponse.class));
        assertThat(userCaptor.getAllValues()).containsExactly("10", "20");
    }
}
