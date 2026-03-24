# Category Management Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add PRO-scoped category CRUD as colored chips above the cares table, with filtering and delete-with-reassignment.

**Architecture:** Extend existing Category backend with PRO endpoints (flat list, create, update, delete-with-reassign). Frontend integrates chips directly into CaresComponent — no new routes. CategoriesStore and CategoriesService get pro-scoped methods alongside existing admin ones.

**Tech Stack:** Spring Boot 3.5.4 / Java 21, Angular 20 (standalone, zoneless, signals), NgRx SignalStore, Angular Material, Transloco i18n

**Spec:** `docs/superpowers/specs/2026-03-16-category-management-design.md`

---

## Chunk 1: Backend

### Task 1: Add `@Size` constraints to CategoryRequest DTO

**Files:**
- Modify: `backend/src/main/java/com/prettyface/app/category/web/dto/CategoryRequest.java`

- [ ] **Step 1: Update CategoryRequest with size constraints**

```java
package com.prettyface.app.category.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoryRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 500) String description
) {}
```

- [ ] **Step 2: Run backend tests to verify nothing breaks**

Run: `cd backend && mvn test -pl . -q`
Expected: All existing tests pass

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/prettyface/app/category/web/dto/CategoryRequest.java
git commit -m "feat: add @Size constraints to CategoryRequest DTO"
```

---

### Task 2: Add CareRepository query methods for count and bulk reassignment

**Files:**
- Modify: `backend/src/main/java/com/prettyface/app/care/repo/CareRepository.java`

- [ ] **Step 1: Add count and bulk update methods**

```java
package com.prettyface.app.care.repo;

import com.prettyface.app.care.domain.Care;
import com.prettyface.app.category.domain.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CareRepository extends JpaRepository<Care, Long> {

    long countByCategoryId(Long categoryId);

    @Modifying
    @Query("UPDATE Care c SET c.category = :target WHERE c.category.id = :sourceId")
    int reassignCategory(@Param("sourceId") Long sourceId, @Param("target") Category target);
}
```

- [ ] **Step 2: Run backend tests**

Run: `cd backend && mvn test -pl . -q`
Expected: All tests pass

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/prettyface/app/care/repo/CareRepository.java
git commit -m "feat: add countByCategoryId and reassignCategory to CareRepository"
```

---

### Task 3: Add DeleteCategoryResponse DTO and 409 handler

**Files:**
- Create: `backend/src/main/java/com/prettyface/app/category/web/dto/DeleteCategoryResponse.java`
- Modify: `backend/src/main/java/com/prettyface/app/common/error/GlobalExceptionHandler.java`

- [ ] **Step 1: Create DeleteCategoryResponse**

```java
package com.prettyface.app.category.web.dto;

public record DeleteCategoryResponse(int reassignedCaresCount) {}
```

- [ ] **Step 2: Add DataIntegrityViolationException handler for duplicate names**

Add to `GlobalExceptionHandler.java` after the existing `badRequest` handler:

```java
@ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
public ResponseEntity<Map<String,Object>> conflict(org.springframework.dao.DataIntegrityViolationException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(Map.of("error", "A record with this value already exists"));
}
```

- [ ] **Step 3: Run backend tests**

Run: `cd backend && mvn test -pl . -q`
Expected: All tests pass

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/prettyface/app/category/web/dto/DeleteCategoryResponse.java \
       backend/src/main/java/com/prettyface/app/common/error/GlobalExceptionHandler.java
git commit -m "feat: add DeleteCategoryResponse DTO and 409 conflict handler"
```

---

### Task 4: Extend CategoryService with pro methods

**Files:**
- Modify: `backend/src/main/java/com/prettyface/app/category/app/CategoryService.java`
- Create: `backend/src/test/java/com/prettyface/app/category/app/CategoryServiceTests.java`

- [ ] **Step 1: Write tests for the new service methods**

```java
package com.prettyface.app.category.app;

