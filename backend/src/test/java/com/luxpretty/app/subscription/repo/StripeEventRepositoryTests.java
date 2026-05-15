package com.luxpretty.app.subscription.repo;

import com.luxpretty.app.subscription.domain.StripeEventProcessed;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase
class StripeEventRepositoryTests {

    @Autowired
    StripeEventRepository repo;

    @Test
    void save_persists_event_processed_record() {
        StripeEventProcessed event = StripeEventProcessed.builder()
            .eventId("evt_TEST_123")
            .eventType("customer.subscription.created")
            .build();

        repo.saveAndFlush(event);

        StripeEventProcessed reloaded = repo.findById("evt_TEST_123").orElseThrow();
        assertThat(reloaded.getEventType()).isEqualTo("customer.subscription.created");
        assertThat(reloaded.getProcessedAt()).isNotNull();
    }

    @Test
    void save_duplicateEventId_throwsDataIntegrityViolation() {
        StripeEventProcessed event = StripeEventProcessed.builder()
            .eventId("evt_TEST_DUPLICATE")
            .eventType("invoice.paid")
            .build();
        repo.saveAndFlush(event);

        StripeEventProcessed duplicate = StripeEventProcessed.builder()
            .eventId("evt_TEST_DUPLICATE")
            .eventType("invoice.paid")
            .build();

        assertThatThrownBy(() -> repo.saveAndFlush(duplicate))
            .isInstanceOf(DataIntegrityViolationException.class);
    }
}
