package com.prettyface.app.tracking.app;

import com.prettyface.app.multitenancy.ApplicationSchemaExecutor;
import com.prettyface.app.multitenancy.TenantContext;
import com.prettyface.app.tracking.domain.SalonClient;
import com.prettyface.app.tracking.repo.SalonClientRepository;
import com.prettyface.app.tracking.web.dto.CreateSalonClientRequest;
import com.prettyface.app.tracking.web.dto.SalonClientResponse;
import com.prettyface.app.users.repo.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class SalonClientService {

    private final SalonClientRepository salonClientRepo;
    private final UserRepository userRepository;
    private final ApplicationSchemaExecutor applicationSchemaExecutor;

    public SalonClientService(SalonClientRepository salonClientRepo,
                               UserRepository userRepository,
                               ApplicationSchemaExecutor applicationSchemaExecutor) {
        this.salonClientRepo = salonClientRepo;
        this.userRepository = userRepository;
        this.applicationSchemaExecutor = applicationSchemaExecutor;
    }

    @Transactional
    public SalonClientResponse create(CreateSalonClientRequest request, Long creatorId) {
        SalonClient client = new SalonClient();
        client.setName(request.name());
        client.setPhone(request.phone());
        client.setEmail(request.email());
        client.setDateOfBirth(request.dateOfBirth());
        client.setNotes(request.notes());
        client.setManual(true);
        client.setCreatedBy(creatorId);
        return toResponse(salonClientRepo.save(client));
    }

    @Transactional(readOnly = true)
    public List<SalonClientResponse> search(String query) {
        return salonClientRepo.search(query).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<SalonClientResponse> recent() {
        return salonClientRepo.findTop10ByOrderByCreatedAtDesc().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public SalonClientResponse getById(Long id) {
        TenantContext.requireActive();
        SalonClient client = salonClientRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Salon client not found"));
        return toResponse(client);
    }

    @Transactional
    public SalonClientResponse linkToUser(Long salonClientId, Long userId) {
        SalonClient client = salonClientRepo.findById(salonClientId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Salon client not found"));
        client.setUserId(userId);
        return toResponse(salonClientRepo.save(client));
    }

    @Transactional
    public SalonClient getOrCreateForUser(Long userId, String name, String phone) {
        return salonClientRepo.findByUserId(userId)
                .orElseGet(() -> {
                    SalonClient sc = new SalonClient();
                    sc.setUserId(userId);
                    sc.setName(name);
                    sc.setPhone(phone);
                    sc.setManual(false);
                    return salonClientRepo.save(sc);
                });
    }

    @Transactional(readOnly = true)
    public List<SalonClient> findAllByIds(java.util.Collection<Long> ids) {
        return salonClientRepo.findAllById(ids);
    }

    private SalonClientResponse toResponse(SalonClient c) {
        String createdByName = null;
        if (c.getCreatedBy() != null) {
            createdByName = applicationSchemaExecutor.call(() ->
                    userRepository.findById(c.getCreatedBy())
                            .map(u -> u.getName())
                            .orElse(null));
        }
        return new SalonClientResponse(
                c.getId(), c.getName(), c.getPhone(), c.getEmail(),
                c.getDateOfBirth(), c.getNotes(), c.getUserId(), c.isManual(),
                c.getCreatedAt(), createdByName
        );
    }
}
