# WebSocket Notifications Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add real-time WebSocket notifications so PRO and EMPLOYEE users are notified instantly when a client creates or cancels a booking, with persistent notification storage and a dedicated notifications page.

**Architecture:** STOMP over SockJS backend (Spring Boot starter-websocket) with per-user queues, a shared-schema `NOTIFICATIONS` table for persistence, and an Angular frontend using `@stomp/stompjs` with a global NgRx SignalStore for badge counts and toast display.

**Tech Stack:** Spring Boot 3.5 WebSocket + STOMP, `@stomp/stompjs` 7.x, Angular 20 SignalStore, Oracle DB shared schema.

---

## File Map

### Backend — New Files
| File | Responsibility |
|------|---------------|
| `backend/src/main/java/com/prettyface/app/notification/domain/Notification.java` | JPA entity |
| `backend/src/main/java/com/prettyface/app/notification/domain/NotificationType.java` | Enum: NEW_BOOKING, BOOKING_CANCELLED, ... |
| `backend/src/main/java/com/prettyface/app/notification/domain/NotificationCategory.java` | Enum: BOOKING, LEAVE, SOCIAL |
| `backend/src/main/java/com/prettyface/app/notification/domain/ReferenceType.java` | Enum: BOOKING, LEAVE_REQUEST, POST |
| `backend/src/main/java/com/prettyface/app/notification/repo/NotificationRepository.java` | Spring Data JPA repository |
| `backend/src/main/java/com/prettyface/app/notification/app/NotificationService.java` | CRUD + business logic |
| `backend/src/main/java/com/prettyface/app/notification/app/NotificationDispatcher.java` | Persist + send via STOMP |
| `backend/src/main/java/com/prettyface/app/notification/web/dto/NotificationResponse.java` | Response DTO |
| `backend/src/main/java/com/prettyface/app/notification/web/mapper/NotificationMapper.java` | Entity → DTO mapper |
| `backend/src/main/java/com/prettyface/app/notification/web/NotificationController.java` | REST endpoints |
| `backend/src/main/java/com/prettyface/app/config/WebSocketConfig.java` | STOMP + SockJS config |
| `backend/src/main/java/com/prettyface/app/config/WebSocketAuthInterceptor.java` | JWT auth on STOMP CONNECT |
| `backend/src/test/java/com/prettyface/app/notification/app/NotificationServiceTests.java` | Unit tests |
| `backend/src/test/java/com/prettyface/app/notification/app/NotificationDispatcherTests.java` | Unit tests |

### Backend — Modified Files
| File | Change |
|------|--------|
| `backend/pom.xml:~91` | Add `spring-boot-starter-websocket` dependency |
| `backend/.../config/ApplicationSchemaMigrator.java:~129` | Add NOTIFICATIONS table + index creation |
| `backend/.../config/SecurityConfig.java:~120,~163` | Allow `/ws/**` endpoint, CSRF exception |
| `backend/.../bookings/app/CareBookingService.java:~40,~269,~293` | Inject NotificationDispatcher, dispatch on create/cancel |

### Frontend — New Files
| File | Responsibility |
|------|---------------|
| `frontend/src/app/features/notifications/models/notification.model.ts` | TypeScript interfaces |
| `frontend/src/app/features/notifications/services/notifications.service.ts` | REST calls |
| `frontend/src/app/features/notifications/services/websocket.service.ts` | STOMP connection |
| `frontend/src/app/features/notifications/store/notifications.store.ts` | Global SignalStore |
| `frontend/src/app/features/notifications/components/notification-toast/notification-toast.component.ts` | Toast component |
| `frontend/src/app/pages/notifications/notifications.component.ts` | Notifications page |
| `frontend/src/app/pages/notifications/notifications.component.html` | Page template |
| `frontend/src/app/pages/notifications/notifications.component.scss` | Page styles |

### Frontend — Modified Files
| File | Change |
|------|--------|
| `frontend/package.json:~39` | Add `@stomp/stompjs` |
| `frontend/src/app/app.config.ts:~45` | Provide NotificationsStore globally |
| `frontend/src/app/app.ts:~10` | Import toast component |
| `frontend/src/app/app.html:~2` | Add `<app-notification-toast>` |
| `frontend/src/app/app.routes.ts:~155` | Add `/notifications` route |
| `frontend/src/app/shared/layout/header/header.ts` | Inject NotificationsStore, add bell icon |
| `frontend/src/app/shared/layout/header/header.html:~54` | Bell icon with badge |
| `frontend/src/app/shared/layout/header/header.scss` | Badge styles |
| `frontend/src/app/shared/layout/bottom-nav/bottom-nav.component.ts:~10,~33` | Add badge to Bookings tab |
| `frontend/public/i18n/fr.json` | Notification translations |
| `frontend/public/i18n/en.json` | Notification translations |

---

### Task 1: Backend — Add WebSocket dependency and NOTIFICATIONS table

**Files:**
- Modify: `backend/pom.xml:91`
- Modify: `backend/src/main/java/com/prettyface/app/config/ApplicationSchemaMigrator.java:24-130`

- [ ] **Step 1: Add spring-boot-starter-websocket to pom.xml**

In `backend/pom.xml`, after the `spring-boot-starter-thymeleaf` dependency (around line 90), add:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-websocket</artifactId>
</dependency>
```

- [ ] **Step 2: Add NOTIFICATIONS table creation to ApplicationSchemaMigrator**

In `backend/src/main/java/com/prettyface/app/config/ApplicationSchemaMigrator.java`, add a constant for the table and index names alongside existing ones (around line 24):

```java
private static final String NOTIFICATIONS_TABLE = "NOTIFICATIONS";
private static final String NOTIFICATIONS_RECIPIENT_INDEX = "IDX_NOTIF_RECIPIENT";
```

Then add a new method `ensureNotificationsTable()` after the `ensureClientBookingHistoryTable()` method (after line 130):

```java
private void ensureNotificationsTable() throws SQLException {
    try (Connection connection = dataSource.getConnection();
         Statement stmt = connection.createStatement()) {
        createOracleObjectIfMissing(stmt, """
                CREATE TABLE NOTIFICATIONS (
                    ID NUMBER(19) GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                    RECIPIENT_ID NUMBER(19) NOT NULL,
                    TENANT_SLUG VARCHAR2(100 CHAR) NOT NULL,
                    TYPE VARCHAR2(50 CHAR) NOT NULL,
                    CATEGORY VARCHAR2(30 CHAR) NOT NULL,
                    TITLE VARCHAR2(255 CHAR) NOT NULL,
                    MESSAGE VARCHAR2(500 CHAR) NOT NULL,
                    REFERENCE_ID NUMBER(19) NOT NULL,
                    REFERENCE_TYPE VARCHAR2(50 CHAR) NOT NULL,
                    IS_READ NUMBER(1) DEFAULT 0 NOT NULL,
                    CREATED_AT TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
                )
                """, NOTIFICATIONS_TABLE);
        createOracleObjectIfMissing(
                stmt,
                "CREATE INDEX " + NOTIFICATIONS_RECIPIENT_INDEX
                        + " ON NOTIFICATIONS (RECIPIENT_ID, IS_READ, CREATED_AT DESC)",
                NOTIFICATIONS_RECIPIENT_INDEX
        );
    }
}
```

Call `ensureNotificationsTable()` from the `run()` method, right after `ensureClientBookingHistoryTable()`.

- [ ] **Step 3: Verify the backend compiles**

Run: `cd /Users/Gustavo.alves/Documents/personal/portfolio/backend && mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add backend/pom.xml backend/src/main/java/com/prettyface/app/config/ApplicationSchemaMigrator.java
git commit -m "feat: add websocket dependency and NOTIFICATIONS table migration"
```

---

### Task 2: Backend — Notification domain model (entity + enums)

**Files:**
- Create: `backend/src/main/java/com/prettyface/app/notification/domain/NotificationType.java`
- Create: `backend/src/main/java/com/prettyface/app/notification/domain/NotificationCategory.java`
- Create: `backend/src/main/java/com/prettyface/app/notification/domain/ReferenceType.java`
- Create: `backend/src/main/java/com/prettyface/app/notification/domain/Notification.java`

- [ ] **Step 1: Create NotificationType enum**

```java
package com.prettyface.app.notification.domain;

