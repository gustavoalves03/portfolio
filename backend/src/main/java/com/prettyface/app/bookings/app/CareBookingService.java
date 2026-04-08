package com.prettyface.app.bookings.app;

import com.prettyface.app.availability.app.SlotAvailabilityService;
import com.prettyface.app.bookings.domain.CareBooking;
import com.prettyface.app.bookings.domain.CareBookingStatus;
import com.prettyface.app.bookings.domain.ClientBookingHistory;
import com.prettyface.app.bookings.repo.CareBookingRepository;
import com.prettyface.app.bookings.repo.ClientBookingHistoryRepository;
import com.prettyface.app.bookings.web.dto.CareBookingDetailedResponse;
import com.prettyface.app.bookings.web.dto.CareBookingRequest;
import com.prettyface.app.bookings.web.dto.CareBookingResponse;
import com.prettyface.app.bookings.web.dto.ClientBookingRequest;
import com.prettyface.app.bookings.web.dto.ClientBookingResponse;
import com.prettyface.app.bookings.web.mapper.CareBookingMapper;
import com.prettyface.app.care.domain.Care;
import com.prettyface.app.care.domain.CareStatus;
import com.prettyface.app.care.repo.CareRepository;
import com.prettyface.app.multitenancy.ApplicationSchemaExecutor;
import com.prettyface.app.multitenancy.TenantContext;
import com.prettyface.app.notification.app.EmailService;
import com.prettyface.app.tenant.domain.Tenant;
import com.prettyface.app.tenant.repo.TenantRepository;
import com.prettyface.app.users.domain.User;
import com.prettyface.app.users.repo.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Service
public class CareBookingService {
    private final CareBookingRepository repo;
    private final UserRepository userRepository;
    private final CareRepository careRepository;
    private final SlotAvailabilityService slotAvailabilityService;
    private final EmailService emailService;
    private final TenantRepository tenantRepository;
    private final ClientBookingHistoryRepository clientBookingHistoryRepository;
    private final ApplicationSchemaExecutor applicationSchemaExecutor;
    private final com.prettyface.app.employee.repo.EmployeeRepository employeeRepository;
    private final com.prettyface.app.notification.app.NotificationDispatcher notificationDispatcher;

    public CareBookingService(CareBookingRepository repo, UserRepository userRepository,
                               CareRepository careRepository, SlotAvailabilityService slotAvailabilityService,
                               EmailService emailService, TenantRepository tenantRepository,
                               ClientBookingHistoryRepository clientBookingHistoryRepository,
                               ApplicationSchemaExecutor applicationSchemaExecutor,
                               com.prettyface.app.employee.repo.EmployeeRepository employeeRepository,
                               com.prettyface.app.notification.app.NotificationDispatcher notificationDispatcher) {
        this.repo = repo;
        this.userRepository = userRepository;
        this.careRepository = careRepository;
        this.slotAvailabilityService = slotAvailabilityService;
        this.emailService = emailService;
        this.tenantRepository = tenantRepository;
        this.clientBookingHistoryRepository = clientBookingHistoryRepository;
        this.applicationSchemaExecutor = applicationSchemaExecutor;
        this.employeeRepository = employeeRepository;
        this.notificationDispatcher = notificationDispatcher;
    }

    @Transactional(readOnly = true)
    public Page<CareBookingResponse> list(Pageable pageable) {
        return repo.findAll(pageable).map(CareBookingMapper::toResponse);
    }

    /**
     * List bookings with optional filters and detailed information
     * @param status Optional status filter (PENDING, CONFIRMED)
     * @param from Optional start date filter
     * @param to Optional end date filter
     * @param userId Optional user ID filter
     * @param pageable Pagination parameters
     * @return Page of detailed booking responses
     */
    @Transactional(readOnly = true)
    public Page<CareBookingDetailedResponse> listDetailed(
            CareBookingStatus status,
            LocalDate from,
            LocalDate to,
            Long userId,
            Pageable pageable
    ) {
        Page<CareBooking> bookings = repo.findByFilters(status, from, to, userId, pageable);

        // Resolve employee names in batch
        java.util.Set<Long> employeeIds = bookings.getContent().stream()
                .map(CareBooking::getEmployeeId)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());

