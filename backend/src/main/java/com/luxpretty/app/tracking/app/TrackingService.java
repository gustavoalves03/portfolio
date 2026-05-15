package com.luxpretty.app.tracking.app;

import com.luxpretty.app.auth.UserPrincipal;
import com.luxpretty.app.common.storage.StorageBackend;
import com.luxpretty.app.employee.app.EmployeePermissionService;
import com.luxpretty.app.employee.domain.AccessLevel;
import com.luxpretty.app.employee.domain.PermissionDomain;
import com.luxpretty.app.employee.repo.EmployeeRepository;
import com.luxpretty.app.multitenancy.ApplicationSchemaExecutor;
import com.luxpretty.app.tracking.domain.*;
import com.luxpretty.app.tracking.repo.*;
import com.luxpretty.app.tracking.web.dto.*;
import com.luxpretty.app.users.domain.Role;
import com.luxpretty.app.users.domain.User;
import com.luxpretty.app.users.repo.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class TrackingService {

    private final ClientProfileRepository profileRepo;
    private final VisitRecordRepository visitRepo;
    private final VisitPhotoRepository photoRepo;
    private final ClientReminderRepository reminderRepo;
    private final UserRepository userRepository;
    private final ApplicationSchemaExecutor applicationSchemaExecutor;
    private final EmployeePermissionService permissionService;
    private final EmployeeRepository employeeRepository;
    private final StorageBackend storageBackend;
    private final com.luxpretty.app.users.app.UserRoleService userRoleService;

    public TrackingService(ClientProfileRepository profileRepo,
                           VisitRecordRepository visitRepo,
                           VisitPhotoRepository photoRepo,
                           ClientReminderRepository reminderRepo,
                           UserRepository userRepository,
                           ApplicationSchemaExecutor applicationSchemaExecutor,
                           EmployeePermissionService permissionService,
                           EmployeeRepository employeeRepository,
                           StorageBackend storageBackend,
                           com.luxpretty.app.users.app.UserRoleService userRoleService) {
        this.profileRepo = profileRepo;
        this.visitRepo = visitRepo;
        this.photoRepo = photoRepo;
        this.reminderRepo = reminderRepo;
        this.userRepository = userRepository;
        this.applicationSchemaExecutor = applicationSchemaExecutor;
        this.permissionService = permissionService;
        this.employeeRepository = employeeRepository;
        this.storageBackend = storageBackend;
        this.userRoleService = userRoleService;
    }

    // ── Authorization helpers ──

    /**
     * Verify that the caller may perform an operation on {@code targetUserId}'s tracking
     * data at the given (domain, level). Rules:
     * <ul>
     *   <li>PRO / ADMIN users bypass the per-domain permission check (tenant owners).</li>
     *   <li>A caller acting on their own tracking data ({@code caller.id == targetUserId})
     *       bypasses the check (client self-operations).</li>
     *   <li>Any other caller MUST have an {@link com.luxpretty.app.employee.domain.Employee}
     *       record, and that employee must hold at least {@code level} on {@code domain}.</li>
     * </ul>
     * Throws {@link ResponseStatusException} 403 on any failure.
     */
    private void requireTrackingAccess(UserPrincipal caller,
                                       Long targetUserId,
                                       PermissionDomain domain,
                                       AccessLevel level) {
        if (caller == null || caller.getId() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Unauthenticated caller");
        }
        // Self-access bypasses the per-domain check.
        if (targetUserId != null && caller.getId().equals(targetUserId)) {
            return;
        }
        boolean canManage = applicationSchemaExecutor.call(
                () -> userRoleService.hasAnyRoleAcrossScopes(caller.getId(), Role.PRO, Role.ADMIN));
        if (canManage) {
            return;
        }
        // Must be an employee with adequate permission.
        Long employeeId = employeeRepository.findByUserId(caller.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Caller has no tracking access"))
                .getId();
        permissionService.requireAccess(employeeId, domain, level);
    }

    // ── Profile ──

    @Transactional
    public ClientProfileResponse getOrCreateProfile(Long userId) {
        ClientProfile profile = profileRepo.findByUserId(userId)
                .orElseGet(() -> {
                    ClientProfile p = new ClientProfile();
                    p.setUserId(userId);
                    return profileRepo.save(p);
                });
        return toProfileResponse(profile);
    }

    @Transactional
    public ClientProfileResponse updateProfile(Long userId, UpdateClientProfileRequest request, UserPrincipal caller) {
        requireTrackingAccess(caller, userId, PermissionDomain.PROFILE, AccessLevel.WRITE);
        ClientProfile profile = profileRepo.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Client profile not found for userId: " + userId));
        if (request.notes() != null) profile.setNotes(request.notes());
        if (request.skinType() != null) profile.setSkinType(request.skinType());
        if (request.hairType() != null) profile.setHairType(request.hairType());
        if (request.allergies() != null) profile.setAllergies(request.allergies());
        if (request.preferences() != null) profile.setPreferences(request.preferences());
        profile.setUpdatedAt(java.time.LocalDateTime.now());
        profile.setUpdatedBy(caller.getId());
        return toProfileResponse(profileRepo.save(profile));
    }

    @Transactional
    public ClientProfileResponse updateConsent(Long userId, boolean consentPhotos, boolean consentPublicShare, UserPrincipal caller) {
        requireTrackingAccess(caller, userId, PermissionDomain.PROFILE, AccessLevel.WRITE);
        ClientProfile profile = profileRepo.findByUserId(userId)
                .orElseGet(() -> {
                    ClientProfile p = new ClientProfile();
                    p.setUserId(userId);
                    return profileRepo.save(p);
                });
        profile.setConsentPhotos(consentPhotos);
        profile.setConsentPublicShare(consentPublicShare);
        profile.setConsentGivenAt(LocalDateTime.now());
        return toProfileResponse(profileRepo.save(profile));
    }

    // ── Visits ──

    @Transactional
    public VisitRecordResponse createVisitRecord(Long userId, CreateVisitRecordRequest request, UserPrincipal caller) {
        requireTrackingAccess(caller, userId, PermissionDomain.VISITS, AccessLevel.WRITE);
        ClientProfile profile = profileRepo.findByUserId(userId)
                .orElseGet(() -> {
                    ClientProfile p = new ClientProfile();
                    p.setUserId(userId);
                    return profileRepo.save(p);
                });

        VisitRecord visit = new VisitRecord();
        visit.setClientProfileId(profile.getId());
        visit.setBookingId(request.bookingId());
        visit.setCareId(request.careId());
        visit.setCareName(request.careName());
        visit.setVisitDate(request.visitDate());
        visit.setPractitionerNotes(request.practitionerNotes());
        visit.setProductsUsed(request.productsUsed());
        visit.setUpdatedBy(caller.getId());
        visit.setUpdatedAt(java.time.LocalDateTime.now());
        visit = visitRepo.save(visit);

        return toVisitResponse(visit);
    }

    @Transactional
    public VisitPhotoResponse addVisitPhoto(Long visitRecordId, MultipartFile photo, PhotoType type, UserPrincipal caller) {
        VisitRecord visit = visitRepo.findById(visitRecordId)
                .orElseThrow(() -> new RuntimeException("Visit record not found: " + visitRecordId));

        Long ownerUserId = resolveOwnerUserId(visit.getClientProfileId());
        requireTrackingAccess(caller, ownerUserId, PermissionDomain.PHOTOS, AccessLevel.WRITE);

        List<VisitPhoto> existing = photoRepo.findByVisitRecordIdOrderByImageOrderAsc(visitRecordId);
        int nextOrder = existing.isEmpty() ? 0 : existing.getLast().getImageOrder() + 1;

        String savedPath = saveFile(photo, visitRecordId, type.name().toLowerCase());

        VisitPhoto vp = new VisitPhoto();
        vp.setVisitRecordId(visitRecordId);
        vp.setPhotoType(type);
        vp.setImagePath(savedPath);
        vp.setImageOrder(nextOrder);
        vp.setUploadedBy(caller.getId());
        vp = photoRepo.save(vp);

        return toPhotoResponse(vp);
    }

    @Transactional
    public VisitRecordResponse rateVisit(Long visitRecordId, int score, String comment, UserPrincipal caller) {
        if (score < 1 || score > 5) {
            throw new IllegalArgumentException("Satisfaction score must be between 1 and 5");
        }
        VisitRecord visit = visitRepo.findById(visitRecordId)
                .orElseThrow(() -> new RuntimeException("Visit record not found: " + visitRecordId));

        Long ownerUserId = resolveOwnerUserId(visit.getClientProfileId());
        requireTrackingAccess(caller, ownerUserId, PermissionDomain.VISITS, AccessLevel.WRITE);

        visit.setSatisfactionScore(score);
        visit.setSatisfactionComment(comment);
        return toVisitResponse(visitRepo.save(visit));
    }

    // ── History ──

    @Transactional
    public ClientHistoryResponse getClientHistory(Long userId, UserPrincipal caller) {
        // Composite read: the caller must have at least some tracking access. We use
        // PROFILE/READ as the minimum bar — most stringent gate covering the history view.
        requireTrackingAccess(caller, userId, PermissionDomain.PROFILE, AccessLevel.READ);

        // Resolve client name from shared schema
        String[] clientInfo = applicationSchemaExecutor.call(() -> {
            var user = userRepository.findById(userId).orElse(null);
            return user != null ? new String[]{user.getName(), user.getEmail()} : new String[]{"Client #" + userId, null};
        });

        ClientProfile profile = profileRepo.findByUserId(userId)
                .orElseGet(() -> {
                    ClientProfile p = new ClientProfile();
                    p.setUserId(userId);
                    return profileRepo.save(p);
                });

        List<VisitRecord> visits = visitRepo.findByClientProfileIdOrderByVisitDateDesc(profile.getId());
        List<VisitRecordResponse> visitResponses = visits.stream()
                .map(this::toVisitResponse)
                .toList();

        List<ClientReminder> reminders = reminderRepo.findByUserIdAndSentFalseOrderByRecommendedDateAsc(userId);
        List<ReminderResponse> reminderResponses = reminders.stream()
                .map(this::toReminderResponse)
                .toList();

        return new ClientHistoryResponse(
                clientInfo[0],
                clientInfo[1],
                toProfileResponse(profile),
                visitResponses,
                reminderResponses
        );
    }

    // ── RGPD: delete all photos ──

    @Transactional
    public void deleteAllPhotos(Long userId, UserPrincipal caller) {
        requireTrackingAccess(caller, userId, PermissionDomain.PHOTOS, AccessLevel.WRITE);

        ClientProfile profile = profileRepo.findByUserId(userId).orElse(null);
        if (profile == null) return;

        List<VisitRecord> visits = visitRepo.findByClientProfileIdOrderByVisitDateDesc(profile.getId());
        for (VisitRecord visit : visits) {
            List<VisitPhoto> photos = photoRepo.findByVisitRecordIdOrderByImageOrderAsc(visit.getId());
            for (VisitPhoto photo : photos) {
                deleteFileFromDisk(photo.getImagePath());
            }
            photoRepo.deleteByVisitRecordId(visit.getId());
        }
    }

    // ── Reminders ──

    @Transactional
    public ReminderResponse createReminder(Long userId, CreateReminderRequest request, UserPrincipal caller) {
        requireTrackingAccess(caller, userId, PermissionDomain.REMINDERS, AccessLevel.WRITE);

        ClientReminder reminder = new ClientReminder();
        reminder.setUserId(userId);
        reminder.setCareId(request.careId());
        reminder.setCareName(request.careName());
        reminder.setRecommendedDate(request.recommendedDate());
        reminder.setMessage(request.message());
        reminder.setCreatedBy(caller.getId());
        return toReminderResponse(reminderRepo.save(reminder));
    }

    // ── File operations ──

    private Long resolveOwnerUserId(Long clientProfileId) {
        if (clientProfileId == null) return null;
        return profileRepo.findById(clientProfileId)
                .map(ClientProfile::getUserId)
                .orElse(null);
    }

    private String saveFile(MultipartFile file, Long visitRecordId, String prefix) {
        try {
            String ext = "";
            String orig = file.getOriginalFilename();
            if (orig != null && orig.contains(".")) {
                ext = orig.substring(orig.lastIndexOf('.'));
            }
            String filename = prefix + "-" + UUID.randomUUID() + ext;
            String key = String.format("visits/%d/%s", visitRecordId, filename);
            String contentType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";
            storageBackend.save(key, file.getBytes(), contentType);
            return "uploads/" + key;
        } catch (IOException e) {
            throw new RuntimeException("Failed to save visit photo", e);
        }
    }

    private void deleteFileFromDisk(String filePath) {
        try {
            storageBackend.delete(filePath);
        } catch (RuntimeException e) {
            // best effort: log silently and continue
        }
    }

    // ── Helpers ──

    private String resolveUserName(Long userId) {
        if (userId == null) return null;
        try {
            return applicationSchemaExecutor.call(() ->
                    userRepository.findById(userId)
                            .map(com.luxpretty.app.users.domain.User::getName)
                            .orElse(null));
        } catch (Exception e) {
            return null;
        }
    }

    // ── Mappers ──

    private ClientProfileResponse toProfileResponse(ClientProfile p) {
        return new ClientProfileResponse(
                p.getId(), p.getUserId(), p.getNotes(), p.getSkinType(), p.getHairType(),
                p.getAllergies(), p.getPreferences(), p.isConsentPhotos(), p.isConsentPublicShare(),
                p.getConsentGivenAt(), p.getCreatedAt(),
                p.getUpdatedAt(), resolveUserName(p.getUpdatedBy())
        );
    }

    private VisitRecordResponse toVisitResponse(VisitRecord v) {
        List<VisitPhoto> photos = photoRepo.findByVisitRecordIdOrderByImageOrderAsc(v.getId());
        List<VisitPhotoResponse> photoResponses = photos.stream()
                .map(this::toPhotoResponse)
                .toList();
        return new VisitRecordResponse(
                v.getId(), v.getClientProfileId(), v.getBookingId(), v.getCareId(),
                v.getCareName(), v.getVisitDate(), v.getPractitionerNotes(), v.getProductsUsed(),
                v.getSatisfactionScore(), v.getSatisfactionComment(), v.getCreatedAt(),
                v.getUpdatedAt(), resolveUserName(v.getUpdatedBy()),
                photoResponses
        );
    }

    private VisitPhotoResponse toPhotoResponse(VisitPhoto p) {
        return new VisitPhotoResponse(
                p.getId(), p.getPhotoType(), toImageUrl(p), p.getImageOrder(),
                resolveUserName(p.getUploadedBy())
        );
    }

    private ReminderResponse toReminderResponse(ClientReminder r) {
        return new ReminderResponse(
                r.getId(), r.getUserId(), r.getCareId(), r.getCareName(),
                r.getRecommendedDate(), r.getMessage(), r.isSent(), r.getCreatedAt(),
                resolveUserName(r.getCreatedBy())
        );
    }

    private String toImageUrl(VisitPhoto photo) {
        String path = photo.getImagePath();
        if (path.startsWith("http")) return path;
        String filename = Paths.get(path).getFileName().toString();
        return "/api/images/visits/" + photo.getVisitRecordId() + "/" + filename;
    }
}
