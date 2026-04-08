package com.prettyface.app.notification.web.mapper;

import com.prettyface.app.notification.domain.*;
import com.prettyface.app.notification.web.dto.NotificationResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationMapperTests {

    @Test
    @DisplayName("toResponse maps all fields correctly")
    void toResponse_mapsAllFieldsCorrectly() {
        LocalDateTime now = LocalDateTime.of(2026, 4, 8, 14, 30);
        Notification notification = Notification.builder()
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
                .createdAt(now)
                .build();

        NotificationResponse response = NotificationMapper.toResponse(notification);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.type()).isEqualTo("NEW_BOOKING");
        assertThat(response.category()).isEqualTo("BOOKING");
        assertThat(response.title()).isEqualTo("Nouveau rendez-vous");
        assertThat(response.message()).isEqualTo("Marie Claire - Soin visage, 14h30");
        assertThat(response.referenceId()).isEqualTo(100L);
        assertThat(response.referenceType()).isEqualTo("BOOKING");
        assertThat(response.read()).isFalse();
        assertThat(response.tenantSlug()).isEqualTo("salon-a");
        assertThat(response.createdAt()).isEqualTo(now);
    }
}
