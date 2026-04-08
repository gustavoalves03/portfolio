package com.prettyface.app.tracking.web;

import com.prettyface.app.auth.UserPrincipal;
import com.prettyface.app.employee.app.EmployeePermissionService;
import com.prettyface.app.employee.domain.AccessLevel;
import com.prettyface.app.employee.domain.PermissionDomain;
import com.prettyface.app.employee.repo.EmployeeRepository;
import com.prettyface.app.tracking.app.TrackingService;
import com.prettyface.app.tracking.domain.PhotoType;
import com.prettyface.app.tracking.web.dto.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Beauty tracking endpoints for both PRO (salon staff) and CLIENT (end user).
 */
@RestController
public class TrackingController {

    private final TrackingService trackingService;
    private final EmployeePermissionService permissionService;
    private final EmployeeRepository employeeRepo;

    public TrackingController(TrackingService trackingService,
                               EmployeePermissionService permissionService,
                               EmployeeRepository employeeRepo) {
        this.trackingService = trackingService;
        this.permissionService = permissionService;
        this.employeeRepo = employeeRepo;
    }

    private Long resolveEmployeeId(Long userId) {
        return employeeRepo.findByUserId(userId)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.FORBIDDEN, "Not an employee"))
                .getId();
    }

    // ── PRO endpoints (/api/pro/tracking) ──

    @GetMapping("/api/pro/tracking/clients/{userId}")
    public ClientHistoryResponse getClientHistory(@PathVariable Long userId) {
        return trackingService.getClientHistory(userId);
    }

    @PutMapping("/api/pro/tracking/clients/{userId}/profile")
    public ClientProfileResponse updateClientProfile(
            @PathVariable Long userId,
            @RequestBody UpdateClientProfileRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return trackingService.updateProfile(userId, request, principal.getId());
    }

    @PostMapping("/api/pro/tracking/clients/{userId}/visits")
    @ResponseStatus(HttpStatus.CREATED)
    public VisitRecordResponse createVisitRecord(
            @PathVariable Long userId,
            @RequestBody CreateVisitRecordRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return trackingService.createVisitRecord(userId, request, principal.getId());
    }

    @PostMapping("/api/pro/tracking/visits/{visitId}/photos")
    @ResponseStatus(HttpStatus.CREATED)
    public VisitPhotoResponse uploadVisitPhoto(
            @PathVariable Long visitId,
            @RequestParam("photo") MultipartFile photo,
            @RequestParam("type") PhotoType type,
            @AuthenticationPrincipal UserPrincipal principal) {
        return trackingService.addVisitPhoto(visitId, photo, type, principal.getId());
    }

    @PostMapping("/api/pro/tracking/clients/{userId}/reminders")
    @ResponseStatus(HttpStatus.CREATED)
    public ReminderResponse createReminder(
            @PathVariable Long userId,
            @RequestBody CreateReminderRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return trackingService.createReminder(userId, request, principal.getId());
    }

    // ── CLIENT endpoints (/api/client/tracking) ──

    @GetMapping("/api/client/tracking/history")
    public ClientHistoryResponse getOwnHistory(@AuthenticationPrincipal UserPrincipal principal) {
        return trackingService.getClientHistory(principal.getId());
    }

    @PutMapping("/api/client/tracking/consent")
    public ClientProfileResponse updateOwnConsent(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody ConsentUpdateRequest request) {
        return trackingService.updateConsent(principal.getId(), request.consentPhotos(), request.consentPublicShare());
    }

    @PostMapping("/api/client/tracking/visits/{visitId}/rate")
    public VisitRecordResponse rateVisit(
            @PathVariable Long visitId,
            @RequestBody RateVisitRequest request) {
        return trackingService.rateVisit(visitId, request.score(), request.comment());
    }

    @DeleteMapping("/api/client/tracking/photos")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteOwnPhotos(@AuthenticationPrincipal UserPrincipal principal) {
        trackingService.deleteAllPhotos(principal.getId());
    }

    // ── EMPLOYEE endpoints (/api/employee/tracking) ──

    @GetMapping("/api/employee/tracking/clients/{userId}")
    public ClientHistoryResponse getClientHistoryAsEmployee(
            @PathVariable Long userId,
            @AuthenticationPrincipal UserPrincipal principal) {
        Long employeeId = resolveEmployeeId(principal.getId());
        permissionService.requireAccess(employeeId, PermissionDomain.PROFILE, AccessLevel.READ);
        return trackingService.getClientHistory(userId);
    }

    @PutMapping("/api/employee/tracking/clients/{userId}/profile")
    public ClientProfileResponse updateClientProfileAsEmployee(
            @PathVariable Long userId,
            @RequestBody UpdateClientProfileRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        Long employeeId = resolveEmployeeId(principal.getId());
        permissionService.requireAccess(employeeId, PermissionDomain.PROFILE, AccessLevel.WRITE);
        return trackingService.updateProfile(userId, request, principal.getId());
    }

    @PostMapping("/api/employee/tracking/clients/{userId}/visits")
    @ResponseStatus(HttpStatus.CREATED)
    public VisitRecordResponse createVisitRecordAsEmployee(
            @PathVariable Long userId,
            @RequestBody CreateVisitRecordRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        Long employeeId = resolveEmployeeId(principal.getId());
        permissionService.requireAccess(employeeId, PermissionDomain.VISITS, AccessLevel.WRITE);
        return trackingService.createVisitRecord(userId, request, principal.getId());
    }

    @GetMapping("/api/employee/permissions/me")
    public Map<String, String> getMyPermissions(@AuthenticationPrincipal UserPrincipal principal) {
        Long employeeId = resolveEmployeeId(principal.getId());
        return permissionService.getPermissions(employeeId).entrySet().stream()
                .collect(Collectors.toMap(e -> e.getKey().name(), e -> e.getValue().name()));
    }
}
