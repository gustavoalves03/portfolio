# Pro Onboarding & Salon Publication

## Context

After registration, a professional gets a tenant (DRAFT status) with an empty schema. Currently, the dashboard shows "Coming soon" and there's no guided path to configure and publish the salon. This spec covers the onboarding checklist and publish/unpublish flow.

## Approach

**Hybrid checklist with blocked publication.** The dashboard displays a configuration checklist linking to existing pages (`/pro/salon`, `/pro/categories`, `/pro/cares`, `/pro/availability`). The "Publish" button is disabled until all mandatory conditions are met. No wizard — the pro completes steps in any order.

## Mandatory Conditions for Publication

1. **Salon name** — already required at tenant creation (always true)
2. **At least 1 category** created
3. **At least 1 active care** in any category
4. **Opening hours** configured (at least 1 day)

Optional (non-blocking): description, logo.

## Backend

### New Endpoints

#### `GET /api/pro/tenant/readiness`

Returns the completion state of each condition and whether the salon can be published.

**Response 200:**
```json
{
  "name": true,
  "hasCategory": true,
  "hasActiveCare": false,
  "hasOpeningHours": false,
  "canPublish": false,
  "status": "DRAFT"
}
```

**Logic:**
- `name`: `tenant.name != null && !tenant.name.isBlank()`
- `hasCategory`: `categoryRepository.count() > 0` (tenant schema)
- `hasActiveCare`: `careRepository.countByStatus(ACTIVE) > 0` (tenant schema)
- `hasOpeningHours`: `openingHourRepository.count() > 0` (tenant schema)
- `canPublish`: all four conditions are true

Requires authenticated PRO user. Uses `TenantContext` to query the correct schema.

#### `PUT /api/pro/tenant/publish`

Publishes the salon (DRAFT → ACTIVE).

**Logic:**
1. Re-verify all readiness conditions server-side (no trust of frontend state)
2. If all conditions met → set `tenant.status = ACTIVE`, save, return 200
3. If any condition fails → return 422 with missing conditions:

**Response 422:**
```json
{
  "message": "Salon cannot be published",
  "missing": ["hasActiveCare", "hasOpeningHours"]
}
```

#### `PUT /api/pro/tenant/unpublish`

Unpublishes the salon (ACTIVE → DRAFT).

**Logic:**
- If already DRAFT → return 200 (idempotent, no-op)
- Set `tenant.status = DRAFT`, save, return 200
- Existing bookings are NOT cancelled — they remain confirmed
- The salon disappears from `/discover` and `/salon/:slug` returns 404 for new visitors

### Existing Behavior (No Changes Needed)

- `PublicDiscoveryController` already filters by `status = ACTIVE`
- `PublicSalonController.getSalon()` already checks `status = ACTIVE`

### Service Layer

New `TenantReadinessService` in `tenant/app/`:
- `getReadiness(String tenantSlug): TenantReadinessResponse` — queries each condition
- `publish(String tenantSlug): void` — validates + updates status
- `unpublish(String tenantSlug): void` — updates status

### DTOs

In `tenant/web/dto/`:
- `TenantReadinessResponse` — maps the readiness JSON above
- `PublishErrorResponse` — maps the 422 error with `message` and `missing` list

## Frontend

### Dashboard Component Rewrite

Replace the placeholder `ProDashboardComponent` with two views based on tenant status.

#### DRAFT View

- **Header**: "Mon salon" + Material chip badge "Brouillon" (neutral color)
- **Checklist card** (`mat-card`): 4 rows, each with:
  - Status icon: `check_circle` (green) if done, `radio_button_unchecked` (grey) if not
  - Label (e.g., "Nom du salon")
  - Link arrow → navigates to the corresponding `/pro/*` page
- **Publish button**: `mat-raised-button color="primary"`, disabled when `canPublish === false`
- **Helper text** when disabled: "Complétez les étapes ci-dessus pour publier votre salon"

#### ACTIVE View

