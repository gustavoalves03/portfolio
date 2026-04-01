package com.prettyface.app.bookings.app;

import com.prettyface.app.availability.app.SlotAvailabilityService;
import com.prettyface.app.bookings.domain.CareBooking;
import com.prettyface.app.bookings.domain.CareBookingStatus;
import com.prettyface.app.bookings.repo.CareBookingRepository;
import com.prettyface.app.bookings.web.dto.CareBookingDetailedResponse;
import com.prettyface.app.bookings.web.dto.CareBookingRequest;
import com.prettyface.app.bookings.web.dto.CareBookingResponse;
import com.prettyface.app.bookings.web.dto.ClientBookingRequest;
import com.prettyface.app.bookings.web.dto.ClientBookingResponse;
import com.prettyface.app.bookings.web.mapper.CareBookingMapper;
import com.prettyface.app.care.domain.Care;
import com.prettyface.app.care.domain.CareStatus;
import com.prettyface.app.care.repo.CareRepository;
import com.prettyface.app.notification.app.EmailService;
import com.prettyface.app.users.domain.User;
import com.prettyface.app.users.repo.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.LocalTime;

@Service
public class CareBookingService {
    private final CareBookingRepository repo;
    private final UserRepository userRepository;
    private final CareRepository careRepository;
    private final SlotAvailabilityService slotAvailabilityService;
    private final EmailService emailService;

    public CareBookingService(CareBookingRepository repo, UserRepository userRepository,
                               CareRepository careRepository, SlotAvailabilityService slotAvailabilityService,
                               EmailService emailService) {
        this.repo = repo;
        this.userRepository = userRepository;
        this.careRepository = careRepository;
        this.slotAvailabilityService = slotAvailabilityService;
        this.emailService = emailService;
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
            LocalDateTime from,
            LocalDateTime to,
            Long userId,
            Pageable pageable
    ) {
        return repo.findByFilters(status, from, to, userId, pageable)
                .map(CareBookingMapper::toDetailedResponse);
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
        CareBooking b = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("Care booking not found: " + id));
        // Authoritative user/care can stay unchanged; update quantity/status only
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
}

