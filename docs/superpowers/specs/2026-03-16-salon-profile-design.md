# Story 2.1 — Salon Profile Setup

## Overview

As a beauty professional, I want to configure my salon's basic information (name, logo, and description), so that clients can identify and learn about my establishment on my public storefront.

**Approach:** Extend the existing `Tenant` entity with profile fields (description, logoPath). No new entity needed — the Tenant already represents the salon conceptually.

## Acceptance Criteria

- Pro settings page at `/pro/salon` where users enter salon name, upload logo image, write rich text description, and save
- Salon profile visible on public storefront at `/salon/{slug}`
- Public page displays salon info + cares grouped by category in accordion
- Slug auto-generated from salon name (URL-friendly, unique), re-generated on name change
- Logo validation: max 5MB, PNG/JPG only (via existing `FileStorageService`)
- Required field validation on salon name
- Previous logo remains unchanged if new upload fails
- Rich text description via Quill editor
- HTML sanitization on both frontend (DomSanitizer) and backend (whitelist)

## Data Model

### Tenant Entity (extended)

New fields added to existing `Tenant.java`:

| Field | Type | Constraints |
|-------|------|-------------|
| `description` | `CLOB` | Rich text HTML, max ~10000 chars |
| `logoPath` | `VARCHAR(500)` | File path from FileStorageService |
| `updatedAt` | `TIMESTAMP` | Auto-updated on save |

Existing fields unchanged: `id`, `slug` (unique), `name`, `ownerId`, `status`, `createdAt`.

## Backend API

### Endpoints

| Method | URL | Auth | Description |
|--------|-----|------|-------------|
| `GET` | `/api/tenant` | PRO | Get current user's tenant profile |
| `PUT` | `/api/tenant` | PRO | Update tenant name, description, logo |
| `GET` | `/api/salon/{slug}` | Public | Get public salon info + cares by category |

### DTOs

**UpdateTenantRequest:**
- `name` — required, max 100 chars
- `description` — HTML string, max 10000 chars
- `logo` — base64 string, nullable (null = no change, empty string = remove logo)

**TenantResponse:**
- `id`, `name`, `slug`, `description`, `logoUrl`, `updatedAt`

**PublicSalonResponse:**
- `name`, `slug`, `description`, `logoUrl`
- `categories[]` — each contains `name` + `cares[]` (name, duration, price, images)

### Business Logic

- Slug re-generated via `SlugUtils` if name changes, with uniqueness check
- Logo stored via `FileStorageService` in `uploads/tenant/{tenantId}/`
- HTML description sanitized server-side (whitelist of allowed tags: `p`, `strong`, `em`, `ul`, `ol`, `li`, `a`, `br`)
- `PUT /api/tenant` verifies the tenant belongs to the authenticated user
- Public endpoint returns only publishable info (no internal IDs, no ownerId)

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
- **Slug preview** — displayed below name, updated in real-time (e.g., "Your URL: /salon/fleur-de-coquillage")
- **Logo** — `ImageManager` component with `maxImages=1`
- **Description** — `ngx-quill` editor (toolbar: bold, italic, lists, links)
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
pro.salon.title          — "Mon salon" / "My salon"
pro.salon.name           — "Nom du salon" / "Salon name"
pro.salon.logo           — "Logo" / "Logo"
pro.salon.description    — "Description" / "Description"
pro.salon.save           — "Enregistrer" / "Save"
pro.salon.saveSuccess    — "Profil mis à jour" / "Profile updated"
pro.salon.slugPreview    — "Votre URL" / "Your URL"
salon.public.noCares     — "Aucun soin disponible" / "No services available"
salon.public.notFound    — "Salon introuvable" / "Salon not found"
salon.public.duration    — "Durée" / "Duration"
salon.public.price       — "Prix" / "Price"
```

## Testing

### Frontend

- `salon-profile.component.spec.ts` — form rendering, required validation on name, store call on save
- `salon-page.component.spec.ts` — profile display, category accordion, empty state, slug not found

### Backend

- `TenantControllerTests.java` — `@WebMvcTest`: GET/PUT tenant auth, PUT validation, public endpoint by slug
- `TenantServiceTests.java` — update logic, slug re-generation, uniqueness check

## Security

- `PUT /api/tenant` — PRO role only, verifies tenant ownership
- `GET /api/salon/{slug}` — public, no sensitive data exposed
- HTML description: sanitized frontend (DomSanitizer) + backend (tag whitelist) to prevent stored XSS
- Logo: size/type validation via existing `FileStorageService`