- **Header**: "Mon salon" + Material chip badge "En ligne" (green)
- **Stats cards row** (2 `mat-card`):
  - "RDV aujourd'hui" — count of today's confirmed bookings
  - "RDV cette semaine" — count of this week's confirmed bookings
- **Recent bookings list**: last 5 bookings with date, time, care name, client name
  - Empty state: "Aucune réservation pour le moment"
- **Quick actions**:
  - "Voir mon salon" → opens `/salon/:slug` in new tab
  - "Dépublier" → secondary button, opens confirmation dialog

#### Unpublish Confirmation Dialog

Standard Material dialog:
- Title: "Dépublier votre salon ?"
- Body: "Votre salon ne sera plus visible sur la plateforme. Les rendez-vous existants seront maintenus."
- Actions: "Annuler" (secondary) / "Dépublier" (warn)

### Store

`DashboardStore` (SignalStore) in `features/dashboard/store/`:

**State:**
```typescript
interface DashboardState {
  readiness: TenantReadiness | null;
  recentBookings: CareBookingDetailed[];
  todayCount: number;
  weekCount: number;
}
```

**Methods:**
- `loadReadiness()` — `GET /api/pro/tenant/readiness`
- `loadActivity()` — `GET /api/bookings/detailed` with date filters, only if ACTIVE
- `publish()` — `PUT /api/pro/tenant/publish` → reload readiness on success
- `unpublish()` — `PUT /api/pro/tenant/unpublish` → reload readiness on success

**Hooks:**
- `onInit`: load readiness, then conditionally load activity if ACTIVE

### Service

`DashboardService` in `features/dashboard/services/`:
- `getReadiness(): Observable<TenantReadiness>`
- `publish(): Observable<void>`
- `unpublish(): Observable<void>`

### i18n Keys

Add to both `fr.json` and `en.json` under `pro.dashboard`:

```
pro.dashboard.title
pro.dashboard.draft
pro.dashboard.active
pro.dashboard.checklist.name
pro.dashboard.checklist.categories
pro.dashboard.checklist.cares
pro.dashboard.checklist.openingHours
pro.dashboard.publish
pro.dashboard.publishDisabledHint
pro.dashboard.publishSuccess
pro.dashboard.publishError
pro.dashboard.unpublish
pro.dashboard.unpublishConfirmTitle
pro.dashboard.unpublishConfirmBody
pro.dashboard.unpublishConfirmAction
pro.dashboard.unpublishSuccess
pro.dashboard.todayBookings
pro.dashboard.weekBookings
pro.dashboard.recentBookings
pro.dashboard.noBookings
pro.dashboard.viewSalon
```

## Data Flow

1. **Dashboard loads** → `GET /api/pro/tenant/readiness` → populates checklist + publish button state
2. **If ACTIVE** → `GET /api/bookings/detailed?from=today&size=5` → populates activity section
3. **Pro completes a step** (e.g., creates a care on `/pro/cares`) → navigates back to dashboard → `onInit` re-fetches readiness → checklist updates
4. **Publish click** → `PUT /api/pro/tenant/publish` → success snackbar → dashboard refreshes to ACTIVE view
5. **Unpublish click** → confirmation dialog → `PUT /api/pro/tenant/unpublish` → success snackbar → dashboard refreshes to DRAFT view

## Error Handling

- **Publish 422**: snackbar with message listing missing conditions (from `missing` array, mapped to i18n keys)
- **Network errors**: generic error snackbar via request status pattern
- **Race condition**: if a pro deletes their last care while on the dashboard, the publish button may appear enabled with stale readiness — the server-side re-validation in `PUT /publish` catches this

## Scope Boundaries

**In scope:**
- Readiness endpoint, publish/unpublish endpoints
- Dashboard component with DRAFT/ACTIVE views
- i18n keys (fr + en)

**Out of scope (future):**
- Revenue stats, graphs, analytics
- Client reviews
- Occupancy rate
- Email notifications on publication
- Admin moderation of salons
