# Story 2.2 — Service Category Management

## Overview

As a beauty professional, I want to create and manage categories for my services, so that clients can browse services by type.

**Approach:** Reuse existing `Category` entity (already tenant-scoped via schema-per-tenant). Add PRO-scoped CRUD endpoints. Integrate category management as chips above the existing CrudTable in the CaresComponent — no new page needed.

## Acceptance Criteria

- Category chips displayed above the cares table on `/pro/cares`
- Chips serve dual purpose: filter cares by category + manage categories (create/edit/delete)
- Click chip to filter, click again to show all
- "+" chip to add a new category
- Context menu on chip for edit/delete
- Auto-assigned colors from a harmonized palette (no icon, no user-chosen color)
- Delete with reassignment: if category has cares, user must choose a target category
- Delete without cares: direct deletion
- Required field validation on category name
- Snackbar feedback on all CRUD operations

## Data Model

### Category Entity (unchanged)

Existing fields: `id`, `name` (unique per schema), `description`.

No schema changes needed — the entity already supports what we need. Multi-tenancy is handled by schema-per-tenant (each tenant's categories live in their own Oracle schema).

### Color Assignment

Colors are **not stored** in the database. They are derived on the frontend from `category.id % CATEGORY_COLORS.length` for stable assignment (color stays consistent even if list order changes):

```typescript
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
```

## Backend API

### Endpoints

| Method | URL | Auth | Description |
|--------|-----|------|-------------|
| `GET` | `/api/pro/categories` | PRO | List tenant's categories |
| `POST` | `/api/pro/categories` | PRO | Create a category |
| `PUT` | `/api/pro/categories/{id}` | PRO | Update a category |
| `DELETE` | `/api/pro/categories/{id}?reassignTo={targetId}` | PRO | Delete with optional reassignment |

**SecurityConfig:** No changes needed — `/api/pro/**` is already restricted to PRO role.

### DTOs

- **CategoryRequest** (existing, modified): add `@Size(max=100)` on `name`, add `@Size(max=500)` on `description` (currently only has `@NotBlank` on name)
- **CategoryResponse** (existing, reused): `id`, `name`, `description`
- **DeleteCategoryResponse** (new): `reassignedCaresCount: int`

### Delete Logic

`DELETE /api/pro/categories/{id}?reassignTo={targetId}`

The entire delete-with-reassign operation runs in a single `@Transactional` block in the service layer to ensure atomicity.

1. Count cares in the category via `CareRepository.countByCategoryId(Long categoryId)`
2. If cares exist and `reassignTo` is absent → **400 Bad Request** with message "Category has N cares, reassignTo is required"
3. If `reassignTo` is provided → validate target category exists, bulk-reassign cares via `CareRepository.updateCategoryByCategoryId(Long sourceCategoryId, Category targetCategory)` (`@Modifying @Query`), delete source category, return `{ reassignedCaresCount: N }`
4. If no cares → delete directly, return `{ reassignedCaresCount: 0 }`

### CareRepository (extended)

New query methods needed:
- `long countByCategoryId(Long categoryId)` — Spring Data derived query
- `@Modifying @Query("UPDATE Care c SET c.category = :target WHERE c.category.id = :sourceId") int updateCategoryByCategoryId(@Param("sourceId") Long sourceId, @Param("target") Category target)` — bulk reassignment

### Controller

New `ProCategoryController` at `/api/pro/categories` — separate from existing `CategoryController` (which remains for admin use). Delegates to `CategoryService` (extended with pro methods) which uses `CategoryRepository` and `CareRepository`.

**GET endpoint returns `List<CategoryResponse>`** (flat list, no pagination) — all categories are needed at once for chips. This differs from the admin `GET /api/categories` which returns `Page<CategoryResponse>`.

### Validation

- `name`: required, max 100 chars, unique per tenant (enforced by DB unique constraint within schema). Duplicate name → **409 Conflict** handled by global exception handler
- `description`: optional, max 500 chars
- `reassignTo` on delete: must reference an existing category in the same tenant, must not be the same as the category being deleted

## Frontend — Category Chips in CaresComponent

### Layout

```
[Title "Mes soins"]
[chip Visage] [chip Corps] [chip Ongles] [+ Ajouter]    ← colored chips
[=============== CrudTable (filtered) ===============]
```

### Chip Behavior

- **Single click** → toggle filter (chip selected = filled style, cares filtered by categoryId)
- **Click when selected** → deselect (show all cares)
- **Context menu** (three-dot button on chip) → Edit / Delete
- **"+" chip** → opens create category dialog

### Filtering

`selectedCategoryId` signal in the component (local signal, not in any store). A component-level computed `filteredCares` filters cares by selected category or returns all if none selected. The `CrudTable` consumes `filteredCares` instead of `cares`.

```typescript
// In CaresComponent
selectedCategoryId = signal<number | null>(null);

filteredCares = computed(() => {
  const selectedId = this.selectedCategoryId();
  const cares = this.caresStore.cares();
  return selectedId ? cares.filter(c => c.category.id === selectedId) : cares;
});
```

### CategoriesService (extended)

New pro-scoped methods alongside existing admin methods:

```
getProCategories()                    → GET /api/pro/categories
createProCategory(req)                → POST /api/pro/categories
updateProCategory(id, req)            → PUT /api/pro/categories/{id}
deleteProCategory(id, reassignTo?)    → DELETE /api/pro/categories/{id}?reassignTo=...
```

### CategoriesStore (extended)

- Methods: add pro CRUD methods calling new service endpoints (`getProCategories`, `createProCategory`, `updateProCategory`, `deleteProCategory`)
- Existing admin methods remain untouched
- **Note:** `selectedCategoryId` and `filteredCares` live in the **component**, not the store (they are view-level concerns)

### Create/Edit Dialog

Fix and reuse existing `create-category.component.ts` dialog. **Current issues to fix:** imports `CreateCareRequest` instead of `CreateCategoryRequest`, labels say "Nom du soin" instead of category, description has wrong validation (`required: true`, `minLength: 10`). After fix: fields are name (required) + description (optional), dialog calls pro endpoints.

### Reassign Category Dialog (new)

```
frontend/src/app/features/categories/modals/reassign-category/reassign-category-dialog.component.ts
```

- Message: "This category has {count} service(s). Choose a target category."
- Mat-select dropdown with other categories (excluding the one being deleted)
- Buttons: Cancel / Confirm deletion
- Calls `DELETE /api/pro/categories/{id}?reassignTo={targetId}`
- Returns `DeleteCategoryResponse` for snackbar feedback
- **After successful deletion:** triggers `CaresStore` refresh to update stale category references

## Internationalization

Keys added to both `fr.json` and `en.json`:

```
pro.categories.add             — "Ajouter une catégorie" / "Add category"
pro.categories.edit            — "Modifier" / "Edit"
pro.categories.delete          — "Supprimer" / "Delete"
pro.categories.nameRequired    — "Le nom est requis" / "Name is required"
pro.categories.createSuccess   — "Catégorie créée" / "Category created"
pro.categories.updateSuccess   — "Catégorie modifiée" / "Category updated"
pro.categories.deleteSuccess   — "Catégorie supprimée" / "Category deleted"
pro.categories.deleteError     — "Erreur lors de la suppression" / "Error deleting category"
pro.categories.reassign.title  — "Réassigner les soins" / "Reassign services"
pro.categories.reassign.message — "Cette catégorie contient {count} soin(s)..." / "This category has {count} service(s)..."
pro.categories.reassign.select — "Catégorie de destination" / "Target category"
pro.categories.reassign.confirm — "Confirmer la suppression" / "Confirm deletion"
pro.categories.filter.all      — "Tous les soins" / "All services"
pro.categories.duplicateName   — "Une catégorie avec ce nom existe déjà" / "A category with this name already exists"
```

## Testing

### Frontend

- **cares.component.spec.ts** (extended) — chips rendering, click-to-filter, "+" button, context menu edit/delete
- **reassign-category-dialog.spec.ts** — message with count, category select dropdown, API call on confirm, cancel behavior

### Backend

- **ProCategoryControllerTests.java** (`@WebMvcTest`) — CRUD with PRO auth, validation errors, delete without reassignTo when cares exist (400), delete with reassignTo (200), unauthorized access (401/403)
- **CategoryServiceTests.java** (extended) — reassignment logic, delete with/without cares, target category validation

## Security

- `/api/pro/categories/**` → PRO role (via existing `/api/pro/**` SecurityConfig rule)
- Tenant isolation implicit via schema-per-tenant (queries only see current tenant's data)
- `reassignTo` target validated to exist within same tenant
- Input validation: name not blank, max 100 chars; description max 500 chars
