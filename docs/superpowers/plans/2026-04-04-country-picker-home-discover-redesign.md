# Country Picker + Home & Discover Redesign — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a country field with autocomplete+flag picker to salon profile, redesign home page as "vitrine immersive" with mini-map + recent posts, and redesign discover page as Airbnb-style split view with map.

**Architecture:** Three independent features sharing the country data: (1) backend adds `addressCountry` column to Tenant + includes it in fullAddress for geocoding, (2) frontend country picker component used in salon profile, (3) home and discover page redesign using Leaflet maps and new layouts.

**Tech Stack:** Angular 20 (standalone, signals, zoneless), Angular Material autocomplete, Leaflet/OpenStreetMap, Nominatim geocoding, Spring Boot 3 / JPA.

---

## File Structure

### Task 1 — Backend: country field
- Modify: `backend/src/main/java/com/prettyface/app/tenant/domain/Tenant.java`
- Modify: `backend/src/main/java/com/prettyface/app/tenant/web/dto/UpdateTenantRequest.java`
- Modify: `backend/src/main/java/com/prettyface/app/tenant/web/dto/TenantResponse.java`
- Modify: `backend/src/main/java/com/prettyface/app/tenant/web/mapper/TenantMapper.java`
- Modify: `backend/src/main/java/com/prettyface/app/tenant/app/TenantService.java`
- Modify: `backend/src/main/java/com/prettyface/app/tenant/web/PublicDiscoveryController.java`

### Task 2 — Frontend: country picker component
- Create: `frontend/src/app/shared/uis/country-picker/country-picker.component.ts`
- Create: `frontend/src/app/shared/uis/country-picker/countries.ts`

### Task 3 — Frontend: integrate country picker into salon profile
- Modify: `frontend/src/app/features/salon-profile/models/salon-profile.model.ts`
- Modify: `frontend/src/app/features/salon-profile/salon-profile.component.ts`
- Modify: `frontend/src/app/features/salon-profile/salon-profile.component.html`
- Modify: `frontend/public/i18n/fr.json`
- Modify: `frontend/public/i18n/en.json`

### Task 4 — Frontend: redesign discover page (split view Airbnb)
- Rewrite: `frontend/src/app/pages/discover/discover-page.component.ts`
- Rewrite: `frontend/src/app/pages/discover/discover-page.component.html`
- Rewrite: `frontend/src/app/pages/discover/discover-page.component.scss`

### Task 5 — Frontend: redesign home page (vitrine immersive)
- Rewrite: `frontend/src/app/pages/home/home.ts`
- Rewrite: `frontend/src/app/pages/home/home.html`
- Rewrite: `frontend/src/app/pages/home/home.scss`
- Modify: `frontend/public/i18n/fr.json`
- Modify: `frontend/public/i18n/en.json`

### Task 6 — Backend: recent posts endpoint (for home page)
- Create: `backend/src/main/java/com/prettyface/app/post/web/PublicPostController.java`
- Create: `backend/src/main/java/com/prettyface/app/post/web/dto/RecentPostResponse.java`
- Modify: `backend/src/main/java/com/prettyface/app/post/repo/PostRepository.java`
- Modify: `frontend/src/app/features/posts/posts.service.ts`
- Modify: `frontend/src/app/features/posts/posts.model.ts`

---

## Task 1: Backend — Add country field to Tenant

### Files:
- Modify: `backend/src/main/java/com/prettyface/app/tenant/domain/Tenant.java`
- Modify: `backend/src/main/java/com/prettyface/app/tenant/web/dto/UpdateTenantRequest.java`
- Modify: `backend/src/main/java/com/prettyface/app/tenant/web/dto/TenantResponse.java`
- Modify: `backend/src/main/java/com/prettyface/app/tenant/web/mapper/TenantMapper.java`
- Modify: `backend/src/main/java/com/prettyface/app/tenant/app/TenantService.java`
- Modify: `backend/src/main/java/com/prettyface/app/tenant/web/PublicDiscoveryController.java`

- [ ] **Step 1: Add column to Tenant entity**

In `Tenant.java`, add after the `addressCity` field (line 63):

```java
@Column(name = "address_country", length = 2)
private String addressCountry;
```

This stores the ISO 3166-1 alpha-2 code (e.g. "FR", "BE", "CH").