import com.prettyface.app.care.repo.CareRepository;
import com.prettyface.app.category.domain.Category;
import com.prettyface.app.category.repo.CategoryRepository;
import com.prettyface.app.category.web.dto.CategoryRequest;
import com.prettyface.app.category.web.dto.CategoryResponse;
import com.prettyface.app.category.web.dto.DeleteCategoryResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTests {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private CareRepository careRepository;

    @InjectMocks
    private CategoryService categoryService;

    private Category category;
    private Category targetCategory;

    @BeforeEach
    void setUp() {
        category = new Category();
        category.setId(1L);
        category.setName("Visage");
        category.setDescription("Soins du visage");

        targetCategory = new Category();
        targetCategory.setId(2L);
        targetCategory.setName("Corps");
        targetCategory.setDescription("Soins du corps");
    }

    @Test
    void listAll_returnsAllCategories() {
        when(categoryRepository.findAll()).thenReturn(List.of(category, targetCategory));

        List<CategoryResponse> result = categoryService.listAll();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).name()).isEqualTo("Visage");
    }

    @Test
    void deleteWithReassignment_whenNoCares_deletesDirectly() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(careRepository.countByCategoryId(1L)).thenReturn(0L);

        DeleteCategoryResponse response = categoryService.deleteWithReassignment(1L, null);

        verify(categoryRepository).deleteById(1L);
        assertThat(response.reassignedCaresCount()).isZero();
    }

    @Test
    void deleteWithReassignment_whenCaresExistAndNoTarget_throws() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(careRepository.countByCategoryId(1L)).thenReturn(3L);

        assertThatThrownBy(() -> categoryService.deleteWithReassignment(1L, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("3");
    }

    @Test
    void deleteWithReassignment_whenCaresExistAndTargetProvided_reassigns() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(careRepository.countByCategoryId(1L)).thenReturn(3L);
        when(categoryRepository.findById(2L)).thenReturn(Optional.of(targetCategory));
        when(careRepository.reassignCategory(eq(1L), any())).thenReturn(3);

        DeleteCategoryResponse response = categoryService.deleteWithReassignment(1L, 2L);

        verify(careRepository).reassignCategory(1L, targetCategory);
        verify(categoryRepository).deleteById(1L);
        assertThat(response.reassignedCaresCount()).isEqualTo(3);
    }

    @Test
    void deleteWithReassignment_whenTargetNotFound_throws() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(careRepository.countByCategoryId(1L)).thenReturn(3L);
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.deleteWithReassignment(1L, 99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("99");
    }

    @Test
    void deleteWithReassignment_whenTargetSameAsSource_throws() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(careRepository.countByCategoryId(1L)).thenReturn(3L);

        assertThatThrownBy(() -> categoryService.deleteWithReassignment(1L, 1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("same");
    }

    @Test
    void deleteWithReassignment_whenSourceNotFound_throws() {
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.deleteWithReassignment(99L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("99");
    }

    @Test
    void listAll_emptyList_returnsEmpty() {
        when(categoryRepository.findAll()).thenReturn(List.of());

        List<CategoryResponse> result = categoryService.listAll();

        assertThat(result).isEmpty();
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd backend && mvn test -Dtest=CategoryServiceTests -pl . -q`
Expected: FAIL — methods `listAll` and `deleteWithReassignment` don't exist

- [ ] **Step 3: Implement service methods**

Modify `CategoryService.java` — add `CareRepository` dependency and two new methods:

```java
package com.prettyface.app.category.app;

import com.prettyface.app.care.repo.CareRepository;
import com.prettyface.app.category.domain.Category;
import com.prettyface.app.category.repo.CategoryRepository;
import com.prettyface.app.category.web.dto.CategoryRequest;
import com.prettyface.app.category.web.dto.CategoryResponse;
import com.prettyface.app.category.web.dto.DeleteCategoryResponse;
import com.prettyface.app.category.web.mapper.CategoryMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CategoryService {
    private final CategoryRepository repo;
    private final CareRepository careRepository;

    public CategoryService(CategoryRepository repo, CareRepository careRepository) {
        this.repo = repo;
        this.careRepository = careRepository;
    }

    @Transactional(readOnly = true)
    public Page<CategoryResponse> list(Pageable pageable) {
        return repo.findAll(pageable).map(CategoryMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> listAll() {
        return repo.findAll().stream().map(CategoryMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public CategoryResponse get(Long id) {
        return repo.findById(id).map(CategoryMapper::toResponse)
                .orElseThrow(() -> new IllegalArgumentException("Category not found: " + id));
    }

    @Transactional
    public CategoryResponse create(CategoryRequest req) {
        Category saved = repo.save(CategoryMapper.toEntity(req));
        return CategoryMapper.toResponse(saved);
    }

    @Transactional
    public CategoryResponse update(Long id, CategoryRequest req) {
        Category c = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("Category not found: " + id));
        CategoryMapper.updateEntity(c, req);
        return CategoryMapper.toResponse(repo.save(c));
    }

    @Transactional
    public void delete(Long id) { repo.deleteById(id); }

    @Transactional
    public DeleteCategoryResponse deleteWithReassignment(Long id, Long reassignToId) {
        repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Category not found: " + id));

        long careCount = careRepository.countByCategoryId(id);

        if (careCount > 0 && reassignToId == null) {
            throw new IllegalStateException(
                    "Category has " + careCount + " care(s), reassignTo is required");
        }

        if (careCount > 0) {
            if (reassignToId.equals(id)) {
                throw new IllegalStateException("Cannot reassign to the same category");
            }
            Category target = repo.findById(reassignToId)
                    .orElseThrow(() -> new IllegalArgumentException("Target category not found: " + reassignToId));
            careRepository.reassignCategory(id, target);
        }

        repo.deleteById(id);
        return new DeleteCategoryResponse((int) careCount);
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd backend && mvn test -Dtest=CategoryServiceTests -pl . -q`
Expected: All 8 tests PASS

- [ ] **Step 5: Run all backend tests for regressions**

Run: `cd backend && mvn test -pl . -q`
Expected: All tests pass (the new constructor parameter for `CareRepository` is handled by Mockito's `@InjectMocks`)

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/prettyface/app/category/app/CategoryService.java \
       backend/src/test/java/com/prettyface/app/category/app/CategoryServiceTests.java
git commit -m "feat: add listAll and deleteWithReassignment to CategoryService"
```

---

### Task 5: Add IllegalStateException handler to GlobalExceptionHandler

**Files:**
- Modify: `backend/src/main/java/com/prettyface/app/common/error/GlobalExceptionHandler.java`

The `deleteWithReassignment` throws `IllegalStateException` when reassignment is required. This should return 400, not 500.

- [ ] **Step 1: Add handler**

Add after the existing `notFound` handler in `GlobalExceptionHandler.java`:

```java
@ExceptionHandler(IllegalStateException.class)
public ResponseEntity<Map<String,Object>> badState(IllegalStateException ex) {
    return ResponseEntity.badRequest()
            .body(Map.of("error", ex.getMessage()));
}
```

- [ ] **Step 2: Run backend tests**

Run: `cd backend && mvn test -pl . -q`
Expected: All tests pass

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/prettyface/app/common/error/GlobalExceptionHandler.java
git commit -m "feat: add IllegalStateException handler (400 Bad Request)"
```

---

### Task 6: Create ProCategoryController

**Files:**
- Create: `backend/src/main/java/com/prettyface/app/category/web/ProCategoryController.java`
- Create: `backend/src/test/java/com/prettyface/app/category/web/ProCategoryControllerTests.java`

- [ ] **Step 1: Write controller tests**

```java
package com.prettyface.app.category.web;

import com.prettyface.app.category.app.CategoryService;
import com.prettyface.app.category.web.dto.CategoryResponse;
import com.prettyface.app.category.web.dto.DeleteCategoryResponse;
import com.prettyface.app.config.SecurityConfig;
import com.prettyface.app.auth.TokenService;
import com.prettyface.app.users.repo.UserRepository;
import com.prettyface.app.auth.CustomOAuth2UserService;
import com.prettyface.app.auth.OAuth2AuthenticationSuccessHandler;
import com.prettyface.app.auth.OAuth2AuthenticationFailureHandler;
import com.prettyface.app.multitenancy.TenantFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.bean.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProCategoryController.class)
@Import(SecurityConfig.class)
class ProCategoryControllerTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CategoryService categoryService;

    // Security config dependencies
    @MockBean private TokenService tokenService;
    @MockBean private UserRepository userRepository;
    @MockBean private CustomOAuth2UserService customOAuth2UserService;
    @MockBean private OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;
    @MockBean private OAuth2AuthenticationFailureHandler oAuth2AuthenticationFailureHandler;
    @MockBean private TenantFilter tenantFilter;

    @Test
    @WithMockUser(roles = "PRO")
    void list_returnsCategoriesForPro() throws Exception {
        when(categoryService.listAll()).thenReturn(List.of(
                new CategoryResponse(1L, "Visage", "desc")));

        mvc.perform(get("/api/pro/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Visage"));
    }

    @Test
    @WithMockUser(roles = "PRO")
    void create_returnsCreatedCategory() throws Exception {
        when(categoryService.create(any())).thenReturn(new CategoryResponse(1L, "Visage", null));

        mvc.perform(post("/api/pro/categories")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Visage\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Visage"));
    }

    @Test
    @WithMockUser(roles = "PRO")
    void create_invalidName_returns400() throws Exception {
        mvc.perform(post("/api/pro/categories")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "PRO")
    void update_returnsUpdatedCategory() throws Exception {
        when(categoryService.update(eq(1L), any())).thenReturn(new CategoryResponse(1L, "Updated", null));

        mvc.perform(put("/api/pro/categories/1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Updated\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated"));
    }

    @Test
    @WithMockUser(roles = "PRO")
    void delete_noCares_returns200() throws Exception {
        when(categoryService.deleteWithReassignment(1L, null))
                .thenReturn(new DeleteCategoryResponse(0));

        mvc.perform(delete("/api/pro/categories/1").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reassignedCaresCount").value(0));
    }

    @Test
    @WithMockUser(roles = "PRO")
    void delete_withReassignment_returns200() throws Exception {
        when(categoryService.deleteWithReassignment(1L, 2L))
                .thenReturn(new DeleteCategoryResponse(3));

        mvc.perform(delete("/api/pro/categories/1?reassignTo=2").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reassignedCaresCount").value(3));
    }

    @Test
    void list_unauthenticated_returns401() throws Exception {
        mvc.perform(get("/api/pro/categories"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    void list_nonProRole_returns403() throws Exception {
        mvc.perform(get("/api/pro/categories"))
                .andExpect(status().isForbidden());
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd backend && mvn test -Dtest=ProCategoryControllerTests -pl . -q`
Expected: FAIL — `ProCategoryController` class not found

- [ ] **Step 3: Create the controller**

```java
package com.prettyface.app.category.web;

import com.prettyface.app.category.app.CategoryService;
import com.prettyface.app.category.web.dto.CategoryRequest;
import com.prettyface.app.category.web.dto.CategoryResponse;
import com.prettyface.app.category.web.dto.DeleteCategoryResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pro/categories")
public class ProCategoryController {

    private final CategoryService service;

    public ProCategoryController(CategoryService service) {
        this.service = service;
    }

    @GetMapping
    public List<CategoryResponse> list() {
        return service.listAll();
    }

    @PostMapping
    public CategoryResponse create(@RequestBody @Valid CategoryRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    public CategoryResponse update(@PathVariable Long id, @RequestBody @Valid CategoryRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    public DeleteCategoryResponse delete(@PathVariable Long id,
                                         @RequestParam(required = false) Long reassignTo) {
        return service.deleteWithReassignment(id, reassignTo);
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd backend && mvn test -Dtest=ProCategoryControllerTests -pl . -q`
Expected: All 8 tests PASS

- [ ] **Step 5: Run all backend tests**

Run: `cd backend && mvn test -pl . -q`
Expected: All tests pass

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/prettyface/app/category/web/ProCategoryController.java \
       backend/src/test/java/com/prettyface/app/category/web/ProCategoryControllerTests.java
git commit -m "feat: add ProCategoryController with CRUD endpoints and tests"
```

---

## Chunk 2: Frontend

### Task 7: Add i18n translation keys

**Files:**
- Modify: `frontend/public/i18n/fr.json`
- Modify: `frontend/public/i18n/en.json`

- [ ] **Step 1: Add French translations**

Add inside the `"pro"` object, after the `"salon"` block:

```json
"categories": {
  "add": "Ajouter une catégorie",
  "edit": "Modifier",
  "delete": "Supprimer",
  "nameRequired": "Le nom est requis",
  "createSuccess": "Catégorie créée",
  "updateSuccess": "Catégorie modifiée",
  "deleteSuccess": "Catégorie supprimée",
  "deleteError": "Erreur lors de la suppression",
  "duplicateName": "Une catégorie avec ce nom existe déjà",
  "reassign": {
    "title": "Réassigner les soins",
    "message": "Cette catégorie contient {{count}} soin(s). Choisissez une catégorie de destination.",
    "select": "Catégorie de destination",
    "confirm": "Confirmer la suppression"
  },
  "filter": {
    "all": "Tous les soins"
  }
}
```

- [ ] **Step 2: Add English translations**

Add inside the `"pro"` object, after the `"salon"` block:

```json
"categories": {
  "add": "Add category",
  "edit": "Edit",
  "delete": "Delete",
  "nameRequired": "Name is required",
  "createSuccess": "Category created",
  "updateSuccess": "Category updated",
  "deleteSuccess": "Category deleted",
  "deleteError": "Error deleting category",
  "duplicateName": "A category with this name already exists",
  "reassign": {
    "title": "Reassign services",
    "message": "This category has {{count}} service(s). Choose a target category.",
    "select": "Target category",
    "confirm": "Confirm deletion"
  },
  "filter": {
    "all": "All services"
  }
}
```

- [ ] **Step 3: Commit**

```bash
git add frontend/public/i18n/fr.json frontend/public/i18n/en.json
git commit -m "feat: add i18n keys for pro category management"
```

---

### Task 8: Add pro methods to CategoriesService

**Files:**
- Modify: `frontend/src/app/features/categories/services/categories.service.ts`
- Modify: `frontend/src/app/features/categories/models/categories.model.ts`

- [ ] **Step 1: Add DeleteCategoryResponse to model**

Add at the end of `categories.model.ts`:

```typescript
export interface DeleteCategoryResponse {
  reassignedCaresCount: number;
}
```

- [ ] **Step 2: Add pro methods to CategoriesService**

```typescript
import { Injectable, inject } from '@angular/core';
import {
  Category,
  CreateCategoryRequest,
  DeleteCategoryResponse,
  UpdateCategoryRequest,
} from '../models/categories.model';
import { BaseCrudService } from '../../../core/data/base-crud.service';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../../../core/config/api-base-url.token';

@Injectable({ providedIn: 'root' })
export class CategoriesService extends BaseCrudService<
  Category,
  CreateCategoryRequest,
  UpdateCategoryRequest
> {
  protected readonly basePath = '/api/categories';

  private get proBaseUrl(): string {
    const a = this.apiBaseUrl?.replace(/\/$/, '') ?? '';
    return `${a}/api/pro/categories`;
  }

  listAllPro(): Observable<Category[]> {
    return this.http.get<Category[]>(this.proBaseUrl);
  }

  createPro(payload: CreateCategoryRequest): Observable<Category> {
    return this.http.post<Category>(this.proBaseUrl, payload);
  }

  updatePro(id: number, payload: UpdateCategoryRequest): Observable<Category> {
    return this.http.put<Category>(`${this.proBaseUrl}/${id}`, payload);
  }

  deletePro(id: number, reassignTo?: number): Observable<DeleteCategoryResponse> {
    const params: Record<string, string> = {};
    if (reassignTo != null) {
      params['reassignTo'] = reassignTo.toString();
    }
    return this.http.delete<DeleteCategoryResponse>(`${this.proBaseUrl}/${id}`, { params });
  }
}
```

- [ ] **Step 3: Commit**

```bash
git add frontend/src/app/features/categories/services/categories.service.ts \
       frontend/src/app/features/categories/models/categories.model.ts
git commit -m "feat: add pro CRUD methods to CategoriesService"
```

---

### Task 9: Add pro methods to CategoriesStore

**Files:**
- Modify: `frontend/src/app/features/categories/store/categories.store.ts`

- [ ] **Step 1: Add pro CRUD methods to the store**

Add the following methods inside `withMethods(...)`, after the existing `deleteCategory` method. These pro methods mirror the existing admin ones but call the pro service endpoints:

```typescript
getProCategories: rxMethod<void>(
  pipe(
    tap(() => patchState(store, setPending())),
    switchMap(() =>
      gateway.listAllPro().pipe(
        tap((categories) => patchState(store, { categories }, setFulfilled())),
        catchError((err) => {
          patchState(store, setError(err?.message ?? 'Erreur de chargement des catégories'));
          return EMPTY;
        })
      )
    )
  )
),
createProCategory: rxMethod<CreateCategoryRequest>(
  pipe(
    tap(() => patchState(store, setPending())),
    exhaustMap((payload) =>
      gateway.createPro(payload).pipe(
        tap((created) => patchState(store, { categories: [...store.categories(), created] }, setFulfilled())),
        catchError((err) => {
          patchState(store, setError(err?.message ?? 'Erreur lors de la création'));
          return EMPTY;
        })
      )
    )
  )
),
updateProCategory: rxMethod<{ id: number; payload: UpdateCategoryRequest }>(
  pipe(
    tap(() => patchState(store, setPending())),
    exhaustMap(({ id, payload }) =>
      gateway.updatePro(id, payload).pipe(
        tap((updated) =>
          patchState(
            store,
            { categories: store.categories().map((it) => (it.id === updated.id ? updated : it)) },
            setFulfilled()
          )
        ),
        catchError((err) => {
          patchState(store, setError(err?.message ?? 'Erreur lors de la mise à jour'));
          return EMPTY;
        })
      )
    )
  )
),
deleteProCategory: rxMethod<{ id: number; reassignTo?: number }>(
  pipe(
    tap(() => patchState(store, setPending())),
    exhaustMap(({ id, reassignTo }) =>
      gateway.deletePro(id, reassignTo).pipe(
        tap(() =>
          patchState(
            store,
            { categories: store.categories().filter((it) => it.id !== id) },
            setFulfilled()
          )
        ),
        catchError((err) => {
          patchState(store, setError(err?.message ?? 'Erreur lors de la suppression'));
          return EMPTY;
        })
      )
    )
  )
),
```

Also add `catchError` and `EMPTY` to the imports from `rxjs`:

```typescript
import { catchError, EMPTY, exhaustMap, pipe, switchMap, tap } from 'rxjs';
```

- [ ] **Step 2: Remove onInit auto-fetch from the store**

The `CategoriesStore` has a `withHooks({ onInit() { store.getCategories(); } })` that auto-fetches admin categories. This causes a double-fetch when `CaresComponent` also calls `getProCategories()`. Remove the auto-fetch and let consumers decide:

Change `withHooks` to:
```typescript
withHooks(() => ({}))
```

- [ ] **Step 3: Update CategoriesComponent to manually trigger fetch**

In `frontend/src/app/features/categories/categories.component.ts`, since the store no longer auto-fetches, add initialization:

```typescript
constructor() {
  this.store.getCategories();
}
```

- [ ] **Step 4: Run frontend tests**

Run: `cd frontend && npm test -- --include='**/categories/**' --watch=false`
Expected: Tests pass (no breaking changes)

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/features/categories/store/categories.store.ts \
       frontend/src/app/features/categories/categories.component.ts
git commit -m "feat: add pro CRUD methods to CategoriesStore, remove auto-fetch"
```

---

### Task 10: Fix legacy create-category dialog

**Files:**
- Modify: `frontend/src/app/features/categories/modals/create-category/create-category.ts`

The legacy dialog at `modals/create-category/create-category.ts` has bugs: imports `CreateCareRequest`, labels say "Nom du soin", description is required with minLength 10. The newer dialog at `modals/create/create-category.component.ts` is correct. Since `CategoriesComponent` imports from `modals/create/`, the legacy dialog is unused. **Delete the legacy dialog files.**

- [ ] **Step 1: Verify the legacy dialog is not imported anywhere**

Search for `create-category/create-category` imports in the codebase (excluding the file itself). Ensure no component references it.

- [ ] **Step 2: Delete legacy dialog files**

Delete the following files:
- `frontend/src/app/features/categories/modals/create-category/create-category.ts`
- `frontend/src/app/features/categories/modals/create-category/create-category.html` (if exists)
- `frontend/src/app/features/categories/modals/create-category/create-category.scss` (if exists)

- [ ] **Step 3: Update the newer dialog to support edit mode**

Modify `frontend/src/app/features/categories/modals/create/create-category.component.ts` to accept optional `MAT_DIALOG_DATA` for edit mode:

```typescript
import { Component, inject, OnInit } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { FormBuilder, FormGroup } from '@angular/forms';
import { CreateCategoryRequest, Category } from '../../models/categories.model';
import { ModalForm } from '../../../../shared/uis/modal-form/modal-form';
import { DynamicForm } from '../../../../shared/uis/dynamic-form/dynamic-form';
import { DynamicFormConfig } from '../../../../shared/models/form-field.model';
import { TranslocoService } from '@jsverse/transloco';

export interface CategoryDialogData {
  category?: Category;
}

@Component({
  selector: 'app-create-category',
  standalone: true,
  imports: [ModalForm, DynamicForm],
  template: `
    <modal-form
      [title]="dialogTitle"
      icon="category"
      iconColor="#fa8e8e"
      [saveLabel]="saveLabel"
      [saveDisabled]="categoryForm.invalid"
      (save)="onSave()"
      (cancel)="onCancel()">
      <dynamic-form [config]="formConfig" [formGroup]="categoryForm" />
    </modal-form>
  `
})
export class CreateCategoryComponent implements OnInit {
  private dialogRef = inject(MatDialogRef<CreateCategoryComponent>);
  private data: CategoryDialogData = inject(MAT_DIALOG_DATA, { optional: true }) ?? {};
  private fb = inject(FormBuilder);
  private i18n = inject(TranslocoService);

  categoryForm!: FormGroup;
  formConfig!: DynamicFormConfig;
  dialogTitle = '';
  saveLabel = '';

  get isEditMode(): boolean {
    return !!this.data.category;
  }

  ngOnInit(): void {
    this.dialogTitle = this.isEditMode
      ? this.i18n.translate('pro.categories.edit')
      : this.i18n.translate('pro.categories.add');
    this.saveLabel = this.isEditMode
      ? this.i18n.translate('common.save')
      : this.i18n.translate('pro.categories.add');
    this.categoryForm = this.fb.group({});
    this.initFormConfig();
  }

  private initFormConfig(): void {
    this.formConfig = {
      rows: [
        {
          fields: [
            {
              name: 'name',
              label: this.i18n.translate('categories.columns.name'),
              type: 'text',
              placeholder: 'Ex: Soins du visage',
              icon: 'label',
              required: true,
              width: 'full',
              value: this.data.category?.name ?? ''
            }
          ]
        },
        {
          fields: [
            {
              name: 'description',
              label: this.i18n.translate('categories.columns.description'),
              type: 'textarea',
              placeholder: '',
              icon: 'description',
              rows: 3,
              width: 'full',
              value: this.data.category?.description ?? ''
            }
          ]
        }
      ]
    };
  }

  onCancel(): void {
    this.dialogRef.close();
  }

  onSave(): void {
    if (this.categoryForm.valid) {
      const categoryData: CreateCategoryRequest = this.categoryForm.value;
      this.dialogRef.close(categoryData);
    } else {
      this.categoryForm.markAllAsTouched();
    }
  }
}
```

- [ ] **Step 4: Run frontend tests**

Run: `cd frontend && npm test -- --watch=false`
Expected: All tests pass

- [ ] **Step 5: Commit**

```bash
git add -A frontend/src/app/features/categories/modals/
git commit -m "feat: fix create-category dialog with edit support, remove legacy dialog"
```

---

### Task 11: Create Reassign Category Dialog

**Files:**
- Create: `frontend/src/app/features/categories/modals/reassign-category/reassign-category-dialog.component.ts`

- [ ] **Step 1: Create the dialog component**

```typescript
import { Component, inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef, MatDialogModule } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatSelectModule } from '@angular/material/select';
import { MatFormFieldModule } from '@angular/material/form-field';
import { FormsModule } from '@angular/forms';
import { TranslocoPipe } from '@jsverse/transloco';
import { Category } from '../../models/categories.model';

export interface ReassignCategoryDialogData {
  categoryId: number;
  categoryName: string;
  careCount: number;
  availableCategories: Category[];
}

@Component({
  selector: 'app-reassign-category-dialog',
  standalone: true,
  imports: [MatDialogModule, MatButtonModule, MatSelectModule, MatFormFieldModule, FormsModule, TranslocoPipe],
  template: `
    <h2 mat-dialog-title>{{ 'pro.categories.reassign.title' | transloco }}</h2>
    <mat-dialog-content>
      <p class="mb-4 text-sm text-neutral-600">
        {{ 'pro.categories.reassign.message' | transloco: { count: data.careCount } }}
      </p>
      <mat-form-field appearance="outline" class="w-full">
        <mat-label>{{ 'pro.categories.reassign.select' | transloco }}</mat-label>
        <mat-select [(value)]="selectedTargetId">
          @for (cat of data.availableCategories; track cat.id) {
            @if (cat.id !== data.categoryId) {
              <mat-option [value]="cat.id">{{ cat.name }}</mat-option>
            }
          }
        </mat-select>
      </mat-form-field>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button (click)="onCancel()">{{ 'common.cancel' | transloco }}</button>
      <button mat-flat-button color="warn" [disabled]="!selectedTargetId" (click)="onConfirm()">
        {{ 'pro.categories.reassign.confirm' | transloco }}
      </button>
    </mat-dialog-actions>
  `
})
export class ReassignCategoryDialogComponent {
  data: ReassignCategoryDialogData = inject(MAT_DIALOG_DATA);
  private dialogRef = inject(MatDialogRef<ReassignCategoryDialogComponent>);

  selectedTargetId: number | null = null;

  onCancel(): void {
    this.dialogRef.close();
  }

  onConfirm(): void {
    if (this.selectedTargetId) {
      this.dialogRef.close(this.selectedTargetId);
    }
  }
}
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/app/features/categories/modals/reassign-category/reassign-category-dialog.component.ts
git commit -m "feat: create reassign category dialog component"
```

---

### Task 12: Integrate category chips into CaresComponent

**Files:**
- Modify: `frontend/src/app/features/cares/cares.component.ts`
- Modify: `frontend/src/app/features/cares/cares.component.html`
- Modify: `frontend/src/app/features/cares/cares.component.scss`

This is the main integration task. Add category chips above the CrudTable with filtering, create/edit/delete actions.

- [ ] **Step 1: Update CaresComponent TypeScript**

Add imports, signals, and methods for category chip management:

```typescript
import { Component, computed, effect, inject, signal } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { CaresStore } from './store/cares.store';
import { CategoriesStore } from '../categories/store/categories.store';
import { CrudTable } from '../../shared/uis/crud-table/crud-table';
import { TableColumn, TableAction } from '../../shared/uis/crud-table/crud-table.models';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { CreateCare } from './modals/create/create-care.component';
import { Care, CareStatus } from './models/cares.model';
import { DeleteCareComponent } from './modals/delete/delete-care.component';
import { CaresService } from './services/cares.service';
import { CreateCategoryComponent } from '../categories/modals/create/create-category.component';
import { ReassignCategoryDialogComponent } from '../categories/modals/reassign-category/reassign-category-dialog.component';
import { Category } from '../categories/models/categories.model';

const CATEGORY_COLORS = [
  '#f4e1d2', // sable
  '#f9d5d3', // rose poudré
  '#dce8d2', // sauge
  '#d5e5f0', // brume
  '#f0dde4', // nacre rosé
  '#e8ddd0', // beige doré
  '#d8e2dc', // menthe douce
  '#f0e6cc', // vanille
];

@Component({
  selector: 'app-cares',
  standalone: true,
  imports: [CrudTable, TranslocoPipe, MatSnackBarModule, MatIconModule, MatMenuModule],
  templateUrl: './cares.component.html',
  styleUrl: './cares.component.scss',
  providers: [CaresStore, CategoriesStore]
})
export class CaresComponent {
  readonly store = inject(CaresStore);
  readonly categoriesStore = inject(CategoriesStore);
  private caresService = inject(CaresService);
  private dialog = inject(MatDialog);
  private snackBar = inject(MatSnackBar);
  private i18n = inject(TranslocoService);

  // Category filtering
  selectedCategoryId = signal<number | null>(null);

  filteredCares = computed(() => {
    const selectedId = this.selectedCategoryId();
    const cares = this.store.availableCares();
    return selectedId ? cares.filter(c => c.category.id === selectedId) : cares;
  });

  // ... keep existing columns, actions, and all care methods unchanged ...
```

Add the following new methods for category management:

```typescript
  getCategoryColor(categoryId: number): string {
    return CATEGORY_COLORS[categoryId % CATEGORY_COLORS.length];
  }

  onSelectCategory(categoryId: number): void {
    this.selectedCategoryId.update(current =>
      current === categoryId ? null : categoryId
    );
  }

  onAddCategory(): void {
    const dialogRef = this.dialog.open(CreateCategoryComponent, {
      width: '500px',
      disableClose: false,
      autoFocus: true
    });

    dialogRef.afterClosed().subscribe(result => {
      if (result) {
        this.categoriesStore.createProCategory(result);
        this.snackBar.open(this.i18n.translate('pro.categories.createSuccess'), 'OK', { duration: 3000 });
      }
    });
  }

  onEditCategory(category: Category): void {
    const dialogRef = this.dialog.open(CreateCategoryComponent, {
      width: '500px',
      disableClose: false,
      autoFocus: true,
      data: { category }
    });

    dialogRef.afterClosed().subscribe(result => {
      if (result) {
        this.categoriesStore.updateProCategory({ id: category.id, payload: result });
        this.snackBar.open(this.i18n.translate('pro.categories.updateSuccess'), 'OK', { duration: 3000 });
      }
    });
  }

  onDeleteCategory(category: Category): void {
    // Check if category has cares by counting filtered cares
    const caresInCategory = this.store.cares().filter(c => c.category.id === category.id);

    if (caresInCategory.length > 0) {
      const dialogRef = this.dialog.open(ReassignCategoryDialogComponent, {
        width: '450px',
        disableClose: true,
        data: {
          categoryId: category.id,
          categoryName: category.name,
          careCount: caresInCategory.length,
          availableCategories: this.categoriesStore.categories()
        }
      });

      dialogRef.afterClosed().subscribe(targetId => {
        if (targetId) {
          this.categoriesStore.deleteProCategory({ id: category.id, reassignTo: targetId });
          this.selectedCategoryId.set(null);
          // Refresh cares to update category references
          this.store.getCares();
          this.snackBar.open(this.i18n.translate('pro.categories.deleteSuccess'), 'OK', { duration: 3000 });
        }
      });
    } else {
      this.categoriesStore.deleteProCategory({ id: category.id });
      this.selectedCategoryId.set(null);
      this.snackBar.open(this.i18n.translate('pro.categories.deleteSuccess'), 'OK', { duration: 3000 });
    }
  }
```

**Important:** In the `CaresComponent` constructor or `onInit`, change the store initialization to call `getProCategories` instead of the admin `getCategories`. Override the `onInit` hook behavior by calling `this.categoriesStore.getProCategories()` in a constructor effect, or modify the store's `withHooks` to conditionally call the pro endpoint. The simplest approach: remove the `onInit` hook from `CategoriesStore` (since it always fetches admin categories) and let the component trigger the appropriate fetch. Add to `CaresComponent`:

```typescript
  constructor() {
    // Load categories from pro endpoint (overrides store's default admin fetch)
    this.categoriesStore.getProCategories();
  }
```

- [ ] **Step 2: Update CaresComponent template**

Replace the content of `cares.component.html`:

```html
<section class="content-center justify-center">
  <h2 class="sr-only">Prestations</h2>

  <!-- Category chips -->
  <div class="flex flex-wrap items-center gap-2 mb-4 px-1">
    @for (category of categoriesStore.categories(); track category.id) {
      <div class="category-chip-wrapper">
        <button
          type="button"
          class="category-chip"
          [class.selected]="selectedCategoryId() === category.id"
          [style.--chip-color]="getCategoryColor(category.id)"
          (click)="onSelectCategory(category.id)"
        >
          {{ category.name }}
        </button>
        <button
          type="button"
          class="chip-menu-trigger"
          [matMenuTriggerFor]="chipMenu"
          (click)="$event.stopPropagation()"
        >
          <mat-icon class="text-sm">more_vert</mat-icon>
        </button>
        <mat-menu #chipMenu="matMenu">
          <button mat-menu-item (click)="onEditCategory(category)">
            <mat-icon>edit</mat-icon>
            {{ 'pro.categories.edit' | transloco }}
          </button>
          <button mat-menu-item (click)="onDeleteCategory(category)">
            <mat-icon>delete</mat-icon>
            {{ 'pro.categories.delete' | transloco }}
          </button>
        </mat-menu>
      </div>
    }
    <button
      type="button"
      class="add-category-chip"
      (click)="onAddCategory()"
    >
      <mat-icon class="text-sm">add</mat-icon>
      {{ 'pro.categories.add' | transloco }}
    </button>
  </div>

  <crud-table
    [dataSource]="filteredCares()"
    [columns]="columns()"
    [actions]="actions()"
    [title]="('cares.title' | transloco)"
    [emptyMessage]="('table.empty' | transloco)"
    [searchPlaceholder]="('table.search.placeholder' | transloco)"
    [loading]="store.isPending()"
    [errorMessage]="store.error()"
    (addItem)="onAddCare()"
    (rowClick)="onViewCareDetails($event)"
  ></crud-table>
</section>
```

- [ ] **Step 3: Update CaresComponent SCSS**

Add to `cares.component.scss`:

```scss
.category-chip-wrapper {
  display: inline-flex;
  align-items: center;
  position: relative;
}

.category-chip {
  display: inline-flex;
  align-items: center;
  padding: 6px 12px;
  border-radius: 16px;
  font-size: 13px;
  font-weight: 500;
  border: 1.5px solid transparent;
  background-color: var(--chip-color);
  color: #3d3d3d;
  cursor: pointer;
  transition: all 150ms ease;

  &:hover {
    filter: brightness(0.95);
  }

  &.selected {
    border-color: #6d6d6d;
    box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
  }
}

.chip-menu-trigger {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  border: none;
  background: transparent;
  cursor: pointer;
  opacity: 0;
  transition: opacity 150ms ease;
  margin-left: -4px;

  .category-chip-wrapper:hover & {
    opacity: 0.6;
  }

  &:hover {
    opacity: 1 !important;
    background-color: rgba(0, 0, 0, 0.06);
  }

  mat-icon {
    font-size: 16px;
    width: 16px;
    height: 16px;
  }
}

.add-category-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  border-radius: 16px;
  font-size: 13px;
  font-weight: 500;
  border: 1.5px dashed #ccc;
  background: transparent;
  color: #888;
  cursor: pointer;
  transition: all 150ms ease;

  &:hover {
    border-color: #999;
    color: #666;
    background-color: rgba(0, 0, 0, 0.02);
  }

  mat-icon {
    font-size: 16px;
    width: 16px;
    height: 16px;
  }
}
```

- [ ] **Step 4: Run frontend tests**

Run: `cd frontend && npm test -- --watch=false`
Expected: Tests pass

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/features/cares/cares.component.ts \
       frontend/src/app/features/cares/cares.component.html \
       frontend/src/app/features/cares/cares.component.scss
git commit -m "feat: integrate category chips with filtering into cares page"
```

---

## Chunk 3: Tests & Cleanup

### Task 13: Write frontend tests for category chips in CaresComponent

**Files:**
- Modify or create: `frontend/src/app/features/cares/cares.component.spec.ts`

- [ ] **Step 1: Write tests for category chips**

Create/extend `cares.component.spec.ts` with tests covering:

1. **Chips render** — chips section appears with categories from the store
2. **Click to filter** — clicking a chip sets `selectedCategoryId`, table shows only matching cares
3. **Click again to deselect** — clicking same chip clears filter, all cares shown
4. **"+ Add" chip** — clicking opens CreateCategoryComponent dialog
5. **Context menu edit** — clicking edit opens dialog with category data
6. **Context menu delete (no cares)** — calls `deleteProCategory` directly
7. **Context menu delete (with cares)** — opens ReassignCategoryDialogComponent

Use `TestBed` with:
- `provideZonelessChangeDetection()`
- `provideHttpClient()`
- `provideRouter([])`
- `provideNoopAnimations()`
- Mock stores via `providers` overrides

- [ ] **Step 2: Run tests**

Run: `cd frontend && npm test -- --include='**/cares.component.spec.ts' --watch=false`
Expected: All tests pass

- [ ] **Step 3: Commit**

```bash
git add frontend/src/app/features/cares/cares.component.spec.ts
git commit -m "test: add category chip tests in CaresComponent"
```

---

### Task 14: Write frontend tests for ReassignCategoryDialog

**Files:**
- Create: `frontend/src/app/features/categories/modals/reassign-category/reassign-category-dialog.component.spec.ts`

- [ ] **Step 1: Write tests**

Test coverage:
1. **Dialog renders** — title, message with count, select dropdown visible
2. **Categories listed** — dropdown shows all categories except the one being deleted
3. **Confirm disabled** — button disabled when no target selected
4. **Confirm enabled** — button enabled after selecting a target
5. **Cancel closes** — cancel button closes dialog without result
6. **Confirm closes with targetId** — confirm returns selected category ID

Use `TestBed` with `MAT_DIALOG_DATA` and `MatDialogRef` mocks.

- [ ] **Step 2: Run tests**

Run: `cd frontend && npm test -- --include='**/reassign-category-dialog.component.spec.ts' --watch=false`
Expected: All tests pass

- [ ] **Step 3: Commit**

```bash
git add frontend/src/app/features/categories/modals/reassign-category/reassign-category-dialog.component.spec.ts
git commit -m "test: add reassign category dialog tests"
```

---

### Task 15: Final integration verification

- [ ] **Step 1: Run all backend tests**

Run: `cd backend && mvn test -pl . -q`
Expected: All tests pass

- [ ] **Step 2: Run all frontend tests**

Run: `cd frontend && npm test -- --watch=false`
Expected: All tests pass

- [ ] **Step 3: Run frontend build**

Run: `cd frontend && npm run build`
Expected: Build succeeds with no errors

- [ ] **Step 4: Verify no missing translations**

Check that all `pro.categories.*` keys are present in both `fr.json` and `en.json`.
