# Salon Discovery Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the `/discover` placeholder with a real salon discovery page backed by a public API, using denormalized category fields on the Tenant entity for efficient cross-tenant queries.

**Architecture:** Add `categoryNames`/`categorySlugs` columns to Tenant (central schema). Sync them on every category CRUD. New `GET /api/public/salons` endpoint queries central schema only. Frontend rewrites the DiscoverPage with salon cards, category chips, and search.

**Tech Stack:** Spring Boot 3.5.4 / Java 21, Angular 20 (standalone, zoneless, signals), Transloco i18n

**Spec:** `docs/superpowers/specs/2026-03-23-salon-discovery-design.md`

---

## Chunk 1: Backend — Denormalized Fields

### Task 1: Add categoryNames and categorySlugs to Tenant entity

**Files:**
- Modify: `backend/src/main/java/com/fleurdecoquillage/app/tenant/domain/Tenant.java`

- [ ] **Step 1: Add columns**

Add after the `logoPath` field:

```java
@Column(name = "category_names", length = 1000)
private String categoryNames;

@Column(name = "category_slugs", length = 1000)
private String categorySlugs;
```

- [ ] **Step 2: Commit**

```bash
git add backend/src/main/java/com/fleurdecoquillage/app/tenant/domain/Tenant.java
git commit -m "feat: add categoryNames and categorySlugs denormalized fields to Tenant"
```

---

### Task 2: Add sync method to CategoryService

**Files:**
- Modify: `backend/src/main/java/com/fleurdecoquillage/app/category/app/CategoryService.java`

- [ ] **Step 1: Add dependencies and sync method**

Update the constructor to inject `TenantRepository` and add a `syncTenantCategories()` method:

```java
package com.fleurdecoquillage.app.category.app;

import com.fleurdecoquillage.app.care.repo.CareRepository;
import com.fleurdecoquillage.app.category.domain.Category;
import com.fleurdecoquillage.app.category.repo.CategoryRepository;
import com.fleurdecoquillage.app.category.web.dto.CategoryRequest;
import com.fleurdecoquillage.app.category.web.dto.CategoryResponse;
import com.fleurdecoquillage.app.category.web.dto.DeleteCategoryResponse;
import com.fleurdecoquillage.app.category.web.mapper.CategoryMapper;
import com.fleurdecoquillage.app.multitenancy.TenantContext;
import com.fleurdecoquillage.app.tenant.app.SlugUtils;
import com.fleurdecoquillage.app.tenant.repo.TenantRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class CategoryService {
    private final CategoryRepository repo;
    private final CareRepository careRepository;
    private final TenantRepository tenantRepository;

    public CategoryService(CategoryRepository repo, CareRepository careRepository, TenantRepository tenantRepository) {
        this.repo = repo;
        this.careRepository = careRepository;
        this.tenantRepository = tenantRepository;
    }

    // ... existing methods unchanged ...

    private void syncTenantCategories() {
        String slug = TenantContext.getCurrentTenant();
        if (slug == null) return;

        tenantRepository.findBySlug(slug).ifPresent(tenant -> {
            List<Category> categories = repo.findAll();
            String names = categories.stream()
                    .map(Category::getName)
                    .sorted()
                    .collect(Collectors.joining(", "));
            String slugs = categories.stream()
                    .map(c -> SlugUtils.toSlug(c.getName()))
                    .sorted()
                    .collect(Collectors.joining(","));
            tenant.setCategoryNames(names.isEmpty() ? null : names);
            tenant.setCategorySlugs(slugs.isEmpty() ? null : slugs);
            tenantRepository.save(tenant);
        });
    }
}
```

- [ ] **Step 2: Call syncTenantCategories after each pro CRUD operation**

Add `syncTenantCategories()` call at the end of:
- `create()` method — after `repo.save()`
- `update()` method — after `repo.save()`
- `deleteWithReassignment()` method — after `repo.deleteById()`

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/fleurdecoquillage/app/category/app/CategoryService.java
git commit -m "feat: sync tenant categoryNames/categorySlugs on category CRUD"
```

---

## Chunk 2: Backend — Discovery Endpoint

### Task 3: Add repository query methods

**Files:**
- Modify: `backend/src/main/java/com/fleurdecoquillage/app/tenant/repo/TenantRepository.java`

- [ ] **Step 1: Add finder methods**

```java
package com.fleurdecoquillage.app.tenant.repo;

