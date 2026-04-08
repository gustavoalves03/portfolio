package com.prettyface.app.tracking.app;

import com.prettyface.app.multitenancy.ApplicationSchemaExecutor;
import com.prettyface.app.tracking.domain.*;
import com.prettyface.app.tracking.repo.*;
import com.prettyface.app.tracking.web.dto.*;
import com.prettyface.app.users.repo.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

@Service
public class TrackingService {

    private static final String UPLOAD_BASE = "uploads/visits";

    private final ClientProfileRepository profileRepo;
    private final VisitRecordRepository visitRepo;
    private final VisitPhotoRepository photoRepo;
    private final ClientReminderRepository reminderRepo;
    private final UserRepository userRepository;
    private final ApplicationSchemaExecutor applicationSchemaExecutor;

    public TrackingService(ClientProfileRepository profileRepo,
                           VisitRecordRepository visitRepo,
                           VisitPhotoRepository photoRepo,
                           ClientReminderRepository reminderRepo,
                           UserRepository userRepository,
                           ApplicationSchemaExecutor applicationSchemaExecutor) {
        this.profileRepo = profileRepo;
        this.visitRepo = visitRepo;
        this.photoRepo = photoRepo;
        this.reminderRepo = reminderRepo;
        this.userRepository = userRepository;
        this.applicationSchemaExecutor = applicationSchemaExecutor;
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
    public ClientProfileResponse updateProfile(Long userId, UpdateClientProfileRequest request, Long modifierId) {
        ClientProfile profile = profileRepo.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Client profile not found for userId: " + userId));
        if (request.notes() != null) profile.setNotes(request.notes());
        if (request.skinType() != null) profile.setSkinType(request.skinType());
        if (request.hairType() != null) profile.setHairType(request.hairType());
        if (request.allergies() != null) profile.setAllergies(request.allergies());
        if (request.preferences() != null) profile.setPreferences(request.preferences());
        profile.setUpdatedAt(java.time.LocalDateTime.now());
        profile.setUpdatedBy(modifierId);
        return toProfileResponse(profileRepo.save(profile));
    }

    @Transactional
    public ClientProfileResponse updateConsent(Long userId, boolean consentPhotos, boolean consentPublicShare) {
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
    public VisitRecordResponse createVisitRecord(Long userId, CreateVisitRecordRequest request, Long creatorId) {
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
        visit.setUpdatedBy(creatorId);
        visit.setUpdatedAt(java.time.LocalDateTime.now());
        visit = visitRepo.save(visit);

        return toVisitResponse(visit);
    }

    @Transactional
    public VisitPhotoResponse addVisitPhoto(Long visitRecordId, MultipartFile photo, PhotoType type, Long uploaderId) {
        VisitRecord visit = visitRepo.findById(visitRecordId)
                .orElseThrow(() -> new RuntimeException("Visit record not found: " + visitRecordId));

        List<VisitPhoto> existing = photoRepo.findByVisitRecordIdOrderByImageOrderAsc(visitRecordId);
        int nextOrder = existing.isEmpty() ? 0 : existing.getLast().getImageOrder() + 1;

        String savedPath = saveFile(photo, visitRecordId, type.name().toLowerCase());

        VisitPhoto vp = new VisitPhoto();
        vp.setVisitRecordId(visitRecordId);
        vp.setPhotoType(type);
        vp.setImagePath(savedPath);
        vp.setImageOrder(nextOrder);
        vp.setUploadedBy(uploaderId);
        vp = photoRepo.save(vp);

        return toPhotoResponse(vp);
    }

    @Transactional
    public VisitRecordResponse rateVisit(Long visitRecordId, int score, String comment) {
        if (score < 1 || score > 5) {
            throw new IllegalArgumentException("Satisfaction score must be between 1 and 5");
        }
        VisitRecord visit = visitRepo.findById(visitRecordId)
                .orElseThrow(() -> new RuntimeException("Visit record not found: " + visitRecordId));
        visit.setSatisfactionScore(score);
        visit.setSatisfactionComment(comment);
        return toVisitResponse(visitRepo.save(visit));
    }

    // ── History ──

    @Transactional
    public ClientHistoryResponse getClientHistory(Long userId) {
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
    public void deleteAllPhotos(Long userId) {
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
    public ReminderResponse createReminder(Long userId, CreateReminderRequest request, Long creatorId) {
        ClientReminder reminder = new ClientReminder();
        reminder.setUserId(userId);
        reminder.setCareId(request.careId());
        reminder.setCareName(request.careName());
        reminder.setRecommendedDate(request.recommendedDate());
        reminder.setMessage(request.message());
        reminder.setCreatedBy(creatorId);
        return toReminderResponse(reminderRepo.save(reminder));
    }

    // ── File operations ──

    private String saveFile(MultipartFile file, Long visitRecordId, String prefix) {
        try {
            String ext = "";
            String orig = file.getOriginalFilename();
            if (orig != null && orig.contains(".")) {
                ext = orig.substring(orig.lastIndexOf('.'));
            }
            String filename = prefix + "-" + UUID.randomUUID() + ext;
            Path dir = Paths.get(UPLOAD_BASE, visitRecordId.toString());
            Files.createDirectories(dir);
            Files.copy(file.getInputStream(), dir.resolve(filename));
            return dir.resolve(filename).toString();
        } catch (IOException e) {
            throw new RuntimeException("Failed to save visit photo", e);
        }
    }

    private void deleteFileFromDisk(String filePath) {
        try {
            Path path = Paths.get(filePath);
            if (Files.exists(path)) {
                Files.delete(path);
            }
        } catch (IOException e) {
            // Log but don't fail — best effort deletion
        }
    }

    // ── Helpers ──

    private String resolveUserName(Long userId) {
        if (userId == null) return null;
        try {
            return applicationSchemaExecutor.call(() ->
                    userRepository.findById(userId)
                            .map(com.prettyface.app.users.domain.User::getName)
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
