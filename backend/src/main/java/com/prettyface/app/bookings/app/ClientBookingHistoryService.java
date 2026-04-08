package com.prettyface.app.bookings.app;

import com.prettyface.app.bookings.domain.ClientBookingHistory;
import com.prettyface.app.bookings.repo.ClientBookingHistoryRepository;
import com.prettyface.app.bookings.web.dto.ClientBookingHistoryResponse;
import com.prettyface.app.bookings.web.dto.ClientBookingResponse;
import com.prettyface.app.multitenancy.ApplicationSchemaExecutor;
import com.prettyface.app.users.domain.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Service
public class ClientBookingHistoryService {

    private static final Logger logger = LoggerFactory.getLogger(ClientBookingHistoryService.class);

    private final ClientBookingHistoryRepository repo;
    private final ApplicationSchemaExecutor applicationSchemaExecutor;

    public ClientBookingHistoryService(ClientBookingHistoryRepository repo,
                                       ApplicationSchemaExecutor applicationSchemaExecutor) {
        this.repo = repo;
        this.applicationSchemaExecutor = applicationSchemaExecutor;
    }

    @Transactional
    public void createMirror(User client, ClientBookingResponse bookingResult,
                              String tenantSlug, String salonName) {
        try {
            ClientBookingHistory history = new ClientBookingHistory();
            history.setUserId(client.getId());
            history.setTenantSlug(tenantSlug);
            history.setSalonName(salonName);
            history.setBookingId(bookingResult.bookingId());
            history.setCareName(bookingResult.careName());
            history.setCarePrice(bookingResult.carePrice());
            history.setCareDuration(bookingResult.careDuration());
            history.setAppointmentDate(LocalDate.parse(bookingResult.appointmentDate()));
            history.setAppointmentTime(LocalTime.parse(bookingResult.appointmentTime()));
            history.setStatus(bookingResult.status());
            applicationSchemaExecutor.run(() -> repo.save(history));
        } catch (Exception e) {
            logger.error("Failed to create booking mirror for user {} booking {}: {}",
                    client.getId(), bookingResult.bookingId(), e.getMessage());
        }
    }

    @Transactional
    public void updateMirrorStatus(String tenantSlug, Long bookingId, String newStatus) {
        applicationSchemaExecutor.run(() -> repo.findByTenantSlugAndBookingId(tenantSlug, bookingId).ifPresent(history -> {
            history.setStatus(newStatus);
            repo.save(history);
        }));
    }

    @Transactional(readOnly = true)
    public List<ClientBookingHistoryResponse> getUpcoming(Long userId) {
        List<ClientBookingHistory> bookings = applicationSchemaExecutor.call(() ->
                repo.findByUserIdAndStatusAndAppointmentDateGreaterThanEqualOrderByAppointmentDateAscAppointmentTimeAsc(
                        userId, "CONFIRMED", LocalDate.now()
                )
        );
        return bookings.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<ClientBookingHistoryResponse> getPast(Long userId) {
        List<ClientBookingHistory> bookings = applicationSchemaExecutor.call(() ->
                repo.findByUserIdAndAppointmentDateBeforeOrderByAppointmentDateDescAppointmentTimeDesc(
                        userId, LocalDate.now()
                )
        );
        return bookings.stream().map(this::toResponse).toList();
    }

    private ClientBookingHistoryResponse toResponse(ClientBookingHistory h) {
        return new ClientBookingHistoryResponse(
                h.getId(),
                h.getBookingId(),
                h.getTenantSlug(),
                h.getSalonName(),
                h.getCareName(),
                h.getCarePrice(),
                h.getCareDuration(),
                h.getAppointmentDate().toString(),
                h.getAppointmentTime().toString(),
                h.getStatus(),
                h.getCreatedAt().toString()
        );
    }
}
