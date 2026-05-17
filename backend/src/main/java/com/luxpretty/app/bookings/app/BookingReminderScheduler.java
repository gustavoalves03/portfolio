package com.luxpretty.app.bookings.app;

import com.luxpretty.app.bookings.domain.CareBooking;
import com.luxpretty.app.bookings.repo.CareBookingRepository;
import com.luxpretty.app.care.domain.Care;
import com.luxpretty.app.care.repo.CareRepository;
import com.luxpretty.app.mail.app.MailOutboxService;
import com.luxpretty.app.mail.domain.MailTemplate;
import com.luxpretty.app.mail.vars.BookingReminderVars;
import com.luxpretty.app.multitenancy.TenantContext;
import com.luxpretty.app.tenant.domain.Tenant;
import com.luxpretty.app.tenant.repo.TenantRepository;
import com.luxpretty.app.users.domain.User;
import com.luxpretty.app.users.repo.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Hourly scheduler that queues a J-1 reminder mail for every CONFIRMED
 * booking happening tomorrow. Idempotent (re-runs are safe) thanks to
 * {@code reminder_sent_at} being marked the first time we touch a row.
 *
 * <p>Anti-spam: when a booking was confirmed less than 2 hours ago, we skip
 * the reminder mail but still set {@code reminderSentAt} — the confirmation
 * email is fresh enough; sending a J-1 right after would feel like spam.
 *
 * <p>Multi-tenant: iterates over every tenant via {@link TenantContext}, the
 * same pattern used by {@code BirthdayScheduler}. Failures in one tenant do
 * not abort the loop.
 */
@Component
public class BookingReminderScheduler {

    private static final Logger logger = LoggerFactory.getLogger(BookingReminderScheduler.class);
    private static final Duration ANTI_SPAM_WINDOW = Duration.ofHours(2);

    private final TenantRepository tenantRepository;
    private final CareBookingRepository bookingRepo;
    private final UserRepository userRepository;
    private final CareRepository careRepository;
    private final MailOutboxService mailOutbox;
    private final String frontendBaseUrl;

    public BookingReminderScheduler(TenantRepository tenantRepository,
                                    CareBookingRepository bookingRepo,
                                    UserRepository userRepository,
                                    CareRepository careRepository,
                                    MailOutboxService mailOutbox,
                                    @Value("${app.frontend.base-url:http://localhost:4200}")
                                    String frontendBaseUrl) {
        this.tenantRepository = tenantRepository;
        this.bookingRepo = bookingRepo;
        this.userRepository = userRepository;
        this.careRepository = careRepository;
        this.mailOutbox = mailOutbox;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    /** Hourly, at HH:00. */
    @Scheduled(cron = "0 0 * * * *")
    public void sendReminders() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        List<Tenant> tenants = tenantRepository.findAll();

        for (Tenant tenant : tenants) {
            try {
                TenantContext.setCurrentTenant(tenant.getSlug());
                processTenantReminders(tenant, tomorrow);
            } catch (Exception e) {
                logger.error("J-1 reminder loop failed for tenant {}: {}",
                        tenant.getSlug(), e.getMessage(), e);
            } finally {
                TenantContext.clear();
            }
        }
    }

    private void processTenantReminders(Tenant tenant, LocalDate targetDate) {
        List<CareBooking> due = bookingRepo.findRemindersDueOnDate(targetDate);
        if (due.isEmpty()) {
            return;
        }

        Instant now = Instant.now();
        for (CareBooking booking : due) {
            try {
                processSingleBooking(booking, tenant, now);
            } catch (Exception e) {
                logger.warn("Failed to process reminder for booking {} (tenant {}): {}",
                        booking.getId(), tenant.getSlug(), e.getMessage());
                // Mark as processed to avoid re-checking forever on a poison row.
                booking.setReminderSentAt(now);
                try {
                    bookingRepo.save(booking);
                } catch (Exception ignored) {
                    // best-effort
                }
            }
        }
    }

    private void processSingleBooking(CareBooking booking, Tenant tenant, Instant now) {
        // Anti-spam window: confirmation < 2h ago → skip mail but mark processed.
        LocalDateTime createdAt = booking.getCreatedAt();
        if (createdAt != null) {
            Instant createdAtInstant = createdAt.atZone(ZoneId.systemDefault()).toInstant();
            if (Duration.between(createdAtInstant, now).compareTo(ANTI_SPAM_WINDOW) < 0) {
                booking.setReminderSentAt(now);
                bookingRepo.save(booking);
                logger.debug("Skipping fresh reminder for booking {} (created {} ago)",
                        booking.getId(), Duration.between(createdAtInstant, now));
                return;
            }
        }

        // Lookup client + care. JPA relations are lazy; we may need to resolve
        // them via repositories if the entities arrived detached.
        User client = booking.getUser();
        if (client == null && booking.getId() != null) {
            // Fall back via the booking's user id if available (defensive).
            client = userRepository.findById(extractUserId(booking)).orElse(null);
        }
        Care care = booking.getCare();
        if (care == null) {
            care = careRepository.findById(extractCareId(booking)).orElse(null);
        }

        if (client == null || client.getEmail() == null || client.getEmail().isBlank()
                || care == null) {
            logger.warn("Skipping reminder for booking {} (tenant {}): missing client/care",
                    booking.getId(), tenant.getSlug());
            booking.setReminderSentAt(now);
            bookingRepo.save(booking);
            return;
        }

        DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.FRENCH);
        DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm");
        String dateStr = booking.getAppointmentDate().format(dateFmt);
        String timeStr = booking.getAppointmentTime().format(timeFmt);
        String address = composeAddress(tenant);

        BookingReminderVars vars = new BookingReminderVars(
                client.getName(),
                tenant.getName(),
                care.getName(),
                dateStr,
                timeStr,
                address,
                booking.getId(),
                frontendBaseUrl + "/bookings");

        mailOutbox.queue(MailTemplate.BOOKING_REMINDER_J1, vars,
                client.getEmail(), tenant.getSlug());

        booking.setReminderSentAt(now);
        bookingRepo.save(booking);
        logger.info("J-1 reminder queued for booking {} (tenant {})",
                booking.getId(), tenant.getSlug());
    }

    /**
     * Lazy proxies expose ids without triggering initialization; the simple
     * getter on the eager id field on the related side is safer than the
     * dotted accessor when entities are detached.
     */
    private Long extractUserId(CareBooking booking) {
        return booking.getUser() != null ? booking.getUser().getId() : null;
    }

    private Long extractCareId(CareBooking booking) {
        return booking.getCare() != null ? booking.getCare().getId() : null;
    }

    private static String composeAddress(Tenant t) {
        StringBuilder sb = new StringBuilder();
        if (t.getAddressStreet() != null && !t.getAddressStreet().isBlank()) {
            sb.append(t.getAddressStreet());
        }
        if (t.getAddressPostalCode() != null && !t.getAddressPostalCode().isBlank()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(t.getAddressPostalCode());
        }
        if (t.getAddressCity() != null && !t.getAddressCity().isBlank()) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(t.getAddressCity());
        }
        return sb.toString();
    }
}
