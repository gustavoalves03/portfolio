# Story 2.1 — Salon Profile Setup

## Overview

As a beauty professional, I want to configure my salon's basic information (name, logo, and description), so that clients can identify and learn about my establishment on my public storefront.

**Approach:** Extend the existing `Tenant` entity with profile fields (description, logoPath). No new entity needed — the Tenant already represents the salon conceptually.

## Acceptance Criteria

- Pro settings page at `/pro/salon` where users enter salon name, upload logo image, write rich text description, and save
- Salon profile visible on public storefront at `/salon/{slug}`
- Public page displays salon info + cares grouped by category in accordion
- Slug is **immutable** after tenant creation (generated once from salon name at registration) — displayed as read-only on the settings page
- Logo validation: max 5MB, PNG/JPG only (via `FileStorageService`, extended to support multiple domains)
- Required field validation on salon name
- Previous logo remains unchanged if new upload fails; old logo file deleted on successful replacement
- Rich text description via Quill editor with character count (10000 char limit on text content)
- HTML sanitization on both frontend (DomSanitizer) and backend (whitelist)

## Data Model

### Tenant Entity (extended)

New fields added to existing `Tenant.java`:

| Field | Type | Constraints |
|-------|------|-------------|
| `description` | `CLOB` | Rich text HTML, max ~10000 chars (text content) |
| `logoPath` | `VARCHAR(500)` | File path from FileStorageService |
| `updatedAt` | `TIMESTAMP` | Auto-updated via `@PreUpdate` callback |

Existing fields unchanged: `id`, `slug` (unique, immutable), `name`, `ownerId`, `status`, `createdAt`.

**Note:** Add `@PreUpdate` callback to set `updatedAt = LocalDateTime.now()`.

## Backend API

### Endpoints

| Method | URL | Auth | Description |
|--------|-----|------|-------------|
| `GET` | `/api/pro/tenant` | PRO | Get current user's tenant profile |
| `PUT` | `/api/pro/tenant` | PRO | Update tenant name, description, logo |
| `GET` | `/api/salon/{slug}` | Public | Get public salon info + cares by category |

**SecurityConfig changes:**
- `/api/pro/**` already restricted to PRO role — new endpoints fit this pattern
- Add `permitAll()` rule for `/api/salon/**` (public storefront access)

### DTOs

**UpdateTenantRequest:**
- `name` — required, max 100 chars
- `description` — HTML string, max 10000 chars (text content)
- `logo` — base64 string, nullable (null = no change, empty string = remove logo)

**TenantResponse:**
- `id`, `name`, `slug`, `description`, `logoUrl`, `updatedAt`

**PublicSalonResponse:**
- `name`, `slug`, `description`, `logoUrl`
- `categories[]` — each contains `name` + `cares[]` (name, duration, price, images)

### Business Logic

- **Slug is immutable** — not re-generated on name change. This avoids breaking the schema-per-tenant linkage where schema names are derived from slugs. The slug is set once at tenant provisioning.
- Logo stored via `FileStorageService` in `uploads/tenant/{tenantId}/`
- **FileStorageService refactoring:** Add a new method `saveBase64Image(String base64Data, String domain, Long entityId)` to support multiple storage domains (currently hardcoded for `cares`). The existing care-specific method delegates to the new generic one.
- **Old logo cleanup:** When logo is replaced or removed, delete the previous file from disk
- HTML description sanitized server-side (whitelist of allowed tags: `p`, `strong`, `em`, `ul`, `ol`, `li`, `a`, `br`)
- `PUT /api/pro/tenant` verifies the tenant belongs to the authenticated user
- Public endpoint returns only publishable info (no internal IDs, no ownerId)

### Tenant-Scoped Care Queries

The public endpoint `GET /api/salon/{slug}` needs to fetch cares belonging to the tenant. Since the app uses schema-per-tenant (each tenant has their own Oracle schema), the query resolves as follows:
1. Resolve tenant by slug → get tenant ID
2. Set `TenantContext` with the resolved tenant (activates the correct schema via `TenantRoutingDataSource`)
3. Query `CareRepository` and `CategoryRepository` within that schema context
4. Return cares grouped by category

### ImageManager Adaptation

The existing `ImageManager` component is coupled to `CareImage` model and has a hardcoded `MAX_IMAGES = 5`. Required changes:
- Add a `maxImages` `@Input()` (default: 5) to make it configurable
- Move the image interface to `shared/models/` (generic `ManagedImage` interface) — `CareImage` extends it
- For salon profile, use `ImageManager` with `maxImages=1` and hide the reorder/name UI when `maxImages === 1`