        java.util.Map<Long, String> employeeNames = new java.util.HashMap<>();
        if (!employeeIds.isEmpty()) {
            employeeRepository.findAllById(employeeIds)
                    .forEach(e -> employeeNames.put(e.getId(), e.getName()));
        }

        return bookings.map(b -> CareBookingMapper.toDetailedResponse(b,
                b.getEmployeeId() != null ? employeeNames.get(b.getEmployeeId()) : null));
    }

    @Transactional(readOnly = true)
    public CareBookingResponse get(Long id) {
        return repo.findById(id).map(CareBookingMapper::toResponse)
                .orElseThrow(() -> new IllegalArgumentException("Care booking not found: " + id));
    }

    @Transactional
    public CareBookingResponse create(CareBookingRequest req) {
        var user = userRepository.findById(req.userId())
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + req.userId()));
        var care = careRepository.findById(req.careId())
                .orElseThrow(() -> new IllegalArgumentException("Care not found: " + req.careId()));

        CareBooking b = new CareBooking();
        b.setUser(user);
        b.setCare(care);
        CareBookingMapper.updateEntity(b, req);
        return CareBookingMapper.toResponse(repo.save(b));
    }

    @Transactional
    public CareBookingResponse update(Long id, CareBookingRequest req) {
        CareBooking b = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Care booking not found: " + id));

        LocalDateTime originalDateTime = LocalDateTime.of(b.getAppointmentDate(), b.getAppointmentTime());
        boolean isPast = originalDateTime.isBefore(LocalDateTime.now());

        // Rule 1: Cannot cancel a past booking — use NO_SHOW instead
        if (isPast && req.status() == CareBookingStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cannot cancel a past appointment. Use NO_SHOW status instead.");
        }

        // Rule 2: Cannot modify date/time of a past booking
        if (isPast && (!req.appointmentDate().equals(b.getAppointmentDate())
                    || !req.appointmentTime().equals(b.getAppointmentTime()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cannot reschedule a past appointment.");
        }

        // Rule 3: If date, time, or care changes → re-verify slot availability
        boolean dateChanged = !req.appointmentDate().equals(b.getAppointmentDate());
        boolean timeChanged = !req.appointmentTime().equals(b.getAppointmentTime());
        boolean careChanged = !req.careId().equals(b.getCare().getId());

        if ((dateChanged || timeChanged || careChanged)
                && req.status() != CareBookingStatus.CANCELLED
                && req.status() != CareBookingStatus.NO_SHOW) {

            boolean slotAvailable = slotAvailabilityService
                    .getAvailableSlots(req.appointmentDate(), req.careId())
                    .stream()
                    .anyMatch(slot -> LocalTime.parse(slot.startTime()).equals(req.appointmentTime()));

            if (!slotAvailable) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "The new time slot is not available.");
            }
        }

        CareBookingMapper.updateEntity(b, req);
        return CareBookingMapper.toResponse(repo.save(b));
    }

    @Transactional
    public void delete(Long id) { repo.deleteById(id); }

    @Transactional
    public ClientBookingResponse createClientBooking(User client, User owner, String salonName,
                                                      ClientBookingRequest req) {
        Care care = careRepository.findById(req.careId())
                .filter(c -> c.getStatus() == CareStatus.ACTIVE)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Care not found or inactive"));

        LocalTime time = LocalTime.parse(req.appointmentTime());

        // Get tenant for booking limits
        String slug = TenantContext.getCurrentTenant();
        Tenant tenant = null;
        if (slug != null) {
            tenant = tenantRepository.findBySlug(slug).orElse(null);
        }

        // Check minimum advance time
        if (tenant != null && tenant.getMinAdvanceMinutes() != null && tenant.getMinAdvanceMinutes() > 0) {
            LocalDateTime appointmentDateTime = LocalDateTime.of(req.appointmentDate(), time);
            LocalDateTime minAllowed = LocalDateTime.now().plusMinutes(tenant.getMinAdvanceMinutes());
            if (appointmentDateTime.isBefore(minAllowed)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Booking must be at least " + tenant.getMinAdvanceMinutes() + " minutes in advance");
            }
        }

        // Check maximum advance time
        if (tenant != null && tenant.getMaxAdvanceDays() != null && tenant.getMaxAdvanceDays() > 0) {
            LocalDate maxDate = LocalDate.now().plusDays(tenant.getMaxAdvanceDays());
            if (req.appointmentDate().isAfter(maxDate)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Booking cannot be more than " + tenant.getMaxAdvanceDays() + " days in advance");
            }
        }

        // Cross-salon history lives in the shared application schema, so query it in
        // a separate transaction with TenantContext cleared.
        List<ClientBookingHistory> clientDayBookings = applicationSchemaExecutor.call(() ->
                clientBookingHistoryRepository.findByUserIdAndAppointmentDateAndStatusNot(
                        client.getId(),
                        req.appointmentDate(),
                        "CANCELLED")
        );

        for (ClientBookingHistory existing : clientDayBookings) {
            LocalTime existingStart = existing.getAppointmentTime();
            LocalTime existingEnd = existingStart.plusMinutes(existing.getCareDuration());
            LocalTime newStart = time;
            LocalTime newEnd = time.plusMinutes(care.getDuration());
            if (newStart.isBefore(existingEnd) && newEnd.isAfter(existingStart)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "You already have a booking from " + existingStart + " to " + existingEnd +
                        " at " + existing.getSalonName());
            }
        }

        // Check max client hours per day
        if (tenant != null && tenant.getMaxClientHoursPerDay() != null && tenant.getMaxClientHoursPerDay() > 0) {
            int totalBookedMinutes = clientDayBookings.stream()
                    .mapToInt(ClientBookingHistory::getCareDuration)
                    .sum();
            int maxMinutes = tenant.getMaxClientHoursPerDay() * 60;
            if (totalBookedMinutes + care.getDuration() > maxMinutes) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                        "Maximum " + tenant.getMaxClientHoursPerDay() + " hours of appointments per day");
            }
        }

        // Re-verify slot availability (concurrency protection)
        boolean slotAvailable = slotAvailabilityService.getAvailableSlots(req.appointmentDate(), req.careId())
                .stream()
                .anyMatch(slot -> LocalTime.parse(slot.startTime()).equals(time));

        if (!slotAvailable) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Slot no longer available");
        }

        CareBooking booking = new CareBooking();
        booking.setUser(client);
        booking.setCare(care);
        booking.setQuantity(1);
        booking.setAppointmentDate(req.appointmentDate());
        booking.setAppointmentTime(time);
        booking.setStatus(CareBookingStatus.CONFIRMED);

        try {
            booking = repo.save(booking);
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Slot no longer available");
        }

        // Async emails (fire-and-forget)
        emailService.sendBookingConfirmationEmail(client, booking, care, salonName);
        emailService.sendNewBookingNotificationEmail(owner, booking, care, client.getName());

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

        return new ClientBookingResponse(
                booking.getId(),
                care.getName(),
                care.getPrice(),
                care.getDuration(),
                booking.getAppointmentDate().toString(),
                booking.getAppointmentTime().toString(),
                booking.getStatus().name(),
                salonName
        );
    }

    @Transactional(readOnly = true)
    public Optional<CareBooking> findById(Long id) {
        return repo.findById(id);
    }

    @Transactional
    public void cancelBooking(Long bookingId) {
        CareBooking booking = repo.findById(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));
        booking.setStatus(CareBookingStatus.CANCELLED);
        repo.save(booking);

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
    }

    @Transactional
    public int cancelFutureBookingsForCare(Long careId) {
        List<CareBooking> futureBookings = repo.findByCareIdAndAppointmentDateGreaterThanEqualAndStatusNot(
                careId, LocalDate.now(), CareBookingStatus.CANCELLED);
        for (CareBooking booking : futureBookings) {
            booking.setStatus(CareBookingStatus.CANCELLED);
            repo.save(booking);
        }
        return futureBookings.size();
    }
}
