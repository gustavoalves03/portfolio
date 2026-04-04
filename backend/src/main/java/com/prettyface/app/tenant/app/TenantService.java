package com.prettyface.app.tenant.app;

import com.prettyface.app.common.storage.FileStorageService;
import com.prettyface.app.tenant.domain.Tenant;
import com.prettyface.app.tenant.repo.TenantRepository;
import com.prettyface.app.tenant.web.dto.TenantResponse;
import com.prettyface.app.tenant.web.dto.UpdateTenantRequest;
import com.prettyface.app.tenant.web.mapper.TenantMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class TenantService {

    private static final Pattern ALLOWED_HTML = Pattern.compile(
            "<(?!/?(?:p|strong|em|ul|ol|li|a|br)\\b)[^>]+>",
            Pattern.CASE_INSENSITIVE
    );

    private final TenantRepository tenantRepository;
    private final FileStorageService fileStorageService;

    public TenantService(TenantRepository tenantRepository, FileStorageService fileStorageService) {
        this.tenantRepository = tenantRepository;
        this.fileStorageService = fileStorageService;
    }

    public Optional<Tenant> findByOwnerId(Long ownerId) {
        return tenantRepository.findByOwnerId(ownerId);
    }

    public Optional<Tenant> findBySlug(String slug) {
        return tenantRepository.findBySlug(slug);
    }

    @Transactional
    public TenantResponse updateProfile(Long ownerId, UpdateTenantRequest request) {
        Tenant tenant = tenantRepository.findByOwnerId(ownerId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found for owner: " + ownerId));

        tenant.setName(request.name());

        // Sanitize and set description
        if (request.description() != null) {
            tenant.setDescription(sanitizeHtml(request.description()));
        } else {
            tenant.setDescription(null);
        }

        // Handle logo: null=no change, ""=remove, base64=new logo
        if (request.logo() != null) {
            if (request.logo().isEmpty()) {
                // Remove logo
                if (tenant.getLogoPath() != null) {
                    fileStorageService.deleteFile(tenant.getLogoPath());
                    tenant.setLogoPath(null);
                }
            } else {
                // New logo upload — delete old one first
                if (tenant.getLogoPath() != null) {
                    fileStorageService.deleteFile(tenant.getLogoPath());
                }
                String logoPath = fileStorageService.saveBase64Image(request.logo(), "tenant", tenant.getId());
                tenant.setLogoPath(logoPath);
            }
        }

        // Handle hero image: null=no change, ""=remove, base64=new
        if (request.heroImage() != null) {
            if (request.heroImage().isEmpty()) {
                if (tenant.getHeroImagePath() != null) {
                    fileStorageService.deleteFile(tenant.getHeroImagePath());
                    tenant.setHeroImagePath(null);
                }
            } else {
                if (tenant.getHeroImagePath() != null) {
                    fileStorageService.deleteFile(tenant.getHeroImagePath());
                }
                String heroPath = fileStorageService.saveBase64Image(request.heroImage(), "tenant", tenant.getId());
                tenant.setHeroImagePath(heroPath);
            }
        }

        // Business info
        tenant.setAddressStreet(request.addressStreet());
        tenant.setAddressPostalCode(request.addressPostalCode());
        tenant.setAddressCity(request.addressCity());
        tenant.setAddressCountry(request.addressCountry());
        tenant.setPhone(request.phone());
        tenant.setContactEmail(request.contactEmail());
        tenant.setSiret(request.siret());

        // Feature flags
        if (request.employeesEnabled() != null) {
            tenant.setEmployeesEnabled(request.employeesEnabled());
        }
        if (request.annualLeaveDays() != null) {
            tenant.setAnnualLeaveDays(request.annualLeaveDays());
        }

        Tenant saved = tenantRepository.save(tenant);
        return TenantMapper.toResponse(saved);
    }

    public TenantResponse getProfile(Long ownerId) {
        Tenant tenant = tenantRepository.findByOwnerId(ownerId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found for owner: " + ownerId));
        return TenantMapper.toResponse(tenant);
    }

    static String sanitizeHtml(String html) {
        if (html == null) return null;
        // Remove all tags except allowed ones
        return ALLOWED_HTML.matcher(html).replaceAll("");
    }
}
