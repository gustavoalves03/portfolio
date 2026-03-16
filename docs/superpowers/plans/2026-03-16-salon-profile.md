# Salon Profile Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Allow beauty professionals to configure their salon profile (name, logo, description) and display it on a public storefront page with cares grouped by category.

**Architecture:** Extend the existing `Tenant` entity with profile fields. Create a `TenantController` under `/api/pro/tenant` for authenticated profile management, and a public endpoint at `/api/salon/{slug}`. Refactor `FileStorageService` to support multiple storage domains. Add `maxImages` input to `ImageManager`. Frontend uses NgRx SignalStore pattern.

**Tech Stack:** Angular 20 (standalone, signals, zoneless), Spring Boot 3.5.4, Oracle DB, NgRx SignalStore, Angular Material, ngx-quill, Transloco i18n

---

## Chunk 1: Backend — Data Model & FileStorage Refactoring

### Task 1: Extend Tenant Entity

**Files:**
- Modify: `backend/src/main/java/com/fleurdecoquillage/app/tenant/domain/Tenant.java`

- [ ] **Step 1: Add new fields to Tenant entity**

Add `description`, `logoPath`, and `updatedAt` fields with JPA annotations. Add `@PreUpdate` callback.

```java
// Add these fields after the existing 'createdAt' field:

@Column(name = "description", columnDefinition = "CLOB")
private String description;

@Column(name = "logo_path", length = 500)
private String logoPath;

@Column(name = "updated_at")
private LocalDateTime updatedAt;

// Add @PreUpdate method after existing @PrePersist:
@PreUpdate
protected void onUpdate() {
    updatedAt = LocalDateTime.now();
}
```

- [ ] **Step 2: Verify the application starts**

Run: `cd backend && mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/fleurdecoquillage/app/tenant/domain/Tenant.java
git commit -m "feat: extend Tenant entity with description, logoPath, updatedAt"
```

---

### Task 2: Refactor FileStorageService for Multiple Domains

**Files:**
- Modify: `backend/src/main/java/com/fleurdecoquillage/app/common/storage/FileStorageService.java`

- [ ] **Step 1: Add generic `saveBase64Image` method with domain parameter**

Add a new method that accepts a `domain` and `entityId`. Keep the old method delegating to the new one for backward compatibility.

```java
/**
 * Save Base64 image data to disk under a specific domain folder.
 * @param base64Data Base64 string with data URL prefix (data:image/png;base64,...)
 * @param domain Storage domain folder (e.g., "cares", "tenant")
 * @param entityId Entity ID for folder organization
 * @return Relative file path
 */
public String saveBase64Image(String base64Data, String domain, Long entityId) {
    try {
        // Extract MIME type and Base64 data
        String[] parts = base64Data.split(",");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid Base64 data format");
        }

        String mimeType = extractMimeType(parts[0]);
        String extension = getExtensionFromMimeType(mimeType);

        if (!extension.equals("png") && !extension.equals("jpg") && !extension.equals("jpeg")) {
            throw new IllegalArgumentException("Only PNG and JPG images are allowed");
        }

        byte[] imageBytes = Base64.getDecoder().decode(parts[1]);

        if (imageBytes.length > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File size exceeds 5MB limit");
        }

        String filename = UUID.randomUUID().toString() + "." + extension;
        Path dir = Paths.get(uploadDir, domain, entityId.toString());
        Files.createDirectories(dir);

        Path filePath = dir.resolve(filename);
        Files.write(filePath, imageBytes);

        return String.format("uploads/%s/%d/%s", domain, entityId, filename);

    } catch (IOException e) {
        throw new RuntimeException("Failed to save image: " + e.getMessage(), e);
    }
}
```

- [ ] **Step 2: Update old `saveBase64Image(String, Long)` to delegate**

Replace the body of the existing 2-param method:

```java
public String saveBase64Image(String base64Data, Long careId) {
    return saveBase64Image(base64Data, "cares", careId);
}
```

Remove all the verbose logging from the old method (the logic is now in the new method).

- [ ] **Step 3: Verify compilation**

Run: `cd backend && mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/fleurdecoquillage/app/common/storage/FileStorageService.java
git commit -m "refactor: add domain parameter to FileStorageService for multi-domain uploads"
```

---

### Task 3: Create Tenant DTOs

**Files:**
- Create: `backend/src/main/java/com/fleurdecoquillage/app/tenant/web/dto/UpdateTenantRequest.java`
- Create: `backend/src/main/java/com/fleurdecoquillage/app/tenant/web/dto/TenantResponse.java`
- Create: `backend/src/main/java/com/fleurdecoquillage/app/tenant/web/dto/PublicSalonResponse.java`
- Create: `backend/src/main/java/com/fleurdecoquillage/app/tenant/web/dto/PublicCategoryDto.java`
- Create: `backend/src/main/java/com/fleurdecoquillage/app/tenant/web/dto/PublicCareDto.java`

- [ ] **Step 1: Create UpdateTenantRequest**

```java
package com.fleurdecoquillage.app.tenant.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateTenantRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 50000) String description, // HTML can be ~5x text content; text limit enforced in frontend (10000 chars)
        String logo // base64 nullable: null=no change, ""=remove
) {}
```

- [ ] **Step 2: Create TenantResponse**

```java
package com.fleurdecoquillage.app.tenant.web.dto;

import java.time.LocalDateTime;

public record TenantResponse(
        Long id,
        String name,
        String slug,
        String description,
        String logoUrl,
        LocalDateTime updatedAt
) {}
```

- [ ] **Step 3: Create PublicCareDto**

```java
package com.fleurdecoquillage.app.tenant.web.dto;

import java.util.List;

public record PublicCareDto(
        String name,
        Integer duration,
        Integer price,
        List<String> imageUrls
) {}
```

- [ ] **Step 4: Create PublicCategoryDto**

