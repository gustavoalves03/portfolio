package com.prettyface.app.tenant.app;

import com.prettyface.app.common.storage.FileStorageService;
import com.prettyface.app.tenant.domain.Tenant;
import com.prettyface.app.tenant.repo.TenantRepository;
import com.prettyface.app.tenant.web.dto.TenantResponse;
import com.prettyface.app.tenant.web.dto.UpdateTenantRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TenantServiceTests {

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private FileStorageService fileStorageService;

    @InjectMocks
    private TenantService tenantService;

    private Tenant tenant;

    @BeforeEach
    void setUp() {
        tenant = Tenant.builder()
                .id(1L)
                .name("My Salon")
                .slug("my-salon")
                .ownerId(42L)
                .build();
    }

    @Test
    void getProfile_returnsTenantResponse() {
        when(tenantRepository.findByOwnerId(42L)).thenReturn(Optional.of(tenant));

        TenantResponse response = tenantService.getProfile(42L);

        assertThat(response.name()).isEqualTo("My Salon");
        assertThat(response.slug()).isEqualTo("my-salon");
    }

    @Test
    void getProfile_throwsWhenNotFound() {
        when(tenantRepository.findByOwnerId(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tenantService.getProfile(99L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateProfile_updatesNameAndDescription() {
        when(tenantRepository.findByOwnerId(42L)).thenReturn(Optional.of(tenant));
        when(tenantRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateTenantRequest request = new UpdateTenantRequest("New Name", "<p>Hello</p>", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        TenantResponse response = tenantService.updateProfile(42L, request);

        assertThat(response.name()).isEqualTo("New Name");
        assertThat(response.description()).isEqualTo("<p>Hello</p>");
    }

    @Test
    void updateProfile_removesLogo() {
        tenant.setLogoPath("uploads/tenant/1/old.png");
        when(tenantRepository.findByOwnerId(42L)).thenReturn(Optional.of(tenant));
        when(tenantRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateTenantRequest request = new UpdateTenantRequest("My Salon", null, "", null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        tenantService.updateProfile(42L, request);

        verify(fileStorageService).deleteFile("uploads/tenant/1/old.png");
        assertThat(tenant.getLogoPath()).isNull();
    }

    @Test
    void updateProfile_replacesLogo() {
        tenant.setLogoPath("uploads/tenant/1/old.png");
        when(tenantRepository.findByOwnerId(42L)).thenReturn(Optional.of(tenant));
        when(tenantRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(fileStorageService.saveBase64Image(anyString(), eq("tenant"), eq(1L)))
                .thenReturn("uploads/tenant/1/new.png");

        UpdateTenantRequest request = new UpdateTenantRequest("My Salon", null, "data:image/png;base64,abc123", null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        tenantService.updateProfile(42L, request);

        verify(fileStorageService).deleteFile("uploads/tenant/1/old.png");
        assertThat(tenant.getLogoPath()).isEqualTo("uploads/tenant/1/new.png");
    }

    @Test
    void sanitizeHtml_removesDisallowedTags() {
        String input = "<p>Hello</p><script>alert('xss')</script><strong>World</strong>";
        String result = TenantService.sanitizeHtml(input);

        assertThat(result).contains("<p>Hello</p>");
        assertThat(result).contains("<strong>World</strong>");
        assertThat(result).doesNotContain("<script>");
    }

    @Test
    void sanitizeHtml_preservesAllowedTags() {
        String input = "<p>Text</p><ul><li>Item</li></ul><a href=\"#\">Link</a><br><em>Italic</em><ol><li>Ordered</li></ol>";
        String result = TenantService.sanitizeHtml(input);

        assertThat(result).isEqualTo(input);
    }
}
