# Story 3.2 — Salon Discovery by Category

## Overview

Replace the `/discover` placeholder with a real salon discovery page. Visitors can browse active salons filtered by care category or text search. Uses denormalized fields on the Tenant entity to avoid cross-schema queries.

**Approach:** Add `categoryNames` and `categorySlugs` denormalized fields to Tenant. Sync them on every category CRUD operation. New public endpoint `GET /api/public/salons` queries the central schema only. Frontend replaces the placeholder with a real page.

## Backend — Denormalized Fields on Tenant

### New Columns

Add to `Tenant` entity:
- `categoryNames` (String, nullable, max 1000) — comma-separated display names: "Soins visage, Ongles, Coiffure"
- `categorySlugs` (String, nullable, max 1000) — comma-separated slugs: "soins-visage,ongles,coiffure"

### Sync Strategy

After each pro category operation (create, update, deleteWithReassignment) in `CategoryService`:
1. Load all categories for the current tenant
2. Build `categoryNames` = names joined by ", "
3. Build `categorySlugs` = slugified names joined by ","
4. Load the Tenant from the central schema via `TenantService.findBySlug(currentTenantSlug)`
5. Update `categoryNames` and `categorySlugs` on the Tenant entity
6. Save via `TenantRepository`

Requires injecting `TenantRepository` and `TenantContext` into `CategoryService`. Uses existing `SlugUtils.toSlug()` for slug generation.

## Backend — Discovery Endpoint

### `GET /api/public/salons`

Public, no auth required.

**Query params:**
- `category` (optional) — slug to filter by (e.g., `soins-visage`)
- `q` (optional) — text search on salon name (case-insensitive)

**Response:** `List<SalonCardResponse>`

```java
record SalonCardResponse(
    String name,
    String slug,
    String description,
    String logoUrl,
    String categoryNames
) {}
```

**Logic:**
1. Query `TenantRepository` for all tenants where `status = ACTIVE`
2. If `category` param: filter where `categorySlugs LIKE '%{slug}%'`
3. If `q` param: filter where `LOWER(name) LIKE '%{q}%'`
4. Map each Tenant to `SalonCardResponse` (generate logoUrl from logoPath)
5. Return list

**Repository methods needed:**
- `List<Tenant> findByStatus(TenantStatus status)`
- `List<Tenant> findByStatusAndCategorySlugsContaining(TenantStatus status, String slug)`
- `List<Tenant> findByStatusAndNameContainingIgnoreCase(TenantStatus status, String name)`

### Controller

New `PublicDiscoveryController` at `/api/public/salons` — separate from `PublicSalonController`.

## Frontend — DiscoverPage Rewrite

### Component

Replace the placeholder `DiscoverPageComponent` with:
- Reads `category` and `q` query params
- Calls `GET /api/public/salons` with params
- Displays results as salon cards
- Category filter chips at the top (same 4 categories as landing page + "Tous")
- Click on chip → updates query param, refetches
- Click on salon card → `/salon/{slug}`
- Search bar → updates `q` query param
- Empty state when no results

### Service

New `DiscoveryService`:
```typescript
searchSalons(category?: string, q?: string): Observable<SalonCard[]>
```

### Data Model

```typescript
interface SalonCard {
  name: string;
  slug: string;
  description: string | null;
  logoUrl: string | null;
  categoryNames: string | null;
}
```

### Layout

- Header: "Découvrir les salons" + search bar
- Category chips row (horizontal scroll, selected chip has border)
- Results count
- Vertical list of salon cards: gradient header, logo circle, name, city (from description), description truncated, category chips
- Empty state: search icon + message

## Internationalization

Keys in both `fr.json` and `en.json`:

```
discover.title          — "Découvrir les salons" / "Discover salons"
discover.search         — "Rechercher un salon..." / "Search for a salon..."
discover.noResults      — "Aucun salon trouvé" / "No salons found"
discover.noResultsHint  — "Essayez une autre catégorie" / "Try another category"
discover.resultsCount   — "{{count}} salon(s) trouvé(s)" / "{{count}} salon(s) found"
discover.allCategories  — "Tous" / "All"
```

## Testing

### Backend
- Sync test: after creating a category, verify tenant's `categoryNames` and `categorySlugs` are updated
- Discovery endpoint: returns only ACTIVE tenants, filters by category slug, filters by name search, returns empty list when no match

### Frontend
- Salon cards render from API response
- Category chip click updates query params and refetches
- Search submits and filters
- Empty state displays when no results
- Click on card navigates to `/salon/{slug}`

## Security

- `GET /api/public/salons` is public (no auth)
- No user data exposed (only salon name, slug, description, logo, categories)
- No cross-schema queries needed (central schema only)