public enum NotificationType {
    NEW_BOOKING,
    BOOKING_CANCELLED,
    LEAVE_REQUEST,
    SICK_LEAVE,
    POST_LIKED
}
```

- [ ] **Step 2: Create NotificationCategory enum**

```java
package com.prettyface.app.notification.domain;

public enum NotificationCategory {
    BOOKING,
    LEAVE,
    SOCIAL
}
```

- [ ] **Step 3: Create ReferenceType enum**

```java
package com.prettyface.app.notification.domain;

public enum ReferenceType {
    BOOKING,
    LEAVE_REQUEST,
    POST
}
```

- [ ] **Step 4: Create Notification entity**

```java
package com.prettyface.app.notification.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "NOTIFICATIONS")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "RECIPIENT_ID", nullable = false)
    private Long recipientId;

    @Column(name = "TENANT_SLUG", nullable = false, length = 100)
    private String tenantSlug;

    @Enumerated(EnumType.STRING)
    @Column(name = "TYPE", nullable = false, length = 50)
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "CATEGORY", nullable = false, length = 30)
    private NotificationCategory category;

    @Column(name = "TITLE", nullable = false, length = 255)
    private String title;

    @Column(name = "MESSAGE", nullable = false, length = 500)
    private String message;

    @Column(name = "REFERENCE_ID", nullable = false)
    private Long referenceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "REFERENCE_TYPE", nullable = false, length = 50)
    private ReferenceType referenceType;

    @Column(name = "IS_READ", nullable = false)
    private boolean read;

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
```

- [ ] **Step 5: Verify compilation**

Run: `cd /Users/Gustavo.alves/Documents/personal/portfolio/backend && mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/prettyface/app/notification/domain/
git commit -m "feat: add Notification entity and enums"
```

---

### Task 3: Backend — Repository, DTO, Mapper

**Files:**
- Create: `backend/src/main/java/com/prettyface/app/notification/repo/NotificationRepository.java`
- Create: `backend/src/main/java/com/prettyface/app/notification/web/dto/NotificationResponse.java`
- Create: `backend/src/main/java/com/prettyface/app/notification/web/mapper/NotificationMapper.java`

- [ ] **Step 1: Create NotificationRepository**

```java
package com.prettyface.app.notification.repo;

import com.prettyface.app.notification.domain.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByRecipientIdOrderByCreatedAtDesc(Long recipientId, Pageable pageable);

    Page<Notification> findByRecipientIdAndReadOrderByCreatedAtDesc(Long recipientId, boolean read, Pageable pageable);

    long countByRecipientIdAndReadFalse(Long recipientId);

    List<Notification> findByRecipientIdAndReadFalseOrderByCreatedAtDesc(Long recipientId);
}
```

- [ ] **Step 2: Create NotificationResponse DTO**

```java
package com.prettyface.app.notification.web.dto;

import java.time.LocalDateTime;

public record NotificationResponse(
        Long id,
        String type,
        String category,
        String title,
        String message,
        Long referenceId,
        String referenceType,
        boolean read,
        String tenantSlug,
        LocalDateTime createdAt
) {}
```

- [ ] **Step 3: Create NotificationMapper**

```java
package com.prettyface.app.notification.web.mapper;

import com.prettyface.app.notification.domain.Notification;
import com.prettyface.app.notification.web.dto.NotificationResponse;

public class NotificationMapper {

    private NotificationMapper() {}

    public static NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType().name(),
                notification.getCategory().name(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getReferenceId(),
                notification.getReferenceType().name(),
                notification.isRead(),
                notification.getTenantSlug(),
                notification.getCreatedAt()
        );
    }
}
```

- [ ] **Step 4: Verify compilation**

Run: `cd /Users/Gustavo.alves/Documents/personal/portfolio/backend && mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/prettyface/app/notification/repo/ backend/src/main/java/com/prettyface/app/notification/web/
git commit -m "feat: add notification repository, DTO, and mapper"
```

---

### Task 4: Backend — NotificationService (CRUD + business logic)

**Files:**
- Create: `backend/src/main/java/com/prettyface/app/notification/app/NotificationService.java`
- Create: `backend/src/test/java/com/prettyface/app/notification/app/NotificationServiceTests.java`

- [ ] **Step 1: Write the failing test**

```java
package com.prettyface.app.notification.app;