## Frontend — Pro Settings (`/pro/salon`)

### Structure

```
frontend/src/app/features/salon-profile/
├── models/
│   └── salon-profile.model.ts       # TenantResponse, UpdateTenantRequest interfaces
├── services/
│   └── salon-profile.service.ts     # HTTP calls (get, update, getPublic)
├── store/
│   └── salon-profile.store.ts       # NgRx SignalStore
└── salon-profile.component.ts/html/scss
```

### Form

- **Salon name** — `mat-form-field` input, required, max 100 chars
- **Slug preview** — displayed as read-only below name (e.g., "Your URL: /salon/fleur-de-coquillage"). Slug is immutable, set at registration.
- **Logo** — `ImageManager` component with `maxImages=1` (reorder/name UI hidden)
- **Description** — `ngx-quill` editor (toolbar: bold, italic, lists, links) with character counter showing remaining chars out of 10000 (counts text content, not HTML)
- **Save button** — loading state (spinner), success/error snackbar messages

### Store (SignalStore)

Standard pattern: `withState()` → `withRequestStatus()` → `withComputed()` → `withMethods()` → `withHooks()`

- State: `tenant: TenantResponse | null`
- Methods: `loadProfile()` (GET), `updateProfile(request)` (PUT)
- Initialized via `onInit` hook

### Route & Navigation

- Route: `/pro/salon` with `authGuard`
- Navigation: "Mon salon" link in pro menu

## Frontend — Public Page (`/salon/{slug}`)

### Structure

```
frontend/src/app/pages/salon/
├── salon-page.component.ts/html/scss
└── salon-page.component.spec.ts
```

No dedicated store — simple HTTP call via `SalonProfileService.getPublicSalon(slug)`, result stored in local signal.

### Layout

1. **Salon header** — rounded logo + name + rich text description (rendered HTML)
2. **Cares accordion** — `mat-expansion-panel` per category
   - Panel title: category name
   - Content: care cards (thumbnail, name, duration, price)
   - Categories sorted by name, cares sorted by name within
3. **Empty state** — "No services available" message if no cares
4. **Not found** — "Salon not found" message if slug doesn't exist

### Rich Text Rendering

HTML from Quill rendered via `[innerHTML]` with Angular DomSanitizer. Scoped CSS styles for rich text content (`.ql-content p`, `.ql-content ul`, etc.).

## Internationalization

Keys added to both `fr.json` and `en.json`:

```
pro.salon.title            — "Mon salon" / "My salon"
pro.salon.name             — "Nom du salon" / "Salon name"
pro.salon.logo             — "Logo" / "Logo"
pro.salon.description      — "Description" / "Description"
pro.salon.save             — "Enregistrer" / "Save"
pro.salon.saveSuccess      — "Profil mis à jour" / "Profile updated"
pro.salon.saveError        — "Erreur lors de la sauvegarde" / "Error saving profile"
pro.salon.slugPreview      — "Votre URL" / "Your URL"
pro.salon.nameRequired     — "Le nom du salon est requis" / "Salon name is required"
pro.salon.descriptionLimit — "Limite de caractères atteinte" / "Character limit reached"
pro.salon.logoError        — "Erreur lors du téléchargement du logo" / "Error uploading logo"
salon.public.noCares       — "Aucun soin disponible" / "No services available"
salon.public.notFound      — "Salon introuvable" / "Salon not found"
salon.public.duration      — "Durée" / "Duration"
salon.public.price         — "Prix" / "Price"
```

## Testing

### Frontend

- `salon-profile.component.spec.ts` — form rendering, required validation on name, store call on save, character counter
- `salon-page.component.spec.ts` — profile display, category accordion, empty state, slug not found

### Backend

- `TenantControllerTests.java` — `@WebMvcTest`: GET/PUT tenant auth, PUT validation, public endpoint by slug, unauthorized access
- `TenantServiceTests.java` — update logic, logo replacement/cleanup, description sanitization

## Security

- `PUT /api/pro/tenant` — PRO role only (via `/api/pro/**` SecurityConfig rule), verifies tenant ownership
- `GET /api/salon/{slug}` — public (`permitAll()` in SecurityConfig), no sensitive data exposed
- HTML description: sanitized frontend (DomSanitizer) + backend (tag whitelist) to prevent stored XSS
- Logo: size/type validation via `FileStorageService`
- Old logo files cleaned up on replacement to prevent disk accumulation