import com.fleurdecoquillage.app.tenant.domain.Tenant;
import com.fleurdecoquillage.app.tenant.domain.TenantStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TenantRepository extends JpaRepository<Tenant, Long> {
    Optional<Tenant> findBySlug(String slug);
    Optional<Tenant> findByOwnerId(Long ownerId);
    boolean existsBySlug(String slug);

    List<Tenant> findByStatus(TenantStatus status);
    List<Tenant> findByStatusAndCategorySlugsContaining(TenantStatus status, String slug);
    List<Tenant> findByStatusAndNameContainingIgnoreCase(TenantStatus status, String name);
}
```

- [ ] **Step 2: Commit**

```bash
git add backend/src/main/java/com/fleurdecoquillage/app/tenant/repo/TenantRepository.java
git commit -m "feat: add discovery query methods to TenantRepository"
```

---

### Task 4: Create SalonCardResponse DTO and PublicDiscoveryController

**Files:**
- Create: `backend/src/main/java/com/fleurdecoquillage/app/tenant/web/dto/SalonCardResponse.java`
- Create: `backend/src/main/java/com/fleurdecoquillage/app/tenant/web/PublicDiscoveryController.java`

- [ ] **Step 1: Create DTO**

```java
package com.fleurdecoquillage.app.tenant.web.dto;

public record SalonCardResponse(
        String name,
        String slug,
        String description,
        String logoUrl,
        String categoryNames
) {}
```

- [ ] **Step 2: Create controller**

```java
package com.fleurdecoquillage.app.tenant.web;

import com.fleurdecoquillage.app.tenant.domain.Tenant;
import com.fleurdecoquillage.app.tenant.domain.TenantStatus;
import com.fleurdecoquillage.app.tenant.repo.TenantRepository;
import com.fleurdecoquillage.app.tenant.web.dto.SalonCardResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/public/salons")
public class PublicDiscoveryController {

    private final TenantRepository tenantRepository;