import com.prettyface.app.notification.domain.Notification;
import com.prettyface.app.notification.domain.NotificationCategory;
import com.prettyface.app.notification.domain.NotificationType;
import com.prettyface.app.notification.domain.ReferenceType;
import com.prettyface.app.notification.repo.NotificationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTests {

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private NotificationService notificationService;

    @Test
    @DisplayName("countUnread returns count of unread notifications for recipient")
    void countUnread_returnsCount() {
        when(notificationRepository.countByRecipientIdAndReadFalse(1L)).thenReturn(5L);

        long count = notificationService.countUnread(1L);

        assertThat(count).isEqualTo(5L);
        verify(notificationRepository).countByRecipientIdAndReadFalse(1L);
    }

    @Test
    @DisplayName("markAsRead sets read=true and saves")
    void markAsRead_setsReadTrue() {
        Notification notification = Notification.builder()
                .id(10L)
                .recipientId(1L)
                .tenantSlug("salon-a")
                .type(NotificationType.NEW_BOOKING)
                .category(NotificationCategory.BOOKING)
                .title("New booking")
                .message("Test message")
                .referenceId(100L)
                .referenceType(ReferenceType.BOOKING)
                .read(false)
                .createdAt(LocalDateTime.now())
                .build();

        when(notificationRepository.findById(10L)).thenReturn(Optional.of(notification));

        notificationService.markAsRead(10L, 1L);

        assertThat(notification.isRead()).isTrue();
        verify(notificationRepository).save(notification);
    }

    @Test
    @DisplayName("markAsRead throws when notification belongs to another user")
    void markAsRead_throwsWhenWrongRecipient() {
        Notification notification = Notification.builder()
                .id(10L)
                .recipientId(2L)
                .read(false)
                .build();

        when(notificationRepository.findById(10L)).thenReturn(Optional.of(notification));

        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> notificationService.markAsRead(10L, 1L)
        );
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /Users/Gustavo.alves/Documents/personal/portfolio/backend && mvn test -pl . -Dtest="NotificationServiceTests" -q`
Expected: FAIL — `NotificationService` class does not exist

- [ ] **Step 3: Implement NotificationService**

```java
package com.prettyface.app.notification.app;

import com.prettyface.app.notification.domain.Notification;
import com.prettyface.app.notification.repo.NotificationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Transactional(readOnly = true)
    public Page<Notification> listForRecipient(Long recipientId, Boolean read, Pageable pageable) {
        if (read != null) {
            return notificationRepository.findByRecipientIdAndReadOrderByCreatedAtDesc(recipientId, read, pageable);
        }
        return notificationRepository.findByRecipientIdOrderByCreatedAtDesc(recipientId, pageable);
    }

    @Transactional(readOnly = true)
    public long countUnread(Long recipientId) {
        return notificationRepository.countByRecipientIdAndReadFalse(recipientId);
    }

    @Transactional
    public void markAsRead(Long notificationId, Long recipientId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
        if (!notification.getRecipientId().equals(recipientId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }
        notification.setRead(true);
        notificationRepository.save(notification);
    }

    @Transactional
    public Notification save(Notification notification) {
        return notificationRepository.save(notification);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd /Users/Gustavo.alves/Documents/personal/portfolio/backend && mvn test -pl . -Dtest="NotificationServiceTests" -q`
Expected: PASS — all 3 tests green

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/prettyface/app/notification/app/NotificationService.java backend/src/test/java/com/prettyface/app/notification/app/NotificationServiceTests.java
git commit -m "feat: add NotificationService with unit tests"
```

---

### Task 5: Backend — WebSocket configuration + JWT auth interceptor

**Files:**
- Create: `backend/src/main/java/com/prettyface/app/config/WebSocketConfig.java`
- Create: `backend/src/main/java/com/prettyface/app/config/WebSocketAuthInterceptor.java`
- Modify: `backend/src/main/java/com/prettyface/app/config/SecurityConfig.java:120,163`

- [ ] **Step 1: Create WebSocketConfig**

```java
package com.prettyface.app.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketAuthInterceptor webSocketAuthInterceptor;

    public WebSocketConfig(WebSocketAuthInterceptor webSocketAuthInterceptor) {
        this.webSocketAuthInterceptor = webSocketAuthInterceptor;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/queue");
        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(webSocketAuthInterceptor);
    }
}
```

- [ ] **Step 2: Create WebSocketAuthInterceptor**

This intercepts STOMP CONNECT frames, extracts the JWT from the `Authorization` header (passed as a STOMP header by the client), validates it, and sets a `Principal` whose `getName()` returns the user ID as String.

```java
package com.prettyface.app.config;

import com.prettyface.app.auth.TokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.security.Principal;

@Component
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketAuthInterceptor.class);
    private final TokenService tokenService;

    public WebSocketAuthInterceptor(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                if (tokenService.validateToken(token)) {
                    Long userId = tokenService.getUserIdFromToken(token);
                    accessor.setUser(new StompPrincipal(userId.toString()));
                    logger.debug("WebSocket CONNECT authenticated for user {}", userId);
                } else {
                    logger.warn("WebSocket CONNECT with invalid JWT");
                    throw new IllegalArgumentException("Invalid JWT token");
                }
            } else {
                logger.warn("WebSocket CONNECT without Authorization header");
                throw new IllegalArgumentException("Missing Authorization header");
            }
        }
        return message;
    }

    private record StompPrincipal(String name) implements Principal {
        @Override
        public String getName() {
            return name;
        }
    }
}
```

- [ ] **Step 3: Update SecurityConfig — add CSRF exception and permit WebSocket endpoint**

In `backend/src/main/java/com/prettyface/app/config/SecurityConfig.java`:

At line 120, update CSRF ignore to include `/ws/**`:
```java
.ignoringRequestMatchers("/oauth2/**", "/api/auth/**", "/ws/**")
```

At line 163, before `.anyRequest().permitAll()`, add:
```java
.requestMatchers("/ws/**").permitAll()
```

- [ ] **Step 4: Verify compilation**

Run: `cd /Users/Gustavo.alves/Documents/personal/portfolio/backend && mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/prettyface/app/config/WebSocketConfig.java backend/src/main/java/com/prettyface/app/config/WebSocketAuthInterceptor.java backend/src/main/java/com/prettyface/app/config/SecurityConfig.java
git commit -m "feat: add WebSocket STOMP config with JWT auth interceptor"
```

---

### Task 6: Backend — NotificationDispatcher (persist + send via STOMP)

**Files:**
- Create: `backend/src/main/java/com/prettyface/app/notification/app/NotificationDispatcher.java`
- Create: `backend/src/test/java/com/prettyface/app/notification/app/NotificationDispatcherTests.java`

- [ ] **Step 1: Write the failing test**

```java
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
        // applicationSchemaExecutor.call() should execute the supplier directly in test
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

        // Should save twice (one per recipient)
        verify(notificationService, times(2)).save(any(Notification.class));

        // Should send via STOMP twice
        verify(messagingTemplate, times(2))
                .convertAndSendToUser(anyString(), eq("/queue/notifications"), any(NotificationResponse.class));

        // Verify first send is to user "10"
        ArgumentCaptor<String> userCaptor = ArgumentCaptor.forClass(String.class);
        verify(messagingTemplate, times(2))
                .convertAndSendToUser(userCaptor.capture(), eq("/queue/notifications"), any(NotificationResponse.class));
        assertThat(userCaptor.getAllValues()).containsExactly("10", "20");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /Users/Gustavo.alves/Documents/personal/portfolio/backend && mvn test -pl . -Dtest="NotificationDispatcherTests" -q`
Expected: FAIL — `NotificationDispatcher` class does not exist

- [ ] **Step 3: Implement NotificationDispatcher**

```java
package com.prettyface.app.notification.app;

import com.prettyface.app.multitenancy.ApplicationSchemaExecutor;
import com.prettyface.app.notification.domain.*;
import com.prettyface.app.notification.web.dto.NotificationResponse;
import com.prettyface.app.notification.web.mapper.NotificationMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class NotificationDispatcher {

    private static final Logger logger = LoggerFactory.getLogger(NotificationDispatcher.class);

    private final NotificationService notificationService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ApplicationSchemaExecutor applicationSchemaExecutor;

    public NotificationDispatcher(NotificationService notificationService,
                                   SimpMessagingTemplate messagingTemplate,
                                   ApplicationSchemaExecutor applicationSchemaExecutor) {
        this.notificationService = notificationService;
        this.messagingTemplate = messagingTemplate;
        this.applicationSchemaExecutor = applicationSchemaExecutor;
    }

    public void dispatch(List<Long> recipientIds, String tenantSlug,
                         NotificationType type, NotificationCategory category,
                         String title, String message,
                         Long referenceId, ReferenceType referenceType) {
        for (Long recipientId : recipientIds) {
            try {
                Notification notification = Notification.builder()
                        .recipientId(recipientId)
                        .tenantSlug(tenantSlug)
                        .type(type)
                        .category(category)
                        .title(title)
                        .message(message)
                        .referenceId(referenceId)
                        .referenceType(referenceType)
                        .read(false)
                        .build();

                Notification saved = applicationSchemaExecutor.call(() ->
                        notificationService.save(notification));

                NotificationResponse response = NotificationMapper.toResponse(saved);
                messagingTemplate.convertAndSendToUser(
                        recipientId.toString(),
                        "/queue/notifications",
                        response
                );
                logger.debug("Notification dispatched to user {} : {}", recipientId, type);
            } catch (Exception e) {
                logger.error("Failed to dispatch notification to user {}: {}", recipientId, e.getMessage());
            }
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd /Users/Gustavo.alves/Documents/personal/portfolio/backend && mvn test -pl . -Dtest="NotificationDispatcherTests" -q`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/prettyface/app/notification/app/NotificationDispatcher.java backend/src/test/java/com/prettyface/app/notification/app/NotificationDispatcherTests.java
git commit -m "feat: add NotificationDispatcher with persist + STOMP send"
```

---

### Task 7: Backend — NotificationController (REST endpoints)

**Files:**
- Create: `backend/src/main/java/com/prettyface/app/notification/web/NotificationController.java`

- [ ] **Step 1: Implement NotificationController**

```java
package com.prettyface.app.notification.web;

import com.prettyface.app.auth.UserPrincipal;
import com.prettyface.app.multitenancy.ApplicationSchemaExecutor;
import com.prettyface.app.notification.app.NotificationService;
import com.prettyface.app.notification.web.dto.NotificationResponse;
import com.prettyface.app.notification.web.mapper.NotificationMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final ApplicationSchemaExecutor applicationSchemaExecutor;

    public NotificationController(NotificationService notificationService,
                                   ApplicationSchemaExecutor applicationSchemaExecutor) {
        this.notificationService = notificationService;
        this.applicationSchemaExecutor = applicationSchemaExecutor;
    }

    @GetMapping
    public Page<NotificationResponse> list(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) Boolean read,
            Pageable pageable) {
        return applicationSchemaExecutor.call(() ->
                notificationService.listForRecipient(principal.getId(), read, pageable)
                        .map(NotificationMapper::toResponse));
    }

    @GetMapping("/unread/count")
    public long unreadCount(@AuthenticationPrincipal UserPrincipal principal) {
        return applicationSchemaExecutor.call(() ->
                notificationService.countUnread(principal.getId()));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        applicationSchemaExecutor.run(() ->
                notificationService.markAsRead(id, principal.getId()));
        return ResponseEntity.noContent().build();
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `cd /Users/Gustavo.alves/Documents/personal/portfolio/backend && mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/prettyface/app/notification/web/NotificationController.java
git commit -m "feat: add NotificationController REST endpoints"
```

---

### Task 8: Backend — Wire NotificationDispatcher into CareBookingService

**Files:**
- Modify: `backend/src/main/java/com/prettyface/app/bookings/app/CareBookingService.java:40-60,267-269,288-294`

- [ ] **Step 1: Add NotificationDispatcher to CareBookingService constructor**

In `CareBookingService.java`, add a field (after line 49):

```java
private final com.prettyface.app.notification.app.NotificationDispatcher notificationDispatcher;
```

Add to constructor parameters and assignment (around line 51-59). The constructor already has many params — add `NotificationDispatcher notificationDispatcher` as the last parameter and assign `this.notificationDispatcher = notificationDispatcher;`.

- [ ] **Step 2: Dispatch notification after booking creation**

In `createClientBooking()`, after line 269 (`emailService.sendNewBookingNotificationEmail(...)`), add:

```java
// Real-time notification to PRO + assigned employee
java.util.List<Long> recipients = new java.util.ArrayList<>();
recipients.add(owner.getId());
if (booking.getEmployeeId() != null) {
    employeeRepository.findById(booking.getEmployeeId()).ifPresent(emp ->
            userRepository.findById(emp.getUserId()).ifPresent(empUser ->
                    recipients.add(empUser.getId())));
}
notificationDispatcher.dispatch(
        recipients,
        TenantContext.getCurrentTenant(),
        com.prettyface.app.notification.domain.NotificationType.NEW_BOOKING,
        com.prettyface.app.notification.domain.NotificationCategory.BOOKING,
        "Nouveau rendez-vous",
        client.getName() + " - " + care.getName() + ", " + booking.getAppointmentTime(),
        booking.getId(),
        com.prettyface.app.notification.domain.ReferenceType.BOOKING
);
```

- [ ] **Step 3: Dispatch notification on booking cancellation**

In `cancelBooking()`, after `repo.save(booking)` (line 293), add:

```java
// Real-time notification to PRO + assigned employee
String tenantSlug = TenantContext.getCurrentTenant();
Tenant tenant = tenantRepository.findBySlug(tenantSlug)
        .orElse(null);
if (tenant != null) {
    java.util.List<Long> recipients = new java.util.ArrayList<>();
    recipients.add(tenant.getOwnerId());
    if (booking.getEmployeeId() != null) {
        employeeRepository.findById(booking.getEmployeeId()).ifPresent(emp ->
                userRepository.findById(emp.getUserId()).ifPresent(empUser ->
                        recipients.add(empUser.getId())));
    }
    Care care = booking.getCare();
    User client = booking.getUser();
    notificationDispatcher.dispatch(
            recipients,
            tenantSlug,
            com.prettyface.app.notification.domain.NotificationType.BOOKING_CANCELLED,
            com.prettyface.app.notification.domain.NotificationCategory.BOOKING,
            "Rendez-vous annulé",
            (client != null ? client.getName() : "Client") + " - " + (care != null ? care.getName() : "Soin") + ", " + booking.getAppointmentDate(),
            booking.getId(),
            com.prettyface.app.notification.domain.ReferenceType.BOOKING
    );
}
```

- [ ] **Step 4: Update CareBookingServiceTests to mock NotificationDispatcher**

In `CareBookingServiceTests.java`, add a new mock field after the existing mocks (around line 55):

```java
@Mock private com.prettyface.app.notification.app.NotificationDispatcher notificationDispatcher;
```

This is needed because `@InjectMocks` will try to inject all constructor params.

- [ ] **Step 5: Verify tests pass**

Run: `cd /Users/Gustavo.alves/Documents/personal/portfolio/backend && mvn test -pl . -Dtest="CareBookingServiceTests" -q`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/prettyface/app/bookings/app/CareBookingService.java backend/src/test/java/com/prettyface/app/bookings/app/CareBookingServiceTests.java
git commit -m "feat: wire NotificationDispatcher into booking create and cancel flows"
```

---

### Task 9: Frontend — Install @stomp/stompjs + notification models

**Files:**
- Modify: `frontend/package.json:~39`
- Create: `frontend/src/app/features/notifications/models/notification.model.ts`

- [ ] **Step 1: Install @stomp/stompjs**

Run: `cd /Users/Gustavo.alves/Documents/personal/portfolio/frontend && npm install @stomp/stompjs`

- [ ] **Step 2: Create notification model**

```typescript
export interface NotificationResponse {
  id: number;
  type: string;
  category: string;
  title: string;
  message: string;
  referenceId: number;
  referenceType: string;
  read: boolean;
  tenantSlug: string;
  createdAt: string;
}
```

- [ ] **Step 3: Commit**

```bash
git add frontend/package.json frontend/package-lock.json frontend/src/app/features/notifications/models/notification.model.ts
git commit -m "feat: add @stomp/stompjs dependency and notification model"
```

---

### Task 10: Frontend — NotificationsService (REST)

**Files:**
- Create: `frontend/src/app/features/notifications/services/notifications.service.ts`

- [ ] **Step 1: Create NotificationsService**

```typescript
import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../../../core/config/api-base-url.token';
import { NotificationResponse } from '../models/notification.model';

interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
}

@Injectable({ providedIn: 'root' })
export class NotificationsService {
  private readonly http = inject(HttpClient);
  private readonly apiBaseUrl = inject(API_BASE_URL);

  list(read?: boolean, page = 0, size = 20): Observable<Page<NotificationResponse>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (read !== undefined) {
      params = params.set('read', read);
    }
    return this.http.get<Page<NotificationResponse>>(`${this.apiBaseUrl}/api/notifications`, { params });
  }

  unreadCount(): Observable<number> {
    return this.http.get<number>(`${this.apiBaseUrl}/api/notifications/unread/count`);
  }

  markAsRead(id: number): Observable<void> {
    return this.http.patch<void>(`${this.apiBaseUrl}/api/notifications/${id}/read`, {});
  }
}
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/app/features/notifications/services/notifications.service.ts
git commit -m "feat: add NotificationsService for REST calls"
```

---

### Task 11: Frontend — WebSocketService (STOMP connection)

**Files:**
- Create: `frontend/src/app/features/notifications/services/websocket.service.ts`

- [ ] **Step 1: Create WebSocketService**

```typescript
import { inject, Injectable, PLATFORM_ID } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { Client, IMessage } from '@stomp/stompjs';
import { Subject } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import { API_BASE_URL } from '../../../core/config/api-base-url.token';
import { NotificationResponse } from '../models/notification.model';

@Injectable({ providedIn: 'root' })
export class WebSocketService {
  private readonly authService = inject(AuthService);
  private readonly apiBaseUrl = inject(API_BASE_URL);
  private readonly platformId = inject(PLATFORM_ID);
  private client: Client | null = null;

  readonly notification$ = new Subject<NotificationResponse>();

  connect(): void {
    if (!isPlatformBrowser(this.platformId)) return;

    const token = this.authService.getToken();
    if (!token) return;

    // Derive WS URL from API base URL
    const wsUrl = this.apiBaseUrl.replace(/^http/, 'ws') + '/ws';

    this.client = new Client({
      brokerURL: wsUrl,
      connectHeaders: {
        Authorization: `Bearer ${token}`,
      },
      reconnectDelay: 1000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      onConnect: () => {
        this.client!.subscribe('/user/queue/notifications', (message: IMessage) => {
          const notification: NotificationResponse = JSON.parse(message.body);
          this.notification$.next(notification);
        });
      },
      onStompError: (frame) => {
        console.error('STOMP error:', frame.headers['message']);
      },
      onWebSocketClose: () => {
        // Reconnection handled automatically by stompjs with exponential backoff
      },
    });

    this.client.activate();
  }

  disconnect(): void {
    if (this.client?.active) {
      this.client.deactivate();
      this.client = null;
    }
  }

  isConnected(): boolean {
    return this.client?.active ?? false;
  }
}
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/app/features/notifications/services/websocket.service.ts
git commit -m "feat: add WebSocketService for STOMP connection"
```

---

### Task 12: Frontend — NotificationsStore (global SignalStore)

**Files:**
- Create: `frontend/src/app/features/notifications/store/notifications.store.ts`
- Modify: `frontend/src/app/app.config.ts:45`

- [ ] **Step 1: Create NotificationsStore**

```typescript
import { computed, effect, inject } from '@angular/core';
import { patchState, signalStore, withComputed, withHooks, withMethods, withState } from '@ngrx/signals';
import { rxMethod } from '@ngrx/signals/rxjs-interop';
import { pipe, switchMap, tap, EMPTY, catchError } from 'rxjs';
import { NotificationsService } from '../services/notifications.service';
import { WebSocketService } from '../services/websocket.service';
import { NotificationResponse } from '../models/notification.model';
import { AuthService } from '../../../core/auth/auth.service';

type NotificationsState = {
  notifications: NotificationResponse[];
  unreadCount: number;
  latestNotification: NotificationResponse | null;
};

export const NotificationsStore = signalStore(
  withState<NotificationsState>({
    notifications: [],
    unreadCount: 0,
    latestNotification: null,
  }),
  withComputed((store) => ({
    hasUnread: computed(() => store.unreadCount() > 0),
    badgeLabel: computed(() => {
      const count = store.unreadCount();
      if (count === 0) return '';
      if (count > 99) return '99+';
      return count.toString();
    }),
  })),
  withMethods((store,
    notificationsService = inject(NotificationsService),
    webSocketService = inject(WebSocketService),
    authService = inject(AuthService),
  ) => ({
    loadUnreadCount: rxMethod<void>(
      pipe(
        switchMap(() => notificationsService.unreadCount()),
        tap((count) => patchState(store, { unreadCount: count })),
        catchError(() => EMPTY)
      )
    ),
    loadNotifications: rxMethod<void>(
      pipe(
        switchMap(() => notificationsService.list(undefined, 0, 50)),
        tap((page) => patchState(store, { notifications: page.content })),
        catchError(() => EMPTY)
      )
    ),
    markAsRead: rxMethod<number>(
      pipe(
        switchMap((id) =>
          notificationsService.markAsRead(id).pipe(
            tap(() =>
              patchState(store, {
                notifications: store.notifications().map((n) =>
                  n.id === id ? { ...n, read: true } : n
                ),
                unreadCount: Math.max(0, store.unreadCount() - 1),
              })
            ),
            catchError(() => EMPTY)
          )
        )
      )
    ),
    connectWebSocket(): void {
      webSocketService.connect();
      webSocketService.notification$.subscribe((notification) => {
        patchState(store, {
          notifications: [notification, ...store.notifications()],
          unreadCount: store.unreadCount() + 1,
          latestNotification: notification,
        });
        // Clear latest after 4s (toast duration + buffer)
        setTimeout(() => {
          if (store.latestNotification()?.id === notification.id) {
            patchState(store, { latestNotification: null });
          }
        }, 4000);
      });
    },
    disconnectWebSocket(): void {
      webSocketService.disconnect();
    },
    clearLatestNotification(): void {
      patchState(store, { latestNotification: null });
    },
  })),
  withHooks((store, authService = inject(AuthService)) => ({
    onInit() {
      // Only load if authenticated
      effect(() => {
        if (authService.isAuthenticated()) {
          store.loadUnreadCount();
          store.connectWebSocket();
        } else {
          store.disconnectWebSocket();
          patchState(store, { notifications: [], unreadCount: 0, latestNotification: null });
        }
      });
    },
  }))
);
```

- [ ] **Step 2: Provide NotificationsStore globally in app.config.ts**

In `frontend/src/app/app.config.ts`, add the import at the top:

```typescript
import { NotificationsStore } from './features/notifications/store/notifications.store';
```

Then add to the `providers` array (after `provideBrowserGlobalErrorListeners()` at line 46):

```typescript
NotificationsStore,
```

- [ ] **Step 3: Verify the frontend compiles**

Run: `cd /Users/Gustavo.alves/Documents/personal/portfolio/frontend && npx ng build --configuration=development 2>&1 | tail -5`
Expected: Build succeeds

- [ ] **Step 4: Commit**

```bash
git add frontend/src/app/features/notifications/store/notifications.store.ts frontend/src/app/app.config.ts
git commit -m "feat: add global NotificationsStore with WebSocket integration"
```

---

### Task 13: Frontend — Notification toast component

**Files:**
- Create: `frontend/src/app/features/notifications/components/notification-toast/notification-toast.component.ts`
- Modify: `frontend/src/app/app.ts:10`
- Modify: `frontend/src/app/app.html:2`

- [ ] **Step 1: Create NotificationToastComponent**

```typescript
import { Component, computed, inject } from '@angular/core';
import { NotificationsStore } from '../../store/notifications.store';
import { TranslocoPipe } from '@jsverse/transloco';

@Component({
  selector: 'app-notification-toast',
  standalone: true,
  imports: [TranslocoPipe],
  template: `
    @if (notification(); as notif) {
      <div class="toast" (click)="dismiss()">
        <div class="toast-content">
          <div class="toast-title">{{ notif.title }}</div>
          <div class="toast-message">{{ notif.message }}</div>
        </div>
        <button type="button" class="toast-close" (click)="dismiss(); $event.stopPropagation()" aria-label="Fermer">
          &times;
        </button>
      </div>
    }
  `,
  styles: `
    :host {
      position: fixed;
      top: 0;
      left: 0;
      right: 0;
      z-index: 1000;
      pointer-events: none;
    }

    .toast {
      pointer-events: auto;
      margin: 12px;
      padding: 14px 20px;
      background: white;
      border-bottom: 2px solid #fda4af;
      border-radius: 12px;
      display: flex;
      align-items: center;
      gap: 14px;
      box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
      animation: slideDown 250ms ease-out;
      cursor: pointer;
    }

    .toast-content {
      flex: 1;
    }

    .toast-title {
      font-weight: 600;
      color: #1a1a2e;
      font-size: 14px;
    }

    .toast-message {
      color: #6b7280;
      font-size: 13px;
      margin-top: 2px;
    }

    .toast-close {
      background: none;
      border: none;
      color: #9ca3af;
      font-size: 20px;
      cursor: pointer;
      padding: 4px 8px;
      line-height: 1;
    }

    @keyframes slideDown {
      from {
        transform: translateY(-100%);
        opacity: 0;
      }
      to {
        transform: translateY(0);
        opacity: 1;
      }
    }
  `,
})
export class NotificationToastComponent {
  private readonly store = inject(NotificationsStore);
  readonly notification = this.store.latestNotification;

  dismiss(): void {
    this.store.clearLatestNotification();
  }
}
```

- [ ] **Step 2: Add toast to app.ts imports**

In `frontend/src/app/app.ts`, add import at top:

```typescript
import { NotificationToastComponent } from './features/notifications/components/notification-toast/notification-toast.component';
```

Update the `imports` array at line 10:

```typescript
imports: [RouterOutlet, Header, Footer, BottomNavComponent, NotificationToastComponent],
```

- [ ] **Step 3: Add toast to app.html**

In `frontend/src/app/app.html`, add after `<app-header>` (line 2):

```html
<app-notification-toast></app-notification-toast>
```

- [ ] **Step 4: Verify compilation**

Run: `cd /Users/Gustavo.alves/Documents/personal/portfolio/frontend && npx ng build --configuration=development 2>&1 | tail -5`
Expected: Build succeeds

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/features/notifications/components/notification-toast/notification-toast.component.ts frontend/src/app/app.ts frontend/src/app/app.html
git commit -m "feat: add notification toast component"
```

---

### Task 14: Frontend — Bell icon in header with badge

**Files:**
- Modify: `frontend/src/app/shared/layout/header/header.ts:1-20`
- Modify: `frontend/src/app/shared/layout/header/header.html:54`
- Modify: `frontend/src/app/shared/layout/header/header.scss`

- [ ] **Step 1: Inject NotificationsStore in header.ts**

In `frontend/src/app/shared/layout/header/header.ts`, add import:

```typescript
import { NotificationsStore } from '../../../features/notifications/store/notifications.store';
```

In the `Header` class, add:

```typescript
protected readonly notificationsStore = inject(NotificationsStore);
```

- [ ] **Step 2: Add bell icon to header.html**

In `frontend/src/app/shared/layout/header/header.html`, at line 54, inside the right actions div, add **before** the existing auth check (`@if (authService.isAuthenticated())`):

```html
@if (authService.isAuthenticated()) {
  <a
    routerLink="/notifications"
    aria-label="Notifications"
    class="notification-bell p-2 rounded-full hover:bg-neutral-100 focus:outline-none transition-colors duration-150 relative">
    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" class="w-6 h-6">
      <path stroke-linecap="round" stroke-linejoin="round" d="M14.857 17.082a23.848 23.848 0 005.454-1.31A8.967 8.967 0 0118 9.75v-.7V9A6 6 0 006 9v.75a8.967 8.967 0 01-2.312 6.022c1.733.64 3.56 1.085 5.455 1.31m5.714 0a24.255 24.255 0 01-5.714 0m5.714 0a3 3 0 11-5.714 0"/>
    </svg>
    @if (notificationsStore.hasUnread()) {
      <span class="notification-badge">
        @if (notificationsStore.unreadCount() > 1) {
          {{ notificationsStore.badgeLabel() }}
        }
      </span>
    }
  </a>
}
```

Note: This needs to be placed before the existing `@if (authService.isAuthenticated())` block that contains the user menu button (line 55), so both appear side by side. The existing `@if` at line 55 stays as-is.

- [ ] **Step 3: Add badge styles to header.scss**

Append to `frontend/src/app/shared/layout/header/header.scss`:

```scss
.notification-bell {
  position: relative;
}

.notification-badge {
  position: absolute;
  top: 4px;
  right: 4px;
  min-width: 12px;
  height: 12px;
  background: #e11d48;
  border-radius: 9px;
  border: 2px solid white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 9px;
  font-weight: 700;
  color: white;
  padding: 0 3px;
  box-shadow: 0 1px 3px rgba(225, 29, 72, 0.4);
  animation: badgeAppear 200ms ease-out;

  &:empty {
    min-width: 12px;
    padding: 0;
  }
}

@keyframes badgeAppear {
  from {
    transform: scale(0);
  }
  to {
    transform: scale(1);
  }
}
```

- [ ] **Step 4: Verify compilation**

Run: `cd /Users/Gustavo.alves/Documents/personal/portfolio/frontend && npx ng build --configuration=development 2>&1 | tail -5`
Expected: Build succeeds

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/shared/layout/header/header.ts frontend/src/app/shared/layout/header/header.html frontend/src/app/shared/layout/header/header.scss
git commit -m "feat: add notification bell icon with badge in header"
```

---

### Task 15: Frontend — Badge on bottom-nav Bookings icon

**Files:**
- Modify: `frontend/src/app/shared/layout/bottom-nav/bottom-nav.component.ts:1-40,123-209`

- [ ] **Step 1: Import NotificationsStore and update template**

In `frontend/src/app/shared/layout/bottom-nav/bottom-nav.component.ts`:

Add import at the top:

```typescript
import { NotificationsStore } from '../../../features/notifications/store/notifications.store';
```

In the class body (after line 126), add:

```typescript
protected readonly notificationsStore = inject(NotificationsStore);
```

Update the template. Replace the `@else` block for regular tabs (lines 31-35) with:

```html
<a [routerLink]="tab.path" class="tab" routerLinkActive="active"
   [routerLinkActiveOptions]="{exact: tab.path === '/'}">
  <span class="tab-icon-wrapper">
    <mat-icon>{{ tab.icon }}</mat-icon>
    @if (tab.id.endsWith('-bookings') && notificationsStore.hasUnread()) {
      <span class="tab-badge">
        @if (notificationsStore.unreadCount() > 1) {
          {{ notificationsStore.badgeLabel() }}
        }
      </span>
    }
  </span>
  <span class="tab-label">{{ tab.labelKey | transloco }}</span>
</a>
```

- [ ] **Step 2: Add badge styles**

In the same component's `styles` section, add after the `.camera-btn` styles (before the closing backtick):

```css
.tab-icon-wrapper {
  position: relative;
  display: inline-flex;
}

.tab-badge {
  position: absolute;
  top: -4px;
  right: -6px;
  min-width: 12px;
  height: 12px;
  background: #e11d48;
  border-radius: 9px;
  border: 2px solid white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 9px;
  font-weight: 700;
  color: white;
  padding: 0 3px;
  box-shadow: 0 1px 3px rgba(225, 29, 72, 0.4);
}

.tab-badge:empty {
  min-width: 12px;
  padding: 0;
}
```

- [ ] **Step 3: Verify compilation**

Run: `cd /Users/Gustavo.alves/Documents/personal/portfolio/frontend && npx ng build --configuration=development 2>&1 | tail -5`
Expected: Build succeeds

- [ ] **Step 4: Commit**

```bash
git add frontend/src/app/shared/layout/bottom-nav/bottom-nav.component.ts
git commit -m "feat: add notification badge on Bookings tab in bottom nav"
```

---

### Task 16: Frontend — Notifications page + routing

**Files:**
- Create: `frontend/src/app/pages/notifications/notifications.component.ts`
- Create: `frontend/src/app/pages/notifications/notifications.component.html`
- Create: `frontend/src/app/pages/notifications/notifications.component.scss`
- Modify: `frontend/src/app/app.routes.ts:~155`

- [ ] **Step 1: Create notifications page component**

`frontend/src/app/pages/notifications/notifications.component.ts`:

```typescript
import { Component, inject } from '@angular/core';
import { Router } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { NotificationsStore } from '../../features/notifications/store/notifications.store';
import { NotificationResponse } from '../../features/notifications/models/notification.model';
import { DatePipe } from '@angular/common';

@Component({
  selector: 'app-notifications-page',
  standalone: true,
  imports: [TranslocoPipe, DatePipe],
  templateUrl: './notifications.component.html',
  styleUrl: './notifications.component.scss',
  providers: [],
})
export class NotificationsComponent {
  protected readonly store = inject(NotificationsStore);
  private readonly router = inject(Router);

  constructor() {
    // Load full notifications list when entering page
    this.store.loadNotifications();
  }

  onNotificationClick(notification: NotificationResponse): void {
    if (!notification.read) {
      this.store.markAsRead(notification.id);
    }
    this.navigateToReference(notification);
  }

  private navigateToReference(notification: NotificationResponse): void {
    switch (notification.referenceType) {
      case 'BOOKING':
        // Determine route based on user role (PRO vs EMPLOYEE)
        // For now navigate to pro bookings; the route guard will handle redirection
        this.router.navigate(['/pro/bookings'], {
          queryParams: { highlight: notification.referenceId },
        });
        break;
      default:
        break;
    }
  }
}
```

- [ ] **Step 2: Create template**

`frontend/src/app/pages/notifications/notifications.component.html`:

```html
<div class="notifications-page">
  <h1 class="page-title">{{ 'notifications.title' | transloco }}</h1>

  @if (store.notifications().length === 0) {
    <div class="empty-state">
      <p>{{ 'notifications.empty' | transloco }}</p>
    </div>
  } @else {
    <div class="notification-list">
      @for (notif of store.notifications(); track notif.id) {
        <div
          class="notification-item"
          [class.unread]="!notif.read"
          (click)="onNotificationClick(notif)">
          <div class="notification-dot" [class.visible]="!notif.read"></div>
          <div class="notification-content">
            <div class="notification-title">{{ notif.title }}</div>
            <div class="notification-message">{{ notif.message }}</div>
            <div class="notification-time">{{ notif.createdAt | date:'short' }}</div>
          </div>
        </div>
      }
    </div>
  }
</div>
```

- [ ] **Step 3: Create styles**

`frontend/src/app/pages/notifications/notifications.component.scss`:

```scss
.notifications-page {
  max-width: 600px;
  margin: 0 auto;
}

.page-title {
  font-size: 1.5rem;
  font-weight: 600;
  margin-bottom: 1.5rem;
}

.empty-state {
  text-align: center;
  padding: 3rem 1rem;
  color: #9ca3af;
}

.notification-list {
  display: flex;
  flex-direction: column;
}

.notification-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 14px 16px;
  border-bottom: 1px solid #f3f4f6;
  cursor: pointer;
  transition: background-color 150ms;

  &:hover {
    background: #fdf2f8;
  }

  &.unread {
    background: #fef7f9;
  }
}

