package com.prettyface.app.tracking.web;

import com.prettyface.app.auth.UserPrincipal;
import com.prettyface.app.tracking.app.TrackingService;
import com.prettyface.app.tracking.domain.PhotoType;
import com.prettyface.app.tracking.web.dto.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * Beauty tracking endpoints for both PRO (salon staff) and CLIENT (end user).
 */
@RestController
public class TrackingController {

    private final TrackingService trackingService;

    public TrackingController(TrackingService trackingService) {
        this.trackingService = trackingService;
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
}