- [ ] **Step 2: Add field to UpdateTenantRequest DTO**

In `UpdateTenantRequest.java`, add after `addressCity`:

```java
@Size(max = 2) String addressCountry,
```

The full record becomes:
```java
public record UpdateTenantRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 50000) String description,
        String logo,
        String heroImage,
        @Size(max = 255) String addressStreet,
        @Size(max = 10) String addressPostalCode,
        @Size(max = 100) String addressCity,
        @Size(max = 2) String addressCountry,
        @Size(max = 20) String phone,
        @Size(max = 255) String contactEmail,
        @Size(max = 14) String siret,
        Boolean employeesEnabled,
        Integer annualLeaveDays
) {}
```

- [ ] **Step 3: Add field to TenantResponse DTO**

In `TenantResponse.java`, add after `addressCity`:

```java
String addressCountry,
```

Full record:
```java
public record TenantResponse(
        Long id,
        String name,
        String slug,
        String description,
        String logoUrl,
        String heroImageUrl,
        String addressStreet,
        String addressPostalCode,
        String addressCity,
        String addressCountry,
        String phone,
        String contactEmail,
        String siret,
        LocalDateTime updatedAt,
        Boolean employeesEnabled,
        Integer annualLeaveDays
) {}
```

- [ ] **Step 4: Update TenantMapper**

In `TenantMapper.toResponse()`, add `tenant.getAddressCountry()` after `tenant.getAddressCity()` in the constructor call.

- [ ] **Step 5: Update TenantService**

In `TenantService.updateProfile()`, add after line 90 (`tenant.setAddressCity(request.addressCity())`):

```java
tenant.setAddressCountry(request.addressCountry());
```

- [ ] **Step 6: Include country in fullAddress for geocoding**

In `PublicDiscoveryController.buildFullAddress()`, append the country name at the end. Replace the method with:

```java
private String buildFullAddress(Tenant t) {
    StringBuilder sb = new StringBuilder();
    if (t.getAddressStreet() != null && !t.getAddressStreet().isBlank())
        sb.append(t.getAddressStreet());
    if (t.getAddressPostalCode() != null && !t.getAddressPostalCode().isBlank()) {
        if (!sb.isEmpty()) sb.append(", ");
        sb.append(t.getAddressPostalCode());
    }
    if (t.getAddressCity() != null && !t.getAddressCity().isBlank()) {
        if (!sb.isEmpty()) sb.append(" ");
        sb.append(t.getAddressCity());
    }
    if (t.getAddressCountry() != null && !t.getAddressCountry().isBlank()) {
        if (!sb.isEmpty()) sb.append(", ");
        sb.append(t.getAddressCountry());
    }
    return sb.isEmpty() ? null : sb.toString();
}
```

- [ ] **Step 7: Compile and verify**

Run: `cd backend && ./mvnw compile`
Expected: BUILD SUCCESS

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/com/prettyface/app/tenant/
git commit -m "feat: add addressCountry field to Tenant entity and DTOs"
```

---

## Task 2: Frontend — Country picker component

### Files:
- Create: `frontend/src/app/shared/uis/country-picker/countries.ts`
- Create: `frontend/src/app/shared/uis/country-picker/country-picker.component.ts`

- [ ] **Step 1: Create countries data file**

Create `frontend/src/app/shared/uis/country-picker/countries.ts` with the full list of countries. Each entry has `code` (ISO 3166-1 alpha-2), `name` (French), `nameEn` (English), `flag` (emoji). Include ~250 countries. Put commonly used ones (FR, BE, CH, LU, DE, ES, IT, PT, GB, NL, MA, TN, DZ, SN, CI, CA, US) in a `POPULAR_COUNTRY_CODES` array.

```typescript
export interface Country {
  code: string;
  name: string;
  nameEn: string;
  flag: string;
}

export const POPULAR_COUNTRY_CODES = ['FR', 'BE', 'CH', 'LU', 'DE', 'ES', 'IT', 'GB', 'MA', 'CA'];