.notification-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: transparent;
  margin-top: 6px;
  flex-shrink: 0;

  &.visible {
    background: #e11d48;
  }
}

.notification-content {
  flex: 1;
  min-width: 0;
}

.notification-title {
  font-weight: 600;
  font-size: 14px;
  color: #1a1a2e;
}

.notification-message {
  font-size: 13px;
  color: #6b7280;
  margin-top: 2px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.notification-time {
  font-size: 11px;
  color: #9ca3af;
  margin-top: 4px;
}
```

- [ ] **Step 4: Add route to app.routes.ts**

In `frontend/src/app/app.routes.ts`, before the `{ path: 'users' ...}` route (around line 157), add:

```typescript
{
  path: 'notifications',
  canActivate: [authGuard],
  loadComponent: () => import('./pages/notifications/notifications.component').then(m => m.NotificationsComponent),
},
```

- [ ] **Step 5: Verify compilation**

Run: `cd /Users/Gustavo.alves/Documents/personal/portfolio/frontend && npx ng build --configuration=development 2>&1 | tail -5`
Expected: Build succeeds

- [ ] **Step 6: Commit**

```bash
git add frontend/src/app/pages/notifications/ frontend/src/app/app.routes.ts
git commit -m "feat: add notifications page with routing"
```

---

### Task 17: Frontend — i18n translations

**Files:**
- Modify: `frontend/public/i18n/fr.json`
- Modify: `frontend/public/i18n/en.json`

- [ ] **Step 1: Add French translations**

In `frontend/public/i18n/fr.json`, add a `notifications` section:

```json
"notifications": {
  "title": "Notifications",
  "empty": "Aucune notification pour le moment",
  "newBooking": "Nouveau rendez-vous",
  "bookingCancelled": "Rendez-vous annulé",
  "markAsRead": "Marquer comme lu"
}
```

- [ ] **Step 2: Add English translations**

In `frontend/public/i18n/en.json`, add the same section:

```json
"notifications": {
  "title": "Notifications",
  "empty": "No notifications yet",
  "newBooking": "New appointment",
  "bookingCancelled": "Appointment cancelled",
  "markAsRead": "Mark as read"
}
```

- [ ] **Step 3: Commit**

```bash
git add frontend/public/i18n/fr.json frontend/public/i18n/en.json
git commit -m "feat: add notification i18n translations (fr + en)"
```

---

### Task 18: Frontend — Highlight booking after navigation from notification

**Files:**
- Modify: `frontend/src/app/pages/pro/pro-bookings.component.ts` (or equivalent bookings page that displays the list)

This task adds support for the `?highlight=123` query param. When present, the page scrolls to the booking item and applies a 2-second fade highlight.

- [ ] **Step 1: Read the current pro-bookings component to understand its structure**

Run: Read `frontend/src/app/pages/pro/pro-bookings.component.ts` to see how bookings are rendered and what template is used.

- [ ] **Step 2: Add highlight logic**

In the bookings page component, inject `ActivatedRoute` and add:

```typescript
private readonly route = inject(ActivatedRoute);
protected readonly highlightId = signal<number | null>(null);

constructor() {
  // ... existing constructor logic

  // Check for highlight query param
  this.route.queryParams.subscribe((params) => {
    const id = params['highlight'];
    if (id) {
      this.highlightId.set(+id);
      // Scroll to element after render
      setTimeout(() => {
        const el = document.getElementById('booking-' + id);
        if (el) {
          el.scrollIntoView({ behavior: 'smooth', block: 'center' });
          el.classList.add('highlight-fade');
          setTimeout(() => el.classList.remove('highlight-fade'), 2000);
        }
      }, 500);
    }
  });
}
```

In the template, add `[id]="'booking-' + booking.id"` to each booking row/card element.

In the styles, add:

```scss
.highlight-fade {
  background-color: #fdf2f8;
  transition: background-color 2s ease-out;
}

.highlight-fade:not(.highlight-fade) {
  background-color: transparent;
}
```

Note: The exact implementation depends on the current structure of the bookings page. The engineer should read the component first (step 1), then apply the pattern to match.

- [ ] **Step 3: Verify compilation**

Run: `cd /Users/Gustavo.alves/Documents/personal/portfolio/frontend && npx ng build --configuration=development 2>&1 | tail -5`
Expected: Build succeeds

- [ ] **Step 4: Commit**

```bash
git add frontend/src/app/pages/pro/
git commit -m "feat: add highlight animation on booking after notification navigation"
```

---

### Task 19: Full integration verification

- [ ] **Step 1: Run all backend tests**

Run: `cd /Users/Gustavo.alves/Documents/personal/portfolio/backend && mvn test -q`
Expected: All tests pass

- [ ] **Step 2: Run frontend build**

Run: `cd /Users/Gustavo.alves/Documents/personal/portfolio/frontend && npx ng build --configuration=development 2>&1 | tail -10`
Expected: Build succeeds with no errors

- [ ] **Step 3: Run frontend tests**

Run: `cd /Users/Gustavo.alves/Documents/personal/portfolio/frontend && npm test -- --watch=false 2>&1 | tail -20`
Expected: All tests pass

- [ ] **Step 4: Final commit if any remaining changes**

```bash
git status
# If any unstaged files remain, add and commit
```
