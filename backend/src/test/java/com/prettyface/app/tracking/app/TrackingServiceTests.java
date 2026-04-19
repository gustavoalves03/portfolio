package com.prettyface.app.tracking.app;

import com.prettyface.app.multitenancy.ApplicationSchemaExecutor;
import com.prettyface.app.tracking.domain.VisitRecord;
import com.prettyface.app.tracking.repo.ClientProfileRepository;
import com.prettyface.app.tracking.repo.ClientReminderRepository;
import com.prettyface.app.tracking.repo.VisitPhotoRepository;
import com.prettyface.app.tracking.repo.VisitRecordRepository;
import com.prettyface.app.tracking.web.dto.VisitRecordResponse;
import com.prettyface.app.users.repo.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Lot2 Sec1 — Scenario #103: Client cannot rate another client's visit.
 *
 * Pattern A (WARN): {@link TrackingService#rateVisit} performs NO
 * client-ownership check. It looks up the VisitRecord by id and applies the
 * satisfaction score + comment with no verification that the caller owns the
 * underlying ClientProfile.
 */
@ExtendWith(MockitoExtension.class)
class TrackingServiceTests {

    @Mock private ClientProfileRepository profileRepo;
    @Mock private VisitRecordRepository visitRepo;
    @Mock private VisitPhotoRepository photoRepo;
    @Mock private ClientReminderRepository reminderRepo;
    @Mock private UserRepository userRepository;
    @Mock private ApplicationSchemaExecutor applicationSchemaExecutor;

    @InjectMocks
    private TrackingService service;

    @Test
    @DisplayName("rateVisit — valid score (1–5) updates the visit")
    void rateVisit_validScore_updates() {
        VisitRecord visit = new VisitRecord();
        visit.setId(10L);
        visit.setClientProfileId(500L);
        when(visitRepo.findById(10L)).thenReturn(Optional.of(visit));
        when(visitRepo.save(any(VisitRecord.class))).thenAnswer(inv -> inv.getArgument(0));
        when(photoRepo.findByVisitRecordIdOrderByImageOrderAsc(10L)).thenReturn(List.of());

        VisitRecordResponse result = service.rateVisit(10L, 5, "Excellent");

        assertThat(result.satisfactionScore()).isEqualTo(5);
        assertThat(result.satisfactionComment()).isEqualTo("Excellent");
    }

    @Test
    @DisplayName("rateVisit — score 0 rejected")
    void rateVisit_scoreTooLow_rejected() {
        assertThatThrownBy(() -> service.rateVisit(10L, 0, "bad"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 1 and 5");
    }

    @Test
    @DisplayName("rateVisit — score 6 rejected")
    void rateVisit_scoreTooHigh_rejected() {
        assertThatThrownBy(() -> service.rateVisit(10L, 6, "?"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 1 and 5");
    }

    // ══════════════════════════════════════════════════════════════
    // ── Lot2 Sec1: Cross-client IDOR on visit rating ──
    // ══════════════════════════════════════════════════════════════
    // NOTE-SEC: VisitRecord is keyed by clientProfileId. TrackingService.rateVisit
    // takes a visitRecordId and applies the rating without verifying that the
    // caller owns the ClientProfile behind that visit. A client-facing rating
    // endpoint that forwards visitRecordId from the request body would let any
    // authenticated client rate any other client's visit.

    @Test
    @DisplayName("Lot2#103: rateVisit_WARN_doesNotCheckClientOwnership (FINDING)")
    void rateVisit_WARN_doesNotCheckClientOwnership() {
        // TODO-SEC: TrackingService.rateVisit performs no check that the caller
        // owns the ClientProfile linked to this VisitRecord. Cross-client
        // rating is possible if the caller can guess a visitRecordId.
        VisitRecord victimVisit = new VisitRecord();
        victimVisit.setId(999L);
        victimVisit.setClientProfileId(777L); // profile of another client
        when(visitRepo.findById(999L)).thenReturn(Optional.of(victimVisit));
        when(visitRepo.save(any(VisitRecord.class))).thenAnswer(inv -> inv.getArgument(0));
        when(photoRepo.findByVisitRecordIdOrderByImageOrderAsc(999L)).thenReturn(List.of());

        // Service happily applies the rating without any ownership check.
        VisitRecordResponse result = service.rateVisit(999L, 1, "cross-client drive-by rating");

        assertThat(result.satisfactionScore()).isEqualTo(1);
        assertThat(result.satisfactionComment()).isEqualTo("cross-client drive-by rating");
        // No ClientProfile / caller cross-check — documents the gap.
    }
}