export const COUNTRIES: Country[] = [
  { code: 'AF', name: 'Afghanistan', nameEn: 'Afghanistan', flag: '🇦🇫' },
  { code: 'AL', name: 'Albanie', nameEn: 'Albania', flag: '🇦🇱' },
  { code: 'DZ', name: 'Algérie', nameEn: 'Algeria', flag: '🇩🇿' },
  // ... full list of ~250 countries
  // The implementing agent should generate the complete list
];
```

- [ ] **Step 2: Create country picker component**

Create `frontend/src/app/shared/uis/country-picker/country-picker.component.ts` — a standalone component using `mat-form-field` + `mat-autocomplete`. Design: Option C (compact autocomplete with inline flag).

Key behavior:
- Input: `countryCode` signal (ISO code, e.g. "FR")
- Output: `countryCodeChange` event emitter
- Shows the flag emoji inside the mat-form-field prefix
- As user types, filters countries by name (in current language) or code
- Selecting a country updates the flag prefix and emits the code
- Uses `TranslocoService` to pick `name` or `nameEn` based on active language

```typescript
import { Component, input, output, signal, computed, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { COUNTRIES, Country } from './countries';

@Component({
  selector: 'app-country-picker',
  standalone: true,
  imports: [FormsModule, MatFormFieldModule, MatInputModule, MatAutocompleteModule, TranslocoPipe],
  template: `
    <mat-form-field appearance="outline" class="full-width">
      <mat-label>{{ 'pro.salon.addressCountry' | transloco }}</mat-label>
      <span matPrefix class="country-flag">{{ selectedFlag() }}</span>
      <input
        matInput
        [ngModel]="searchText()"
        (ngModelChange)="onSearchChange($event)"
        [matAutocomplete]="auto"
        name="countrySearch"
      />
      <mat-autocomplete
        #auto="matAutocomplete"
        (optionSelected)="onSelect($event.option.value)"
        [displayWith]="displayFn"
      >
        @for (country of filteredCountries(); track country.code) {
          <mat-option [value]="country.code">
            <span class="option-flag">{{ country.flag }}</span>
            <span class="option-name">{{ getCountryName(country) }}</span>
            <span class="option-code">{{ country.code }}</span>
          </mat-option>
        }
      </mat-autocomplete>
    </mat-form-field>
  `,
  styles: `
    .country-flag {
      font-size: 22px;
      line-height: 1;
      margin-right: 8px;
    }
    .option-flag {
      font-size: 18px;
      margin-right: 8px;
    }
    .option-name {
      font-size: 13px;
    }
    .option-code {
      font-size: 11px;
      color: #999;
      margin-left: auto;
      padding-left: 8px;
    }
    mat-option {
      display: flex;
      align-items: center;
    }
    .full-width {
      width: 100%;
    }
  `,
})
export class CountryPickerComponent {
  private readonly transloco = inject(TranslocoService);

  readonly countryCode = input<string | null>(null);
  readonly countryCodeChange = output<string | null>();

  readonly searchText = signal('');
  private readonly countries = COUNTRIES;

  readonly selectedFlag = computed(() => {
    const code = this.countryCode();
    if (!code) return '🌍';
    const country = this.countries.find(c => c.code === code);
    return country?.flag ?? '🌍';
  });

  readonly filteredCountries = computed(() => {
    const query = this.searchText().toLowerCase().trim();
    if (!query) return this.countries.slice(0, 30);
    return this.countries.filter(c =>
      c.name.toLowerCase().includes(query) ||
      c.nameEn.toLowerCase().includes(query) ||
      c.code.toLowerCase().includes(query)
    ).slice(0, 30);
  });

  constructor() {
    // Sync initial value
    effect(() => {
      const code = this.countryCode();
      if (code) {
        const country = this.countries.find(c => c.code === code);
        if (country) {
          this.searchText.set(this.getCountryName(country));
        }
      }
    });
  }

  getCountryName(country: Country): string {
    return this.transloco.getActiveLang() === 'fr' ? country.name : country.nameEn;
  }

  onSearchChange(value: string): void {
    this.searchText.set(value);
  }

  onSelect(code: string): void {
    const country = this.countries.find(c => c.code === code);
    if (country) {
      this.searchText.set(this.getCountryName(country));
      this.countryCodeChange.emit(code);
    }
  }

  displayFn = (code: string): string => {
    if (!code) return '';
    const country = this.countries.find(c => c.code === code);
    return country ? this.getCountryName(country) : code;
  };
}
```

Note: The implementing agent needs to add the `effect` import and ensure signals work correctly with the autocomplete.

- [ ] **Step 3: Verify it compiles**

Run: `cd frontend && npx ng build --configuration=development 2>&1 | tail -5`
Expected: "Application bundle generation complete"

- [ ] **Step 4: Commit**

```bash
git add frontend/src/app/shared/uis/country-picker/
git commit -m "feat: add country picker component with flag autocomplete"
```

---

## Task 3: Integrate country picker into salon profile

### Files:
- Modify: `frontend/src/app/features/salon-profile/models/salon-profile.model.ts`
- Modify: `frontend/src/app/features/salon-profile/salon-profile.component.ts`
- Modify: `frontend/src/app/features/salon-profile/salon-profile.component.html`
- Modify: `frontend/public/i18n/fr.json`
- Modify: `frontend/public/i18n/en.json`

- [ ] **Step 1: Add addressCountry to frontend models**

In `salon-profile.model.ts`, add `addressCountry: string | null` to both `TenantResponse` (after `addressCity`) and `UpdateTenantRequest` (after `addressCity`).

- [ ] **Step 2: Add signal and sync in component TS**

In `salon-profile.component.ts`:
- Add `protected addressCountry = signal<string | null>(null);`
- In the effect that syncs tenant → form: add `this.addressCountry.set(tenant.addressCountry ?? null);`
- In `onSave()`, add `addressCountry: this.addressCountry()` to the request object
- Add `CountryPickerComponent` to the imports array

- [ ] **Step 3: Add country picker to template**

In `salon-profile.component.html`, inside Tab 2 (Adresse), after the postal code + city row (after the closing `</div>` of `field-row`), add:

```html
<app-country-picker
  [countryCode]="addressCountry()"
  (countryCodeChange)="addressCountry.set($event)"
/>
```

- [ ] **Step 4: Add translation keys**

In `fr.json`, inside `pro.salon`, add:
```json
"addressCountry": "Pays"
```

In `en.json`, inside `pro.salon`, add:
```json
"addressCountry": "Country"
```

- [ ] **Step 5: Build and verify**

Run: `cd frontend && npx ng build --configuration=development 2>&1 | tail -5`
Expected: "Application bundle generation complete"

- [ ] **Step 6: Commit**

```bash
git add frontend/src/app/features/salon-profile/ frontend/public/i18n/
git commit -m "feat: add country picker to salon profile address tab"
```

---

## Task 4: Redesign discover page — Split view Airbnb

### Files:
- Rewrite: `frontend/src/app/pages/discover/discover-page.component.ts`
- Rewrite: `frontend/src/app/pages/discover/discover-page.component.html`
- Rewrite: `frontend/src/app/pages/discover/discover-page.component.scss`

- [ ] **Step 1: Rewrite discover page component TS**

Key changes from current implementation:
- Keep existing: Leaflet dynamic import, geocoding with Nominatim, geolocation
- Add: `hoveredSlug` signal to track which salon card is hovered
- Add: `markersBySlug` map to find markers by salon slug for hover highlight
- Change `geocodeAndPlotSalons`: store markers in a `Record<string, L.Marker>` keyed by slug
- Add `onSalonHover(slug: string)` and `onSalonLeave(slug: string)` methods that scale the marker and open/close popup
- Layout: the map container ref stays `#mapContainer`

The component should keep all existing geocoding/geolocation logic but add hover sync.

- [ ] **Step 2: Rewrite discover page HTML**

Split view layout:

```html
<div class="discover-page">
  <div class="discover-content">
    <!-- Left panel: search + list -->
    <div class="discover-list-panel">
      <div class="search-bar">
        <form (submit)="onSearch(); $event.preventDefault()">
          <span class="search-icon">🔍</span>
          <input type="text" [placeholder]="'discover.search' | transloco"
            [value]="searchQuery()" (input)="searchQuery.set($any($event.target).value)" />
        </form>
      </div>

      @if (salons().length > 0) {
        <p class="results-count">{{ salons().length }} salon(s)</p>
        <div class="salon-list">
          @for (salon of salons(); track salon.slug; let i = $index) {
            <button class="salon-card"
              [class.hovered]="hoveredSlug() === salon.slug"
              (mouseenter)="onSalonHover(salon.slug)"
              (mouseleave)="onSalonLeave(salon.slug)"
              (click)="onSalonClick(salon.slug)">
              <div class="salon-card-avatar" [style.background]="...">...</div>
              <div class="salon-card-body">
                <span class="salon-card-name">{{ salon.name }}</span>
                @if (salon.addressCity) { <span class="salon-card-city">📍 {{ salon.addressCity }}</span> }
                @if (salon.description) { <p class="salon-card-desc">{{ truncate(salon.description, 100) }}</p> }
                <!-- category chips -->
              </div>
            </button>
          }
        </div>
      } @else {
        <!-- empty state -->
      }
    </div>

    <!-- Right panel: map -->
    <div class="discover-map-panel">
      <div class="map-container" #mapContainer></div>
    </div>
  </div>
</div>
```

- [ ] **Step 3: Rewrite discover page SCSS**

Key styles:
```scss
.discover-content {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 80px); // minus header height

  @media (min-width: 768px) {
    flex-direction: row;
  }
}

.discover-list-panel {
  flex: 1;
  overflow-y: auto;
  padding: 16px;

  @media (min-width: 768px) {
    flex: 0 0 50%;
    max-width: 50%;
  }
}

.discover-map-panel {
  height: 250px;
  order: -1; // map on top on mobile

  @media (min-width: 768px) {
    flex: 0 0 50%;
    max-width: 50%;
    height: auto;
    order: 0;
    position: sticky;
    top: 0;
  }
}

.map-container {
  width: 100%;
  height: 100%;
}

.salon-card.hovered {
  border-color: #c06;
  box-shadow: 0 2px 12px rgba(192, 0, 102, 0.12);
}
```

Plus all the salon card styles, search bar styles, empty state, and custom marker styles (user-dot, salon-pin, leaflet popup overrides) from the current SCSS.

- [ ] **Step 4: Build and verify**

Run: `cd frontend && npx ng build --configuration=development 2>&1 | tail -5`
Expected: "Application bundle generation complete"

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/pages/discover/
git commit -m "feat: redesign discover page with Airbnb-style split view map"
```

---

## Task 5: Redesign home page — Vitrine immersive

### Files:
- Rewrite: `frontend/src/app/pages/home/home.ts`
- Rewrite: `frontend/src/app/pages/home/home.html`
- Rewrite: `frontend/src/app/pages/home/home.scss`
- Modify: `frontend/public/i18n/fr.json`
- Modify: `frontend/public/i18n/en.json`

- [ ] **Step 1: Rewrite home component TS**

Add:
- Leaflet mini-map initialization (dynamic import, same as discover)
- Geolocation for user position
- `recentPosts` signal fed from new `PostsService.listRecentPublic()` method
- `nearestSalons` computed that takes first 4 salons (later can sort by distance)

```typescript
import { Component, inject, signal, AfterViewInit, OnDestroy, PLATFORM_ID, ElementRef, viewChild } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { Router } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { TranslocoPipe } from '@jsverse/transloco';
import { DiscoveryService } from '../../features/discovery/discovery.service';
import { PostsService } from '../../features/posts/posts.service';
import { SalonCard } from '../../features/discovery/discovery.model';
import { API_BASE_URL } from '../../core/config/api-base-url.token';
```

The component:
- Fetches salons via `discoveryService.searchSalons()`
- Fetches recent posts via `postsService.listRecentPublic(6)`
- Initializes a small Leaflet map in `#miniMap` container
- Geocodes salon addresses onto the mini map

- [ ] **Step 2: Rewrite home HTML**

Structure:
1. **Hero** — warm gradient background, white title/subtitle, frosted search bar
2. **"Près de toi"** — grid: mini-map (left) + 2-3 salon mini-cards (right)
3. **"Derniers posts"** — horizontal scroll of post thumbnails (100x140px)
4. **CTA Pro** — "Tu es pro ? Crée ta vitrine gratuitement"
5. **"Voir tous les salons"** button always visible (not inside a conditional)

- [ ] **Step 3: Rewrite home SCSS**

Key styles: warm hero gradient, frosted search bar, mini-map container (160px wide, 160px tall on desktop), post thumbnails with gradient overlays, responsive grid.

- [ ] **Step 4: Add translation keys**

In both `fr.json` and `en.json`, add:
- `home.nearYou`: "Près de toi" / "Near you"
- `home.recentPosts`: "Derniers posts" / "Recent posts"

- [ ] **Step 5: Build and verify**

Run: `cd frontend && npx ng build --configuration=development 2>&1 | tail -5`
Expected: "Application bundle generation complete"

- [ ] **Step 6: Commit**

```bash
git add frontend/src/app/pages/home/ frontend/public/i18n/
git commit -m "feat: redesign home page with immersive hero, mini-map, and recent posts"
```

---

## Task 6: Backend — Recent public posts endpoint

### Files:
- Create: `backend/src/main/java/com/prettyface/app/post/web/PublicPostController.java`
- Create: `backend/src/main/java/com/prettyface/app/post/web/dto/RecentPostResponse.java`
- Modify: `backend/src/main/java/com/prettyface/app/post/repo/PostRepository.java`
- Modify: `frontend/src/app/features/posts/posts.service.ts`
- Modify: `frontend/src/app/features/posts/posts.model.ts`

- [ ] **Step 1: Create RecentPostResponse DTO**

```java
package com.prettyface.app.post.web.dto;

import com.prettyface.app.post.domain.PostType;
import java.time.LocalDateTime;

public record RecentPostResponse(
    Long id,
    PostType type,
    String caption,
    String thumbnailUrl,
    String salonName,
    String salonSlug,
    LocalDateTime createdAt
) {}
```

- [ ] **Step 2: Add repository method**

In `PostRepository.java`, add:

```java
List<Post> findTop6ByOrderByCreatedAtDesc();
```

Note: Posts are not currently tenant-scoped (they're in a shared table). If multi-tenant isolation is needed later, this query will need to join with Tenant. For now this returns the 6 most recent posts globally.

- [ ] **Step 3: Create PublicPostController**

```java
package com.prettyface.app.post.web;

import com.prettyface.app.post.domain.Post;
import com.prettyface.app.post.domain.PostType;
import com.prettyface.app.post.repo.PostRepository;
import com.prettyface.app.post.web.dto.RecentPostResponse;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Paths;
import java.util.List;

@RestController
@RequestMapping("/api/public/posts")
public class PublicPostController {

    private final PostRepository postRepository;

    public PublicPostController(PostRepository postRepository) {
        this.postRepository = postRepository;
    }

    @GetMapping("/recent")
    public List<RecentPostResponse> recent() {
        return postRepository.findTop6ByOrderByCreatedAtDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    private RecentPostResponse toResponse(Post p) {
        String thumbnailPath = p.getAfterImagePath() != null
                ? p.getAfterImagePath()
                : p.getBeforeImagePath();
        String thumbnailUrl = thumbnailPath != null
                ? "/api/images/posts/" + Paths.get(thumbnailPath).getFileName().toString()
                : null;

        // TODO: When posts are tenant-scoped, join with Tenant to get salonName/salonSlug
        return new RecentPostResponse(
                p.getId(), p.getType(), p.getCaption(),
                thumbnailUrl, null, null, p.getCreatedAt()
        );
    }
}
```

- [ ] **Step 4: Add security permit for the endpoint**

In `SecurityConfig.java`, verify that `GET /api/public/**` is already permitted (it is — line 146). No changes needed.

- [ ] **Step 5: Add frontend model and service method**

In `posts.model.ts`, add:
```typescript
export interface RecentPost {
  id: number;
  type: PostType;
  caption: string | null;
  thumbnailUrl: string | null;
  salonName: string | null;
  salonSlug: string | null;
  createdAt: string;
}
```

In `posts.service.ts`, add:
```typescript
listRecentPublic(limit = 6): Observable<RecentPost[]> {
  return this.http.get<RecentPost[]>(`${this.apiBaseUrl}/api/public/posts/recent`);
}
```

- [ ] **Step 6: Compile backend and build frontend**

Run: `cd backend && ./mvnw compile`
Run: `cd frontend && npx ng build --configuration=development 2>&1 | tail -5`
Expected: Both succeed

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/prettyface/app/post/ frontend/src/app/features/posts/
git commit -m "feat: add public recent posts endpoint for home page"
```

---

## Execution Order

Tasks 1 → 2 → 3 are sequential (backend country → component → integration).
Task 6 can run in parallel with Tasks 1-3.
Tasks 4 and 5 depend on Task 6 (home needs recent posts) and benefit from Task 1 (country in fullAddress improves geocoding).

Recommended order: **1 → 2 → 3 → 6 → 4 → 5**