    public PublicDiscoveryController(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    @GetMapping
    public List<SalonCardResponse> discover(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String q) {

        List<Tenant> tenants;

        if (category != null && !category.isBlank()) {
            tenants = tenantRepository.findByStatusAndCategorySlugsContaining(TenantStatus.ACTIVE, category);
        } else if (q != null && !q.isBlank()) {
            tenants = tenantRepository.findByStatusAndNameContainingIgnoreCase(TenantStatus.ACTIVE, q.trim());
        } else {
            tenants = tenantRepository.findByStatus(TenantStatus.ACTIVE);
        }

        return tenants.stream().map(this::toCard).toList();
    }

    private SalonCardResponse toCard(Tenant t) {
        String logoUrl = null;
        if (t.getLogoPath() != null) {
            String filename = t.getLogoPath().substring(t.getLogoPath().lastIndexOf('/') + 1);
            logoUrl = "/api/images/tenant/" + t.getId() + "/" + filename;
        }
        // Truncate description for card display
        String desc = t.getDescription();
        if (desc != null && desc.length() > 200) {
            desc = desc.substring(0, 200) + "...";
        }
        return new SalonCardResponse(t.getName(), t.getSlug(), desc, logoUrl, t.getCategoryNames());
    }
}
```

- [ ] **Step 3: Ensure `/api/public/**` is publicly accessible in SecurityConfig**

Check that `SecurityConfig` allows unauthenticated access to `/api/public/**`. If not, add it alongside `/api/salon/**`.

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/fleurdecoquillage/app/tenant/web/dto/SalonCardResponse.java \
       backend/src/main/java/com/fleurdecoquillage/app/tenant/web/PublicDiscoveryController.java
git commit -m "feat: add GET /api/public/salons discovery endpoint"
```

---

## Chunk 3: Frontend — Discovery Page

### Task 5: Add i18n keys

**Files:**
- Modify: `frontend/public/i18n/fr.json`
- Modify: `frontend/public/i18n/en.json`

- [ ] **Step 1: Update French discover keys**

Replace the existing `"discover"` block with:

```json
"discover": {
  "title": "Découvrir les salons",
  "search": "Rechercher un salon...",
  "noResults": "Aucun salon trouvé",
  "noResultsHint": "Essayez une autre catégorie",
  "resultsCount": "{{count}} salon(s) trouvé(s)",
  "allCategories": "Tous",
  "backHome": "Retour à l'accueil"
}
```

- [ ] **Step 2: Update English discover keys**

Replace the existing `"discover"` block with:

```json
"discover": {
  "title": "Discover salons",
  "search": "Search for a salon...",
  "noResults": "No salons found",
  "noResultsHint": "Try another category",
  "resultsCount": "{{count}} salon(s) found",
  "allCategories": "All",
  "backHome": "Back to home"
}
```

- [ ] **Step 3: Commit**

```bash
git add frontend/public/i18n/fr.json frontend/public/i18n/en.json
git commit -m "feat: update i18n keys for salon discovery page"
```

---

### Task 6: Create DiscoveryService

**Files:**
- Create: `frontend/src/app/features/discovery/discovery.service.ts`
- Create: `frontend/src/app/features/discovery/discovery.model.ts`

- [ ] **Step 1: Create model**

```typescript
export interface SalonCard {
  name: string;
  slug: string;
  description: string | null;
  logoUrl: string | null;
  categoryNames: string | null;
}
```

- [ ] **Step 2: Create service**

```typescript
import { Injectable, inject, PLATFORM_ID } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { isPlatformBrowser } from '@angular/common';
import { API_BASE_URL } from '../../core/config/api-base-url.token';
import { SalonCard } from './discovery.model';

@Injectable({ providedIn: 'root' })
export class DiscoveryService {
  private http = inject(HttpClient);
  private apiBaseUrl = inject(API_BASE_URL);
  private isBrowser = isPlatformBrowser(inject(PLATFORM_ID));

  searchSalons(category?: string | null, q?: string | null): Observable<SalonCard[]> {
    const params: Record<string, string> = {};
    if (category) params['category'] = category;
    if (q) params['q'] = q;

    return this.http
      .get<SalonCard[]>(`${this.apiBaseUrl}/api/public/salons`, { params })
      .pipe(map((salons) => salons.map((s) => this.transformLogoUrl(s))));
  }

  private transformLogoUrl(salon: SalonCard): SalonCard {
    if (!salon.logoUrl || !this.isBrowser) return salon;
    if (salon.logoUrl.startsWith('http')) return salon;
    return { ...salon, logoUrl: `${this.apiBaseUrl}${salon.logoUrl}` };
  }
}
```

- [ ] **Step 3: Commit**

```bash
git add frontend/src/app/features/discovery/
git commit -m "feat: add DiscoveryService and SalonCard model"
```

---

### Task 7: Rewrite DiscoverPageComponent

**Files:**
- Rewrite: `frontend/src/app/pages/discover/discover-page.component.ts`

- [ ] **Step 1: Replace the component**

```typescript
import { Component, computed, effect, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { TranslocoPipe } from '@jsverse/transloco';
import { map, switchMap } from 'rxjs';
import { DiscoveryService } from '../../features/discovery/discovery.service';
import { SalonCard } from '../../features/discovery/discovery.model';

interface CategoryFilter {
  name: string;
  slug: string;
  emoji: string;
  color: string;
}

const CATEGORIES: CategoryFilter[] = [
  { name: 'Soins visage', slug: 'soins-visage', emoji: '💆', color: '#f4e1d2' },
  { name: 'Ongles', slug: 'ongles', emoji: '💅', color: '#f9d5d3' },
  { name: 'Coiffure', slug: 'coiffure', emoji: '✂️', color: '#dce8d2' },
  { name: 'Épilation', slug: 'epilation', emoji: '🧖', color: '#d5e5f0' },
];

const GRADIENTS = [
  'linear-gradient(135deg, #e8d5c4, #f0e0d0)',
  'linear-gradient(135deg, #d5e0d2, #e0ead5)',
  'linear-gradient(135deg, #d5d5e8, #e0e0f0)',
  'linear-gradient(135deg, #e0d5d8, #f0e5e8)',
  'linear-gradient(135deg, #f0e6cc, #f5ecd5)',
];

@Component({
  selector: 'app-discover-page',
  standalone: true,
  imports: [TranslocoPipe],
  templateUrl: './discover-page.component.html',
  styleUrl: './discover-page.component.scss',
})
export class DiscoverPageComponent {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private discoveryService = inject(DiscoveryService);

  readonly categories = CATEGORIES;
  readonly searchQuery = signal('');

  private params = toSignal(
    this.route.queryParamMap.pipe(
      map((p) => ({ category: p.get('category'), q: p.get('q') }))
    ),
    { initialValue: { category: null as string | null, q: null as string | null } }
  );

  readonly selectedCategory = computed(() => this.params().category);

  readonly salons = toSignal(
    this.route.queryParamMap.pipe(
      switchMap((p) =>
        this.discoveryService.searchSalons(p.get('category'), p.get('q'))
      )
    ),
    { initialValue: [] as SalonCard[] }
  );

  getGradient(index: number): string {
    return GRADIENTS[index % GRADIENTS.length];
  }

  onCategoryClick(slug: string | null): void {
    this.router.navigate(['/discover'], {
      queryParams: slug ? { category: slug } : {},
    });
  }

  onSearch(): void {
    const q = this.searchQuery().trim();
    this.router.navigate(['/discover'], {
      queryParams: q ? { q } : {},
    });
  }

  onSalonClick(slug: string): void {
    this.router.navigate(['/salon', slug]);
  }

  truncate(text: string | null, max: number): string {
    if (!text) return '';
    // Strip HTML tags for card display
    const plain = text.replace(/<[^>]*>/g, '');
    return plain.length > max ? plain.substring(0, max) + '...' : plain;
  }
}
```

- [ ] **Step 2: Create template**

Create `frontend/src/app/pages/discover/discover-page.component.html`:

```html
<div class="discover-page">
  <!-- Header -->
  <section class="discover-header">
    <h1 class="discover-title">{{ 'discover.title' | transloco }}</h1>
    <form class="discover-search" (submit)="onSearch(); $event.preventDefault()">
      <span class="search-icon">🔍</span>
      <input
        type="text"
        class="search-input"
        [placeholder]="'discover.search' | transloco"
        [value]="searchQuery()"
        (input)="searchQuery.set($any($event.target).value)"
      />
    </form>
  </section>

  <!-- Category chips -->
  <section class="category-chips">
    <button
      type="button"
      class="chip"
      [class.selected]="!selectedCategory()"
      [style.background-color]="'#f0f0f0'"
      (click)="onCategoryClick(null)"
    >
      {{ 'discover.allCategories' | transloco }}
    </button>
    @for (cat of categories; track cat.slug) {
      <button
        type="button"
        class="chip"
        [class.selected]="selectedCategory() === cat.slug"
        [style.background-color]="cat.color"
        (click)="onCategoryClick(cat.slug)"
      >
        {{ cat.emoji }} {{ cat.name }}
      </button>
    }
  </section>

  <!-- Results -->
  @if (salons().length > 0) {
    <p class="results-count">{{ salons().length }} salon(s)</p>
    <section class="salon-grid">
      @for (salon of salons(); track salon.slug; let i = $index) {
        <button type="button" class="salon-card" (click)="onSalonClick(salon.slug)">
          <div class="salon-card-header" [style.background]="salon.logoUrl ? 'url(' + salon.logoUrl + ') center/cover' : getGradient(i)">
            @if (!salon.logoUrl) {
              <div class="salon-card-logo">{{ salon.name.charAt(0) }}</div>
            }
          </div>
          <div class="salon-card-body">
            <span class="salon-card-name">{{ salon.name }}</span>
            <p class="salon-card-desc">{{ truncate(salon.description, 120) }}</p>
            @if (salon.categoryNames) {
              <div class="salon-card-categories">
                @for (catName of salon.categoryNames.split(', '); track catName) {
                  <span class="salon-card-chip">{{ catName }}</span>
                }
              </div>
            }
          </div>
        </button>
      }
    </section>
  } @else {
    <section class="empty-state">
      <span class="empty-icon">🔍</span>
      <p class="empty-title">{{ 'discover.noResults' | transloco }}</p>
      <p class="empty-hint">{{ 'discover.noResultsHint' | transloco }}</p>
    </section>
  }
</div>
```

- [ ] **Step 3: Create styles**

Create `frontend/src/app/pages/discover/discover-page.component.scss`:

```scss
.discover-page {
  max-width: 900px;
  margin: 0 auto;
  padding: 24px;
}

// Header
.discover-header {
  text-align: center;
  margin-bottom: 24px;
}

.discover-title {
  font-size: 1.5rem;
  font-weight: 300;
  letter-spacing: 0.5px;
  color: #333;
  margin-bottom: 16px;
}

.discover-search {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  background: white;
  border-radius: 24px;
  padding: 10px 20px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
  width: 100%;
  max-width: 380px;

  .search-icon {
    font-size: 14px;
    flex-shrink: 0;
  }

  .search-input {
    border: none;
    outline: none;
    font-size: 13px;
    color: #333;
    width: 100%;
    background: transparent;

    &::placeholder {
      color: #999;
    }
  }
}

// Category chips
.category-chips {
  display: flex;
  gap: 8px;
  overflow-x: auto;
  padding-bottom: 16px;
  margin-bottom: 8px;

  scrollbar-width: none;
  &::-webkit-scrollbar {
    display: none;
  }
}

.chip {
  flex: 0 0 auto;
  padding: 8px 16px;
  border-radius: 20px;
  font-size: 12px;
  font-weight: 500;
  color: #555;
  border: 2px solid transparent;
  cursor: pointer;
  transition: border-color 150ms ease;

  &.selected {
    border-color: #a08060;
    color: #333;
  }

  &:hover:not(.selected) {
    filter: brightness(0.97);
  }
}

// Results
.results-count {
  font-size: 12px;
  color: #999;
  margin-bottom: 12px;
}

// Salon grid
.salon-grid {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.salon-card {
  background: white;
  border-radius: 16px;
  overflow: hidden;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
  border: none;
  cursor: pointer;
  text-align: left;
  width: 100%;
  transition: box-shadow 150ms ease;

  &:hover {
    box-shadow: 0 4px 20px rgba(0, 0, 0, 0.1);
  }
}

.salon-card-header {
  height: 100px;
  position: relative;
}

.salon-card-logo {
  position: absolute;
  bottom: -20px;
  left: 16px;
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background: white;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  font-weight: 500;
  color: #c06;
}

.salon-card-body {
  padding: 28px 16px 16px;
}

.salon-card-name {
  display: block;
  font-size: 15px;
  font-weight: 500;
  color: #333;
}

.salon-card-desc {
  font-size: 12px;
  color: #666;
  line-height: 1.4;
  margin: 8px 0 0;
}

.salon-card-categories {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  margin-top: 10px;
}

.salon-card-chip {
  padding: 3px 10px;
  border-radius: 12px;
  font-size: 10px;
  background: #f5f0ed;
  color: #666;
}

// Empty state
.empty-state {
  text-align: center;
  padding: 60px 20px;
}

.empty-icon {
  font-size: 40px;
  display: block;
  margin-bottom: 12px;
}

.empty-title {
  font-size: 16px;
  font-weight: 300;
  color: #555;
  margin: 0;
}

.empty-hint {
  font-size: 13px;
  color: #999;
  margin: 6px 0 0;
}
```

- [ ] **Step 4: Run TypeScript check**

Run: `cd frontend && npx tsc --noEmit`
Expected: No errors

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/pages/discover/
git commit -m "feat: rewrite DiscoverPage with salon cards, category filters, and search"
```

---

## Chunk 4: Backend Security & Verification

### Task 8: Ensure /api/public/** is permitted in SecurityConfig

**Files:**
- Modify: `backend/src/main/java/com/fleurdecoquillage/app/config/SecurityConfig.java` (if needed)

- [ ] **Step 1: Check SecurityConfig**

Search for `permitAll` or `requestMatchers` in SecurityConfig to verify `/api/public/**` is permitted. If only `/api/salon/**` is listed, add `/api/public/**` alongside it.

- [ ] **Step 2: Commit if changed**

```bash
git add backend/src/main/java/com/fleurdecoquillage/app/config/SecurityConfig.java
git commit -m "fix: permit /api/public/** in SecurityConfig"
```

---

### Task 9: Final verification

- [ ] **Step 1: Run TypeScript check**

Run: `cd frontend && npx tsc --noEmit`
Expected: No errors

- [ ] **Step 2: Verify translations**

Check that all `discover.*` keys exist in both `fr.json` and `en.json`.

- [ ] **Step 3: Verify routing**

Check that `/discover` route in `app.routes.ts` lazy-loads `DiscoverPageComponent`.