```java
package com.fleurdecoquillage.app.tenant.web.dto;

import java.util.List;

public record PublicCategoryDto(
        String name,
        List<PublicCareDto> cares
) {}
```

- [ ] **Step 5: Create PublicSalonResponse**

```java
package com.fleurdecoquillage.app.tenant.web.dto;

import java.util.List;

public record PublicSalonResponse(
        String name,
        String slug,
        String description,
        String logoUrl,
        List<PublicCategoryDto> categories
) {}
```

- [ ] **Step 6: Verify compilation**

Run: `cd backend && mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/fleurdecoquillage/app/tenant/web/
git commit -m "feat: add Tenant DTOs (UpdateTenantRequest, TenantResponse, PublicSalonResponse)"
```

---

### Task 4: Create Tenant Mapper

**Files:**
- Create: `backend/src/main/java/com/fleurdecoquillage/app/tenant/web/mapper/TenantMapper.java`

- [ ] **Step 1: Create TenantMapper with static methods**

```java
package com.fleurdecoquillage.app.tenant.web.mapper;

import com.fleurdecoquillage.app.care.domain.Care;
import com.fleurdecoquillage.app.care.domain.CareImage;
import com.fleurdecoquillage.app.care.domain.CareStatus;
import com.fleurdecoquillage.app.category.domain.Category;
import com.fleurdecoquillage.app.tenant.domain.Tenant;
import com.fleurdecoquillage.app.tenant.web.dto.*;

import java.util.Comparator;
import java.util.List;

public class TenantMapper {

    private TenantMapper() {}

    public static TenantResponse toResponse(Tenant tenant) {
        String logoUrl = tenant.getLogoPath() != null
                ? "/api/images/tenant/" + tenant.getId() + "/" + extractFilename(tenant.getLogoPath())
                : null;

        return new TenantResponse(
                tenant.getId(),
                tenant.getName(),
                tenant.getSlug(),
                tenant.getDescription(),
                logoUrl,
                tenant.getUpdatedAt()
        );
    }

    public static PublicSalonResponse toPublicResponse(Tenant tenant, List<Category> categories) {
        String logoUrl = tenant.getLogoPath() != null
                ? "/api/images/tenant/" + tenant.getId() + "/" + extractFilename(tenant.getLogoPath())
                : null;

        List<PublicCategoryDto> categoryDtos = categories.stream()
                .filter(cat -> cat.getCares() != null && cat.getCares().stream()
                        .anyMatch(c -> c.getStatus() == CareStatus.ACTIVE))
                .sorted(Comparator.comparing(Category::getName))
                .map(TenantMapper::toCategoryDto)
                .toList();

        return new PublicSalonResponse(
                tenant.getName(),
                tenant.getSlug(),
                tenant.getDescription(),
                logoUrl,
                categoryDtos
        );
    }

    private static PublicCategoryDto toCategoryDto(Category category) {
        List<PublicCareDto> careDtos = category.getCares().stream()
                .filter(c -> c.getStatus() == CareStatus.ACTIVE)
                .sorted(Comparator.comparing(Care::getName))
                .map(TenantMapper::toCareDto)
                .toList();

        return new PublicCategoryDto(category.getName(), careDtos);
    }

    private static PublicCareDto toCareDto(Care care) {
        List<String> imageUrls = care.getImages() != null
                ? care.getImages().stream()
                    .sorted(Comparator.comparingInt(CareImage::getImageOrder))
                    .map(img -> "/api/images/cares/" + care.getId() + "/" + img.getFilename())
                    .toList()
                : List.of();

        return new PublicCareDto(care.getName(), care.getDuration(), care.getPrice(), imageUrls);
    }

    private static String extractFilename(String filePath) {
        int lastSlash = filePath.lastIndexOf('/');
        return lastSlash >= 0 ? filePath.substring(lastSlash + 1) : filePath;
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `cd backend && mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/fleurdecoquillage/app/tenant/web/mapper/TenantMapper.java
git commit -m "feat: add TenantMapper for entity-to-DTO conversion"
```

---

### Task 4b: Add Tenant Image Serving Endpoint

**Files:**
- Modify: `backend/src/main/java/com/fleurdecoquillage/app/common/storage/FileController.java`

- [ ] **Step 1: Add tenant image serving endpoint**

Add a new endpoint to `FileController` after the existing cares endpoint:

```java
@GetMapping("/tenant/{tenantId}/{filename}")
public ResponseEntity<Resource> serveTenantImage(
        @PathVariable Long tenantId,
        @PathVariable String filename
) {
    try {
        String filePath = String.format("uploads/tenant/%d/%s", tenantId, filename);
        Resource resource = fileStorageService.loadFile(filePath);
        String contentType = determineContentType(filename);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CACHE_CONTROL, "max-age=31536000")
                .body(resource);
    } catch (Exception e) {
        return ResponseEntity.notFound().build();
    }
}
```

The existing SecurityConfig already has `permitAll()` for `GET /api/images/**`, which covers this new endpoint.

- [ ] **Step 2: Verify compilation**

Run: `cd backend && mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/fleurdecoquillage/app/common/storage/FileController.java
git commit -m "feat: add tenant image serving endpoint at /api/images/tenant/{id}/{filename}"
```

---

## Chunk 2: Backend — Service, Controller & Security

### Task 5: Extend TenantService with Update Logic

**Files:**
- Modify: `backend/src/main/java/com/fleurdecoquillage/app/tenant/app/TenantService.java`

- [ ] **Step 1: Add update method and HTML sanitization**

```java
package com.fleurdecoquillage.app.tenant.app;

import com.fleurdecoquillage.app.common.storage.FileStorageService;
import com.fleurdecoquillage.app.tenant.domain.Tenant;
import com.fleurdecoquillage.app.tenant.repo.TenantRepository;
import com.fleurdecoquillage.app.tenant.web.dto.TenantResponse;
import com.fleurdecoquillage.app.tenant.web.dto.UpdateTenantRequest;
import com.fleurdecoquillage.app.tenant.web.mapper.TenantMapper;
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
```

- [ ] **Step 2: Verify compilation**

Run: `cd backend && mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/fleurdecoquillage/app/tenant/app/TenantService.java
git commit -m "feat: add profile update logic to TenantService with HTML sanitization"
```

---

### Task 6: Create TenantController

**Files:**
- Create: `backend/src/main/java/com/fleurdecoquillage/app/tenant/web/TenantController.java`

- [ ] **Step 1: Create controller with GET and PUT endpoints**

```java
package com.fleurdecoquillage.app.tenant.web;

import com.fleurdecoquillage.app.auth.UserPrincipal;
import com.fleurdecoquillage.app.tenant.web.dto.TenantResponse;
import com.fleurdecoquillage.app.tenant.web.dto.UpdateTenantRequest;
import com.fleurdecoquillage.app.tenant.app.TenantService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/pro/tenant")
public class TenantController {

    private final TenantService tenantService;

    public TenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @GetMapping
    public TenantResponse getProfile(@AuthenticationPrincipal UserPrincipal principal) {
        return tenantService.getProfile(principal.getId());
    }

    @PutMapping
    public TenantResponse updateProfile(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody @Valid UpdateTenantRequest request) {
        return tenantService.updateProfile(principal.getId(), request);
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `cd backend && mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/fleurdecoquillage/app/tenant/web/TenantController.java
git commit -m "feat: add TenantController with GET/PUT /api/pro/tenant endpoints"
```

---

### Task 7: Create Public Salon Endpoint

**Files:**
- Create: `backend/src/main/java/com/fleurdecoquillage/app/tenant/web/PublicSalonController.java`
- Modify: `backend/src/main/java/com/fleurdecoquillage/app/config/SecurityConfig.java`

- [ ] **Step 1: Create PublicSalonController**

```java
package com.fleurdecoquillage.app.tenant.web;

import com.fleurdecoquillage.app.category.domain.Category;
import com.fleurdecoquillage.app.category.repo.CategoryRepository;
import com.fleurdecoquillage.app.multitenancy.TenantContext;
import com.fleurdecoquillage.app.tenant.app.TenantService;
import com.fleurdecoquillage.app.tenant.web.dto.PublicSalonResponse;
import com.fleurdecoquillage.app.tenant.web.mapper.TenantMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/salon")
public class PublicSalonController {

    private final TenantService tenantService;
    private final CategoryRepository categoryRepository;

    public PublicSalonController(TenantService tenantService, CategoryRepository categoryRepository) {
        this.tenantService = tenantService;
        this.categoryRepository = categoryRepository;
    }

    @GetMapping("/{slug}")
    @Transactional(readOnly = true)
    public ResponseEntity<PublicSalonResponse> getSalon(@PathVariable String slug) {
        return tenantService.findBySlug(slug)
                .map(tenant -> {
                    // Set tenant context to query the correct schema
                    TenantContext.setCurrentTenant(tenant.getSlug());
                    try {
                        List<Category> categories = categoryRepository.findAll();
                        return ResponseEntity.ok(TenantMapper.toPublicResponse(tenant, categories));
                    } finally {
                        TenantContext.clear();
                    }
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
```

- [ ] **Step 2: Add `/api/salon/**` to SecurityConfig permitAll**

In `SecurityConfig.java`, add this line after the existing `permitAll()` rules for `/api/care/**` (around line 141):

```java
.requestMatchers(HttpMethod.GET, "/api/salon/**").permitAll() // Public salon storefront
```

- [ ] **Step 3: Verify compilation**

Run: `cd backend && mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/fleurdecoquillage/app/tenant/web/PublicSalonController.java
git add backend/src/main/java/com/fleurdecoquillage/app/config/SecurityConfig.java
git commit -m "feat: add public salon endpoint GET /api/salon/{slug} with permitAll"
```

---

### Task 8: Backend Tests

**Files:**
- Create: `backend/src/test/java/com/fleurdecoquillage/app/tenant/app/TenantServiceTests.java`
- Create: `backend/src/test/java/com/fleurdecoquillage/app/tenant/web/TenantControllerTests.java`

- [ ] **Step 1: Create TenantServiceTests**

```java
package com.fleurdecoquillage.app.tenant.app;

import com.fleurdecoquillage.app.common.storage.FileStorageService;
import com.fleurdecoquillage.app.tenant.domain.Tenant;
import com.fleurdecoquillage.app.tenant.repo.TenantRepository;
import com.fleurdecoquillage.app.tenant.web.dto.TenantResponse;
import com.fleurdecoquillage.app.tenant.web.dto.UpdateTenantRequest;
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

        UpdateTenantRequest request = new UpdateTenantRequest("New Name", "<p>Hello</p>", null);
        TenantResponse response = tenantService.updateProfile(42L, request);

        assertThat(response.name()).isEqualTo("New Name");
        assertThat(response.description()).isEqualTo("<p>Hello</p>");
    }

    @Test
    void updateProfile_removesLogo() {
        tenant.setLogoPath("uploads/tenant/1/old.png");
        when(tenantRepository.findByOwnerId(42L)).thenReturn(Optional.of(tenant));
        when(tenantRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateTenantRequest request = new UpdateTenantRequest("My Salon", null, "");
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

        UpdateTenantRequest request = new UpdateTenantRequest("My Salon", null, "data:image/png;base64,abc123");
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
```

- [ ] **Step 2: Run tests to verify they pass**

Run: `cd backend && mvn test -pl . -Dtest="com.fleurdecoquillage.app.tenant.app.TenantServiceTests" -q`
Expected: All tests pass

- [ ] **Step 3: Commit**

```bash
git add backend/src/test/java/com/fleurdecoquillage/app/tenant/
git commit -m "test: add TenantService unit tests for profile management"
```

---

## Chunk 3: Frontend — ImageManager Refactoring & Salon Profile Feature

### Task 9: Add maxImages Input to ImageManager

**Files:**
- Modify: `frontend/src/app/shared/uis/image-manager/image-manager.component.ts`
- Modify: `frontend/src/app/shared/uis/image-manager/image-manager.component.html`

- [ ] **Step 1: Add maxImages input and replace hardcoded constant**

In `image-manager.component.ts`:

Replace:
```typescript
import { CareImage } from '../../../features/cares/models/cares.model';

const MAX_IMAGES = 5;
```

With:
```typescript
const DEFAULT_MAX_IMAGES = 5;
```

Add `maxImages` input and remove the `CareImage` import. Define a local `ManagedImage` interface in the same file:

```typescript
export interface ManagedImage {
  id?: string;
  url: string;
  name: string;
  order: number;
  file?: File;
  base64Data?: string;
}
```

Update the component class:
- Add input: `maxImages = input<number>(DEFAULT_MAX_IMAGES);`
- Change `images` input type: `images = input<ManagedImage[]>([]);`
- Change `imagesChange` output type: `imagesChange = output<ManagedImage[]>();`
- Change `localImages` signal type: `localImages = signal<ManagedImage[]>([]);`
- Replace all `MAX_IMAGES` references with `this.maxImages()`
- Replace all `CareImage` type references with `ManagedImage`

The `readonly MAX_IMAGES = MAX_IMAGES;` public field becomes a computed getter in the template that uses `maxImages()`.

- [ ] **Step 2: Update image-manager.component.html**

Replace template references to `MAX_IMAGES` with `maxImages()`:

Replace:
```html
<p class="hint">Vous pouvez ajouter jusqu'à {{ MAX_IMAGES }} images (PNG, JPG, max 5MB)</p>
```
With:
```html
<p class="hint">Vous pouvez ajouter jusqu'à {{ maxImages() }} images (PNG, JPG, max 5MB)</p>
```

Replace:
```html
Limite atteinte ({{ MAX_IMAGES }} images max)
```
With:
```html
Limite atteinte ({{ maxImages() }} images max)
```

Also: when `maxImages() === 1`, hide drag handles and name inputs. Wrap drag-related elements:
```html
@if (maxImages() > 1) {
  <!-- drag handle -->
}
```

And wrap name input similarly:
```html
@if (maxImages() > 1) {
  <!-- name input mat-form-field -->
}
```

Also remove `multiple` from file input when `maxImages() === 1`:
```html
<input
  #fileInput
  type="file"
  accept="image/png,image/jpeg,image/jpg"
  [multiple]="maxImages() > 1"
  (change)="onFileSelected($event)"
  class="file-input"
/>
```

- [ ] **Step 3: Update CareImage to extend ManagedImage**

In `frontend/src/app/features/cares/models/cares.model.ts`, update the `CareImage` interface:

Replace:
```typescript
export interface CareImage {
  id?: string;
  url: string;
  name: string;
  order: number;
  file?: File;
  base64Data?: string;
}
```

With:
```typescript
import { ManagedImage } from '../../../shared/uis/image-manager/image-manager.component';

export interface CareImage extends ManagedImage {}
```

- [ ] **Step 4: Verify frontend compiles**

Run: `cd frontend && npx ng build --configuration=development 2>&1 | tail -5`
Expected: Build succeeds

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/shared/uis/image-manager/
git add frontend/src/app/features/cares/models/cares.model.ts
git commit -m "refactor: add maxImages input to ImageManager, extract ManagedImage interface"
```

---

### Task 10: Install ngx-quill

**Files:**
- Modify: `frontend/package.json`

- [ ] **Step 1: Install ngx-quill and quill**

Run: `cd frontend && npm install ngx-quill quill`

- [ ] **Step 2: Verify the build**

Run: `cd frontend && npx ng build --configuration=development 2>&1 | tail -5`
Expected: Build succeeds

- [ ] **Step 3: Commit**

```bash
git add frontend/package.json frontend/package-lock.json
git commit -m "chore: install ngx-quill and quill for rich text editing"
```

---

### Task 11: Create Salon Profile Models & Service

**Files:**
- Create: `frontend/src/app/features/salon-profile/models/salon-profile.model.ts`
- Create: `frontend/src/app/features/salon-profile/services/salon-profile.service.ts`

- [ ] **Step 1: Create salon profile models**

```typescript
// salon-profile.model.ts
export interface TenantResponse {
  id: number;
  name: string;
  slug: string;
  description: string | null;
  logoUrl: string | null;
  updatedAt: string | null;
}

export interface UpdateTenantRequest {
  name: string;
  description: string | null;
  logo: string | null; // base64 or null (no change) or "" (remove)
}

export interface PublicCareDto {
  name: string;
  duration: number;
  price: number;
  imageUrls: string[];
}

export interface PublicCategoryDto {
  name: string;
  cares: PublicCareDto[];
}

export interface PublicSalonResponse {
  name: string;
  slug: string;
  description: string | null;
  logoUrl: string | null;
  categories: PublicCategoryDto[];
}
```

- [ ] **Step 2: Create salon profile service**

```typescript
// salon-profile.service.ts
import { Injectable, inject, PLATFORM_ID } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { isPlatformBrowser } from '@angular/common';
import { Observable, map } from 'rxjs';
import { API_BASE_URL } from '../../../core/config/api-base-url.token';
import { TenantResponse, UpdateTenantRequest, PublicSalonResponse } from '../models/salon-profile.model';

@Injectable({ providedIn: 'root' })
export class SalonProfileService {
  private readonly http = inject(HttpClient);
  private readonly apiBaseUrl = inject(API_BASE_URL);
  private readonly platformId = inject(PLATFORM_ID);
  private readonly isBrowser = isPlatformBrowser(this.platformId);

  private get baseUrl(): string {
    const base = this.apiBaseUrl?.replace(/\/$/, '') ?? '';
    return base;
  }

  getProfile(): Observable<TenantResponse> {
    return this.http.get<TenantResponse>(`${this.baseUrl}/api/pro/tenant`).pipe(
      map(tenant => this.transformLogoUrl(tenant))
    );
  }

  updateProfile(request: UpdateTenantRequest): Observable<TenantResponse> {
    return this.http.put<TenantResponse>(`${this.baseUrl}/api/pro/tenant`, request).pipe(
      map(tenant => this.transformLogoUrl(tenant))
    );
  }

  getPublicSalon(slug: string): Observable<PublicSalonResponse> {
    return this.http.get<PublicSalonResponse>(`${this.baseUrl}/api/salon/${slug}`).pipe(
      map(salon => this.transformPublicUrls(salon))
    );
  }

  private transformLogoUrl(tenant: TenantResponse): TenantResponse {
    if (!tenant.logoUrl || !this.isBrowser) return tenant;
    const url = tenant.logoUrl.startsWith('http') ? tenant.logoUrl : `${this.baseUrl}${tenant.logoUrl}`;
    return { ...tenant, logoUrl: url };
  }

  private transformPublicUrls(salon: PublicSalonResponse): PublicSalonResponse {
    if (!this.isBrowser) return salon;
    return {
      ...salon,
      logoUrl: salon.logoUrl ? (salon.logoUrl.startsWith('http') ? salon.logoUrl : `${this.baseUrl}${salon.logoUrl}`) : null,
      categories: salon.categories.map(cat => ({
        ...cat,
        cares: cat.cares.map(care => ({
          ...care,
          imageUrls: care.imageUrls.map(url =>
            url.startsWith('http') ? url : `${this.baseUrl}${url}`
          )
        }))
      }))
    };
  }
}
```

- [ ] **Step 3: Verify compilation**

Run: `cd frontend && npx ng build --configuration=development 2>&1 | tail -5`
Expected: Build succeeds

- [ ] **Step 4: Commit**

```bash
git add frontend/src/app/features/salon-profile/
git commit -m "feat: add salon profile models and service"
```

---

### Task 12: Create Salon Profile Store

**Files:**
- Create: `frontend/src/app/features/salon-profile/store/salon-profile.store.ts`

- [ ] **Step 1: Create SignalStore**

```typescript
import { inject } from '@angular/core';
import { patchState, signalStore, withHooks, withMethods, withState } from '@ngrx/signals';
import { rxMethod } from '@ngrx/signals/rxjs-interop';
import { EMPTY, catchError, exhaustMap, pipe, switchMap, tap } from 'rxjs';
import { TenantResponse, UpdateTenantRequest } from '../models/salon-profile.model';
import { SalonProfileService } from '../services/salon-profile.service';
import { setError, setFulfilled, setPending, withRequestStatus } from '../../../shared/features/request.status.feature';

type SalonProfileState = {
  tenant: TenantResponse | null;
  saveSuccess: boolean;
};

export const SalonProfileStore = signalStore(
  withState<SalonProfileState>({ tenant: null, saveSuccess: false }),
  withRequestStatus(),
  withMethods((store, service = inject(SalonProfileService)) => ({
    loadProfile: rxMethod<void>(
      pipe(
        tap(() => patchState(store, setPending())),
        switchMap(() =>
          service.getProfile().pipe(
            tap(tenant => patchState(store, { tenant }, setFulfilled())),
            catchError(() => {
              patchState(store, setError('Erreur de chargement du profil'));
              return EMPTY;
            })
          )
        )
      )
    ),
    updateProfile: rxMethod<UpdateTenantRequest>(
      pipe(
        tap(() => patchState(store, { saveSuccess: false }, setPending())),
        exhaustMap(request =>
          service.updateProfile(request).pipe(
            tap(tenant => patchState(store, { tenant, saveSuccess: true }, setFulfilled())),
            catchError(() => {
              patchState(store, setError('Erreur lors de la sauvegarde'));
              return EMPTY;
            })
          )
        )
      )
    ),
    clearStatus() {
      patchState(store, { saveSuccess: false }, setFulfilled());
    }
  })),
  withHooks((store) => ({
    onInit() {
      store.loadProfile();
    }
  }))
);
```

- [ ] **Step 2: Verify compilation**

Run: `cd frontend && npx ng build --configuration=development 2>&1 | tail -5`
Expected: Build succeeds

- [ ] **Step 3: Commit**

```bash
git add frontend/src/app/features/salon-profile/store/
git commit -m "feat: add SalonProfileStore with load and update methods"
```

---

### Task 13: Create Salon Profile Component (Pro Settings Page)

**Files:**
- Create: `frontend/src/app/features/salon-profile/salon-profile.component.ts`
- Create: `frontend/src/app/features/salon-profile/salon-profile.component.html`
- Create: `frontend/src/app/features/salon-profile/salon-profile.component.scss`

- [ ] **Step 1: Create the component TypeScript**

```typescript
import { Component, inject, signal, effect, computed } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { QuillEditorComponent } from 'ngx-quill';
import { ImageManager, ManagedImage } from '../../shared/uis/image-manager/image-manager.component';
import { SalonProfileStore } from './store/salon-profile.store';
import { UpdateTenantRequest } from './models/salon-profile.model';

@Component({
  selector: 'app-salon-profile',
  standalone: true,
  imports: [
    FormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatProgressSpinnerModule,
    TranslocoPipe,
    QuillEditorComponent,
    ImageManager,
  ],
  providers: [SalonProfileStore],
  templateUrl: './salon-profile.component.html',
  styleUrl: './salon-profile.component.scss',
})
export class SalonProfileComponent {
  protected readonly store = inject(SalonProfileStore);
  private readonly snackBar = inject(MatSnackBar);
  private readonly transloco = inject(TranslocoService);

  protected name = signal('');
  protected description = signal('');
  protected logoImages = signal<ManagedImage[]>([]);
  protected slug = signal('');

  // Track if logo changed (to decide null vs base64 in request)
  private logoChanged = false;

  protected readonly descriptionTextLength = computed(() => {
    const text = this.description()?.replace(/<[^>]*>/g, '') ?? '';
    return text.length;
  });

  readonly quillModules = {
    toolbar: [['bold', 'italic'], [{ list: 'ordered' }, { list: 'bullet' }], ['link']],
  };

  readonly MAX_DESCRIPTION_LENGTH = 10000;

  constructor() {
    // Sync store tenant to form fields
    effect(() => {
      const tenant = this.store.tenant();
      if (tenant) {
        this.name.set(tenant.name);
        this.slug.set(tenant.slug);
        this.description.set(tenant.description ?? '');
        if (tenant.logoUrl) {
          this.logoImages.set([{
            id: 'existing-logo',
            url: tenant.logoUrl,
            name: 'logo',
            order: 0,
          }]);
        } else {
          this.logoImages.set([]);
        }
        this.logoChanged = false;
      }
    });

    // Show snackbar on save success
    effect(() => {
      if (this.store.saveSuccess()) {
        this.snackBar.open(
          this.transloco.translate('pro.salon.saveSuccess'),
          undefined,
          { duration: 3000 }
        );
        this.store.clearStatus();
      }
    });

    // Show snackbar on error
    effect(() => {
      const error = this.store.error();
      if (error) {
        this.snackBar.open(
          this.transloco.translate('pro.salon.saveError'),
          undefined,
          { duration: 5000, panelClass: 'snackbar-error' }
        );
      }
    });
  }

  protected onLogoChange(images: ManagedImage[]): void {
    this.logoImages.set(images);
    this.logoChanged = true;
  }

  protected onSave(): void {
    if (!this.name().trim()) return;

    let logo: string | null = null;

    if (this.logoChanged) {
      const images = this.logoImages();
      if (images.length === 0) {
        logo = ''; // Remove logo
      } else if (images[0].base64Data || images[0].file) {
        // New image — use base64Data (set by ImageManager via FileReader)
        logo = images[0].url; // url contains the data URL from FileReader
      }
    }

    const request: UpdateTenantRequest = {
      name: this.name().trim(),
      description: this.description() || null,
      logo,
    };

    this.store.updateProfile(request);
  }

}
```

- [ ] **Step 2: Create the component template**

```html
<div class="salon-profile-container">
  <h1 class="text-2xl font-medium text-neutral-800 mb-6">{{ 'pro.salon.title' | transloco }}</h1>

  @if (store.isPending() && !store.tenant()) {
    <div class="flex justify-center py-12">
      <mat-spinner diameter="40"></mat-spinner>
    </div>
  } @else if (store.tenant()) {
    <form (ngSubmit)="onSave()" class="flex flex-col gap-6 max-w-2xl">

      <!-- Salon Name -->
      <mat-form-field appearance="outline">
        <mat-label>{{ 'pro.salon.name' | transloco }}</mat-label>
        <input
          matInput
          [ngModel]="name()"
          (ngModelChange)="name.set($event)"
          required
          maxlength="100"
          name="salonName"
        />
        @if (!name().trim()) {
          <mat-error>{{ 'pro.salon.nameRequired' | transloco }}</mat-error>
        }
      </mat-form-field>

      <!-- Slug Preview (read-only) -->
      <div class="text-sm text-neutral-500">
        {{ 'pro.salon.slugPreview' | transloco }}: <span class="font-mono">/salon/{{ slug() }}</span>
      </div>

      <!-- Logo -->
      <div>
        <label class="block text-sm font-medium text-neutral-700 mb-2">{{ 'pro.salon.logo' | transloco }}</label>
        <image-manager
          [images]="logoImages()"
          [maxImages]="1"
          (imagesChange)="onLogoChange($event)"
        />
      </div>

      <!-- Description (Rich Text) -->
      <div>
        <label class="block text-sm font-medium text-neutral-700 mb-2">{{ 'pro.salon.description' | transloco }}</label>
        <quill-editor
          [ngModel]="description()"
          (ngModelChange)="description.set($event)"
          [modules]="quillModules"
          format="html"
          name="description"
          class="salon-quill-editor"
        ></quill-editor>
        <div class="text-xs text-neutral-400 mt-1 text-right">
          {{ descriptionTextLength() }} / {{ MAX_DESCRIPTION_LENGTH }}
          @if (descriptionTextLength() > MAX_DESCRIPTION_LENGTH) {
            <span class="text-red-500 ml-1">{{ 'pro.salon.descriptionLimit' | transloco }}</span>
          }
        </div>
      </div>

      <!-- Save Button -->
      <div class="flex justify-end">
        <button
          mat-raised-button
          color="primary"
          type="submit"
          [disabled]="store.isPending() || !name().trim() || descriptionTextLength() > MAX_DESCRIPTION_LENGTH"
        >
          @if (store.isPending()) {
            <mat-spinner diameter="20" class="inline-block mr-2"></mat-spinner>
          }
          {{ 'pro.salon.save' | transloco }}
        </button>
      </div>

    </form>
  }
</div>
```

- [ ] **Step 3: Create the component styles**

```scss
.salon-profile-container {
  padding: 2rem;
}

.salon-quill-editor {
  ::ng-deep .ql-container {
    min-height: 150px;
    font-family: inherit;
  }

  ::ng-deep .ql-editor {
    min-height: 150px;
  }
}
```

- [ ] **Step 4: Verify compilation**

Run: `cd frontend && npx ng build --configuration=development 2>&1 | tail -5`
Expected: Build succeeds

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/features/salon-profile/
git commit -m "feat: add salon profile settings component with form, quill editor, and image manager"
```

---

### Task 14: Add Route and Navigation

**Files:**
- Modify: `frontend/src/app/app.routes.ts`
- Modify: `frontend/src/app/shared/layout/header/header.html`

- [ ] **Step 1: Add /pro/salon route**

In `app.routes.ts`, add a new child route under the `pro` path:

```typescript
{
  path: 'pro',
  canActivate: [authGuard],
  children: [
    {
      path: 'dashboard',
      loadComponent: () => import('./pages/pro/pro-dashboard.component').then(m => m.ProDashboardComponent),
    },
    {
      path: 'salon',
      loadComponent: () => import('./features/salon-profile/salon-profile.component').then(m => m.SalonProfileComponent),
    },
    { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
  ],
},
```

- [ ] **Step 2: Add "Mon salon" link in header user menu**

In `header.html`, add a menu item after the dashboard button (line 49):

```html
<button mat-menu-item routerLink="/pro/salon">
  {{ 'pro.salon.title' | transloco }}
</button>
```

- [ ] **Step 3: Verify compilation**

Run: `cd frontend && npx ng build --configuration=development 2>&1 | tail -5`
Expected: Build succeeds

- [ ] **Step 4: Commit**

```bash
git add frontend/src/app/app.routes.ts
git add frontend/src/app/shared/layout/header/header.html
git commit -m "feat: add /pro/salon route and navigation link in header menu"
```

---

## Chunk 4: Frontend — Public Salon Page & i18n

### Task 15: Create Public Salon Page Component

**Files:**
- Create: `frontend/src/app/pages/salon/salon-page.component.ts`
- Create: `frontend/src/app/pages/salon/salon-page.component.html`
- Create: `frontend/src/app/pages/salon/salon-page.component.scss`

- [ ] **Step 1: Create the component TypeScript**

```typescript
import { Component, inject, signal, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslocoPipe } from '@jsverse/transloco';
import { SalonProfileService } from '../../features/salon-profile/services/salon-profile.service';
import { PublicSalonResponse } from '../../features/salon-profile/models/salon-profile.model';

@Component({
  selector: 'app-salon-page',
  standalone: true,
  imports: [MatExpansionModule, MatProgressSpinnerModule, TranslocoPipe],
  templateUrl: './salon-page.component.html',
  styleUrl: './salon-page.component.scss',
})
export class SalonPageComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly salonService = inject(SalonProfileService);

  protected salon = signal<PublicSalonResponse | null>(null);
  protected loading = signal(true);
  protected notFound = signal(false);

  ngOnInit(): void {
    const slug = this.route.snapshot.paramMap.get('slug');
    if (!slug) {
      this.notFound.set(true);
      this.loading.set(false);
      return;
    }

    this.salonService.getPublicSalon(slug).subscribe({
      next: (salon) => {
        this.salon.set(salon);
        this.loading.set(false);
      },
      error: () => {
        this.notFound.set(true);
        this.loading.set(false);
      },
    });
  }

  protected formatDuration(minutes: number): string {
    if (minutes < 60) return `${minutes} min`;
    const h = Math.floor(minutes / 60);
    const m = minutes % 60;
    return m > 0 ? `${h}h${m.toString().padStart(2, '0')}` : `${h}h`;
  }

  protected formatPrice(cents: number): string {
    return (cents / 100).toFixed(2).replace('.', ',') + ' €';
  }
}
```

- [ ] **Step 2: Create the template**

```html
@if (loading()) {
  <div class="flex justify-center py-16">
    <mat-spinner diameter="40"></mat-spinner>
  </div>
} @else if (notFound()) {
  <div class="flex items-center justify-center min-h-[60vh]">
    <div class="text-center">
      <p class="text-xl text-neutral-500">{{ 'salon.public.notFound' | transloco }}</p>
    </div>
  </div>
} @else if (salon(); as salon) {
  <div class="salon-page">
    <!-- Salon Header -->
    <section class="salon-header">
      @if (salon.logoUrl) {
        <img [src]="salon.logoUrl" [alt]="salon.name" class="salon-logo" />
      }
      <h1 class="text-3xl font-medium text-neutral-900 mt-4">{{ salon.name }}</h1>
      @if (salon.description) {
        <div class="salon-description mt-4" [innerHTML]="salon.description"></div>
      }
    </section>

    <!-- Cares by Category (Accordion) -->
    @if (salon.categories.length > 0) {
      <section class="mt-8">
        <mat-accordion multi>
          @for (category of salon.categories; track category.name) {
            <mat-expansion-panel [expanded]="true">
              <mat-expansion-panel-header>
                <mat-panel-title class="font-medium">
                  {{ category.name }}
                </mat-panel-title>
              </mat-expansion-panel-header>

              <div class="care-list">
                @for (care of category.cares; track care.name) {
                  <div class="care-card">
                    @if (care.imageUrls.length > 0) {
                      <img [src]="care.imageUrls[0]" [alt]="care.name" class="care-thumbnail" />
                    }
                    <div class="care-info">
                      <span class="care-name">{{ care.name }}</span>
                      <div class="care-details">
                        <span>{{ 'salon.public.duration' | transloco }}: {{ formatDuration(care.duration) }}</span>
                        <span>{{ 'salon.public.price' | transloco }}: {{ formatPrice(care.price) }}</span>
                      </div>
                    </div>
                  </div>
                }
              </div>
            </mat-expansion-panel>
          }
        </mat-accordion>
      </section>
    } @else {
      <div class="text-center py-12 text-neutral-500">
        {{ 'salon.public.noCares' | transloco }}
      </div>
    }
  </div>
}
```

- [ ] **Step 3: Create the styles**

```scss
.salon-page {
  max-width: 800px;
  margin: 0 auto;
  padding: 2rem;
}

.salon-header {
  text-align: center;
  padding-bottom: 2rem;
  border-bottom: 1px solid var(--mat-sys-outline-variant, #e0e0e0);
}

.salon-logo {
  width: 120px;
  height: 120px;
  object-fit: cover;
  border-radius: 50%;
  margin: 0 auto;
  display: block;
}

.salon-description {
  max-width: 600px;
  margin: 0 auto;
  color: #555;
  line-height: 1.6;

  p {
    margin-bottom: 0.5rem;
  }

  ul, ol {
    padding-left: 1.5rem;
    margin-bottom: 0.5rem;
  }

  a {
    color: var(--mat-sys-primary, #b4637a);
    text-decoration: underline;
  }
}

.care-list {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.care-card {
  display: flex;
  align-items: center;
  gap: 1rem;
  padding: 0.75rem;
  border-radius: 8px;
  background: #fafafa;
}

.care-thumbnail {
  width: 64px;
  height: 64px;
  object-fit: cover;
  border-radius: 8px;
  flex-shrink: 0;
}

.care-info {
  flex: 1;
}

.care-name {
  font-weight: 500;
  display: block;
  margin-bottom: 0.25rem;
}

.care-details {
  display: flex;
  gap: 1rem;
  font-size: 0.875rem;
  color: #777;
}
```

- [ ] **Step 4: Add route for /salon/:slug**

In `app.routes.ts`, add before the wildcard route:

```typescript
{
  path: 'salon/:slug',
  loadComponent: () => import('./pages/salon/salon-page.component').then(m => m.SalonPageComponent),
},
```

- [ ] **Step 5: Verify compilation**

Run: `cd frontend && npx ng build --configuration=development 2>&1 | tail -5`
Expected: Build succeeds

- [ ] **Step 6: Commit**

```bash
git add frontend/src/app/pages/salon/
git add frontend/src/app/app.routes.ts
git commit -m "feat: add public salon page at /salon/:slug with accordion cares by category"
```

---

### Task 16: Add i18n Translations

**Files:**
- Modify: `frontend/public/i18n/fr.json`
- Modify: `frontend/public/i18n/en.json`

- [ ] **Step 1: Add French translations**

Add under the `"pro"` key:

```json
"pro": {
  "dashboard": {
    "title": "Tableau de bord"
  },
  "salon": {
    "title": "Mon salon",
    "name": "Nom du salon",
    "logo": "Logo",
    "description": "Description",
    "save": "Enregistrer",
    "saveSuccess": "Profil mis à jour avec succès",
    "saveError": "Erreur lors de la sauvegarde du profil",
    "slugPreview": "Votre URL",
    "nameRequired": "Le nom du salon est requis",
    "descriptionLimit": "Limite de caractères atteinte",
    "logoError": "Erreur lors du téléchargement du logo"
  }
}
```

Add at root level:

```json
"salon": {
  "public": {
    "noCares": "Aucun soin disponible pour le moment",
    "notFound": "Salon introuvable",
    "duration": "Durée",
    "price": "Prix"
  }
}
```

- [ ] **Step 2: Add English translations**

Add under the `"pro"` key:

```json
"pro": {
  "dashboard": {
    "title": "Dashboard"
  },
  "salon": {
    "title": "My salon",
    "name": "Salon name",
    "logo": "Logo",
    "description": "Description",
    "save": "Save",
    "saveSuccess": "Profile updated successfully",
    "saveError": "Error saving profile",
    "slugPreview": "Your URL",
    "nameRequired": "Salon name is required",
    "descriptionLimit": "Character limit reached",
    "logoError": "Error uploading logo"
  }
}
```

Add at root level:

```json
"salon": {
  "public": {
    "noCares": "No services available at this time",
    "notFound": "Salon not found",
    "duration": "Duration",
    "price": "Price"
  }
}
```

- [ ] **Step 3: Commit**

```bash
git add frontend/public/i18n/
git commit -m "feat: add i18n translations for salon profile (fr + en)"
```

---

### Task 17: Frontend Tests

**Files:**
- Create: `frontend/src/app/features/salon-profile/salon-profile.component.spec.ts`
- Create: `frontend/src/app/pages/salon/salon-page.component.spec.ts`

- [ ] **Step 1: Create salon profile component spec**

```typescript
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideTransloco } from '@jsverse/transloco';
import { SalonProfileComponent } from './salon-profile.component';
import { API_BASE_URL } from '../../core/config/api-base-url.token';

describe('SalonProfileComponent', () => {
  let component: SalonProfileComponent;
  let fixture: ComponentFixture<SalonProfileComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SalonProfileComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        provideNoopAnimations(),
        provideTransloco({ config: { defaultLang: 'fr', availableLangs: ['fr', 'en'] } }),
        { provide: API_BASE_URL, useValue: 'http://localhost:8080' },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(SalonProfileComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
```

- [ ] **Step 2: Create salon page component spec**

```typescript
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, ActivatedRoute } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideTransloco } from '@jsverse/transloco';
import { SalonPageComponent } from './salon-page.component';
import { API_BASE_URL } from '../../core/config/api-base-url.token';

describe('SalonPageComponent', () => {
  let component: SalonPageComponent;
  let fixture: ComponentFixture<SalonPageComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SalonPageComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        provideNoopAnimations(),
        provideTransloco({ config: { defaultLang: 'fr', availableLangs: ['fr', 'en'] } }),
        { provide: API_BASE_URL, useValue: 'http://localhost:8080' },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: { get: () => 'test-salon' } } },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(SalonPageComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
```

- [ ] **Step 3: Run frontend tests**

Run: `cd frontend && npm test -- --no-watch --browsers=ChromeHeadless 2>&1 | tail -10`
Expected: All tests pass

- [ ] **Step 4: Commit**

```bash
git add frontend/src/app/features/salon-profile/salon-profile.component.spec.ts
git add frontend/src/app/pages/salon/salon-page.component.spec.ts
git commit -m "test: add specs for salon profile and public salon page components"
```
