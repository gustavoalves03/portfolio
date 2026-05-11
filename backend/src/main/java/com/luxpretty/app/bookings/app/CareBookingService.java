package com.luxpretty.app.bookings.app;


import com.luxpretty.app.common.error.ResourceNotFoundException;
import com.luxpretty.app.auth.UserPrincipal;
import com.luxpretty.app.availability.app.SlotAvailabilityService;
import com.luxpretty.app.bookings.domain.CareBooking;
import com.luxpretty.app.tracking.app.SalonClientService;
import com.luxpretty.app.bookings.domain.CareBookingStatus;
import com.luxpretty.app.bookings.domain.ClientBookingHistory;
import com.luxpretty.app.bookings.repo.CareBookingRepository;
import com.luxpretty.app.bookings.repo.ClientBookingHistoryRepository;
import com.luxpretty.app.bookings.web.dto.CareBookingDetailedResponse;
import com.luxpretty.app.bookings.web.dto.CareBookingRequest;
import com.luxpretty.app.bookings.web.dto.CareBookingResponse;
import com.luxpretty.app.bookings.web.dto.ClientBookingRequest;
import com.luxpretty.app.bookings.web.dto.ClientBookingResponse;
import com.luxpretty.app.bookings.web.mapper.CareBookingMapper;
import com.luxpretty.app.care.domain.Care;
import com.luxpretty.app.care.domain.CareStatus;
import com.luxpretty.app.care.repo.CareRepository;
import com.luxpretty.app.multitenancy.ApplicationSchemaExecutor;
import com.luxpretty.app.multitenancy.TenantContext;
import com.luxpretty.app.mail.app.MailOutboxService;
import com.luxpretty.app.mail.domain.MailTemplate;
import com.luxpretty.app.mail.vars.BookingConfirmedVars;
import com.luxpretty.app.mail.vars.BookingReceivedProVars;
import com.luxpretty.app.tenant.domain.Tenant;
import com.luxpretty.app.tenant.repo.TenantRepository;
import com.luxpretty.app.users.domain.Role;
import com.luxpretty.app.users.domain.User;
import com.luxpretty.app.users.repo.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class CareBookingService {
    private final CareBookingRepository repo;
    private final UserRepository userRepository;
    private final CareRepository careRepository;
    private final SlotAvailabilityService slotAvailabilityService;
    private final MailOutboxService mailOutbox;
    private final TenantRepository tenantRepository;

    @Value("${app.frontend.base-url}")
    private String frontendBaseUrl;
    private final ClientBookingHistoryRepository clientBookingHistoryRepository;
    private final ApplicationSchemaExecutor applicationSchemaExecutor;
    private final com.luxpretty.app.employee.repo.EmployeeRepository employeeRepository;
    private final com.luxpretty.app.notification.app.NotificationDispatcher notificationDispatcher;
    private final SalonClientService salonClientService;
    private final BookingPolicyService bookingPolicyService;

    public CareBookingService(CareBookingRepository repo, UserRepository userRepository,
                               CareRepository careRepository, SlotAvailabilityService slotAvailabilityService,
                               MailOutboxService mailOutbox, TenantRepository tenantRepository,
                               ClientBookingHistoryRepository clientBookingHistoryRepository,
                               ApplicationSchemaExecutor applicationSchemaExecutor,
                               com.luxpretty.app.employee.repo.EmployeeRepository employeeRepository,
                               com.luxpretty.app.notification.app.NotificationDispatcher notificationDispatcher,
                               SalonClientService salonClientService,
                               BookingPolicyService bookingPolicyService) {
        this.repo = repo;
        this.userRepository = userRepository;
        this.careRepository = careRepository;
        this.slotAvailabilityService = slotAvailabilityService;
        this.mailOutbox = mailOutbox;
        this.tenantRepository = tenantRepository;
        this.clientBookingHistoryRepository = clientBookingHistoryRepository;
        this.applicationSchemaExecutor = applicationSchemaExecutor;
        this.employeeRepository = employeeRepository;
        this.notificationDispatcher = notificationDispatcher;
        this.salonClientService = salonClientService;
        this.bookingPolicyService = bookingPolicyService;
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
        return listDetailed(status, from, to, userId, pageable, null);
    }

    /**
     * List bookings with optional filters, automatically scoping the result set
     * to the caller's own assignments when the caller is a plain EMPLOYEE
     * (PRO / ADMIN callers see every booking in the tenant). A {@code null}
     * caller is treated as "no extra scoping", which preserves behaviour for
     * internal/background calls and existing tests.
     */
    @Transactional(readOnly = true)
    public Page<CareBookingDetailedResponse> listDetailed(
            CareBookingStatus status,
            LocalDate from,
            LocalDate to,
            Long userId,
            Pageable pageable,
            UserPrincipal caller
    ) {
        Long employeeIdFilter = resolveEmployeeScope(caller);

        Page<CareBooking> bookings;
        if (employeeIdFilter != null) {
            bookings = repo.findByFiltersAndEmployeeId(
                    status, from, to, userId, employeeIdFilter, pageable);
        } else {
            bookings = repo.findByFilters(status, from, to, userId, pageable);
        }

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

        // Resolve salon client names in batch
        java.util.Set<Long> salonClientIds = bookings.getContent().stream()
                .map(CareBooking::getSalonClientId)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());

        java.util.Map<Long, String> salonClientNames = new java.util.HashMap<>();
        if (!salonClientIds.isEmpty()) {
            salonClientService.findAllByIds(salonClientIds)
                    .forEach(sc -> salonClientNames.put(sc.getId(), sc.getName()));
        }

        return bookings.map(b -> CareBookingMapper.toDetailedResponse(b,
                b.getEmployeeId() != null ? employeeNames.get(b.getEmployeeId()) : null,
                b.getSalonClientId() != null ? salonClientNames.get(b.getSalonClientId()) : null));
    }

    @Transactional(readOnly = true)
    public CareBookingResponse get(Long id) {
        return repo.findById(id).map(CareBookingMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Care booking not found: " + id));
    }

    @Transactional
    public CareBookingResponse create(CareBookingRequest req) {
        TenantContext.requireActive();
        var user = userRepository.findById(req.userId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + req.userId()));
        var care = careRepository.findById(req.careId())
                .orElseThrow(() -> new ResourceNotFoundException("Care not found: " + req.careId()));

        // Verify slot availability (opening hours + existing bookings)
        boolean slotAvailable = slotAvailabilityService
                .getAvailableSlots(req.appointmentDate(), req.careId())
                .stream()
                .anyMatch(slot -> LocalTime.parse(slot.startTime()).equals(req.appointmentTime()));

        if (!slotAvailable) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "The requested time slot is not available.");
        }

        // Clear any stale CANCELLED row for the same slot triple so the unique
        // constraint UK_BOOKING_SLOT doesn't fire on re-booking (see createClientBooking).
        evictCancelledBookingsForSlot(req.appointmentDate(), req.appointmentTime(), req.careId());

        CareBooking b = new CareBooking();
        b.setUser(user);
        b.setCare(care);
        CareBookingMapper.updateEntity(b, req);
        if (req.salonClientId() != null) {
            b.setSalonClientId(req.salonClientId());
        }
        try {
            return CareBookingMapper.toResponse(repo.save(b));
        } catch (DataIntegrityViolationException ex) {
            // Same translation as createClientBooking: unique-slot collision on a
            // concurrent insert should surface as a clean 409 with a user-friendly
            // message, not a raw DataIntegrityViolationException.
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Slot no longer available");
        }
    }

    /**
     * Hard-deletes any CANCELLED booking rows holding the given (date, time, care)
     * slot triple. Needed because {@code UK_BOOKING_SLOT} is a plain unique constraint
     * (no {@code status} filter) and the cancel flow is a soft-delete, so a stale
     * cancelled row would block a legitimate re-book on the same slot.
     *
     * <p>Called from {@link #create(CareBookingRequest)} and
     * {@link #createClientBooking(User, User, String, ClientBookingRequest)} right
     * before the insert. The cancelled booking's history is already preserved
     * in the shared {@code CLIENT_BOOKING_HISTORY} table.
     */
    private void evictCancelledBookingsForSlot(LocalDate date, LocalTime time, Long careId) {
        List<CareBooking> stale = repo.findByAppointmentDateAndAppointmentTimeAndCareIdAndStatus(
                date, time, careId, CareBookingStatus.CANCELLED);
        if (!stale.isEmpty()) {
            repo.deleteAll(stale);
            repo.flush();
        }
    }

    @Transactional
    public CareBookingResponse update(Long id, CareBookingRequest req) {
        return update(id, req, null);
    }

    /**
     * Overload that enforces booking-ownership for EMPLOYEE callers: a plain
     * employee can only mutate bookings assigned to themselves. PRO / ADMIN
     * callers retain full access, matching the existing tenant-owner contract.
     * Passing {@code null} disables the caller check (preserves behaviour for
     * background jobs and existing tests).
     */
    @Transactional
    public CareBookingResponse update(Long id, CareBookingRequest req, UserPrincipal caller) {
        TenantContext.requireActive();
        CareBooking b = repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Care booking not found: " + id));

        requireCanMutateBooking(caller, b);

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
    public void delete(Long id) {
        TenantContext.requireActive();
        repo.deleteById(id);
    }

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

        // Explicit past-date guard — safety net independent of minAdvanceMinutes or slot service
        if (req.appointmentDate().isBefore(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot book in the past");
        }

        // Per-tenant booking-policy guard (max bookings/day per client + max
        // bookings/week for first-time clients). Throws BookingLimitExceededException
        // → mapped to HTTP 409 with a typed error code by GlobalExceptionHandler.
        bookingPolicyService.validateClientLimits(client.getId(), req.appointmentDate());

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

        // UK_BOOKING_SLOT (appointment_date, appointment_time, care_id) is a plain
        // unique constraint — cancel is a soft-delete that leaves the row in place,
        // so a stale CANCELLED row would collide on re-booking. Hard-delete any
        // cancelled booking for the same slot triple before inserting the new one.
        // History/audit is preserved independently in CLIENT_BOOKING_HISTORY.
        evictCancelledBookingsForSlot(req.appointmentDate(), time, req.careId());

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

        // Auto-create/find SalonClient for this user
        var salonClient = salonClientService.getOrCreateForUser(client.getId(), client.getName(), null);
        booking.setSalonClientId(salonClient.getId());
        repo.save(booking);

        // Queue transactional emails via outbox (delivered after commit)
        DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.FRENCH);
        DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm");
        String dateStr = booking.getAppointmentDate().format(dateFmt);
        String timeStr = booking.getAppointmentTime().format(timeFmt);
        String durationStr = formatDuration(care.getDuration());
        String tenantSlug = TenantContext.getCurrentTenant();

        mailOutbox.queue(
                MailTemplate.BOOKING_CONFIRMED,
                new BookingConfirmedVars(
                        client.getName(), salonName, care.getName(),
                        BigDecimal.valueOf(care.getPrice()),
                        durationStr, dateStr, timeStr,
                        booking.getId(), frontendBaseUrl + "/bookings"),
                client.getEmail(),
                tenantSlug);

        mailOutbox.queue(
                MailTemplate.BOOKING_RECEIVED_PRO,
                new BookingReceivedProVars(
                        owner.getName(), client.getName(), care.getName(),
                        BigDecimal.valueOf(care.getPrice()),
                        durationStr, dateStr, timeStr,
                        booking.getId(), frontendBaseUrl + "/pro/bookings"),
                owner.getEmail(),
                tenantSlug);

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
                com.luxpretty.app.notification.domain.NotificationType.NEW_BOOKING,
                com.luxpretty.app.notification.domain.NotificationCategory.BOOKING,
                "Nouveau rendez-vous",
                client.getName() + " - " + care.getName() + ", " + booking.getAppointmentTime(),
                booking.getId(),
                com.luxpretty.app.notification.domain.ReferenceType.BOOKING
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
        TenantContext.requireActive();
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
                    com.luxpretty.app.notification.domain.NotificationType.BOOKING_CANCELLED,
                    com.luxpretty.app.notification.domain.NotificationCategory.BOOKING,
                    "Rendez-vous annulé",
                    (client != null ? client.getName() : "Client") + " - " + (care != null ? care.getName() : "Soin") + ", " + booking.getAppointmentDate(),
                    booking.getId(),
                    com.luxpretty.app.notification.domain.ReferenceType.BOOKING
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

    // -----------------------------------------------------------------------
    // Authorisation helpers
    // -----------------------------------------------------------------------

    /**
     * When a caller is provided and their role is EMPLOYEE, return the
     * employeeId they are allowed to see (= their own). PRO / ADMIN / null
     * callers return {@code null}, meaning "no employee scope applied".
     */
    private Long resolveEmployeeScope(UserPrincipal caller) {
        if (caller == null) {
            return null;
        }
        Role role = userRepository.findById(caller.getId())
                .map(User::getRole)
                .orElse(null);
        if (role == Role.PRO || role == Role.ADMIN) {
            return null;
        }
        // EMPLOYEE (or unknown role) — lock results to their own employeeId.
        return employeeRepository.findByUserId(caller.getId())
                .map(com.luxpretty.app.employee.domain.Employee::getId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "No employee profile linked to caller"));
    }

    /**
     * EMPLOYEE callers may only mutate bookings where they are the assigned
     * employee. PRO / ADMIN (and a {@code null} caller, used for background
     * flows and existing tests) keep full write access.
     */
    private void requireCanMutateBooking(UserPrincipal caller, CareBooking booking) {
        if (caller == null) {
            return;
        }
        Role role = userRepository.findById(caller.getId())
                .map(User::getRole)
                .orElse(null);
        if (role == Role.PRO || role == Role.ADMIN) {
            return;
        }
        Long callerEmployeeId = employeeRepository.findByUserId(caller.getId())
                .map(com.luxpretty.app.employee.domain.Employee::getId)
                .orElse(null);
        if (booking.getEmployeeId() != null
                && !booking.getEmployeeId().equals(callerEmployeeId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Cannot modify a booking assigned to another employee");
        }
    }

    // -----------------------------------------------------------------------
    // Formatting helpers
    // -----------------------------------------------------------------------

    private static String formatDuration(int minutes) {
        if (minutes < 60) {
            return minutes + " min";
        }
        int hours = minutes / 60;
        int remaining = minutes % 60;
        if (remaining == 0) {
            return hours + "h";
        }
        return hours + "h" + String.format("%02d", remaining);
    }
}
