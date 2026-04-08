# Client Profile Redesign — Design Spec

## Overview

Redesign the PRO client detail page (`/pro/clients/:userId`) with a scrollable dashboard layout (Design B), fix the missing client name, add audit trail (who modified what + when), implement per-domain employee permissions, and decompose the 1146-line monolith component into focused sub-components.

## Scope

- Fix client name display (resolve from USERS table via ApplicationSchemaExecutor)
- Redesign page layout: scrollable dashboard (no tabs), cards-based, consistent with app aesthetic
- Audit trail: last modifier + timestamp on profiles, visits, photos, reminders
- Employee permissions: per-domain (Profile, Visits, Photos, Reminders) × 3 levels (NONE, READ, WRITE)
- Employee route: `/employee/clients/:userId` with permission enforcement
- Component decomposition: split into 5 focused sub-components

## 1. Fix Client Name

**Problem:** `ClientHistoryResponse` lacks client name because `User` lives in shared schema, `ClientProfile` in tenant schema.

**Solution:** In `TrackingService.getClientHistory()`, use `ApplicationSchemaExecutor` to fetch `User` by `userId` from shared schema. Add `clientName` and `clientEmail` fields to `ClientHistoryResponse`.

**Changes:**
- `ClientHistoryResponse` DTO: add `String clientName`, `String clientEmail`
- `TrackingService.getClientHistory()`: lookup user via `ApplicationSchemaExecutor.call()`
- Frontend `ClientHistoryResponse` model: add `clientName`, `clientEmail`

## 2. Page Layout — Dashboard B

Scrollable vertical layout with no tabs. Sections stack top to bottom:

### Header Compact
- Avatar: 48px circle, background `#c06`, white initials (first letter of first + last name)
- Name: 16px bold `#1a1a2e`
- Subtitle: "Depuis avr. 2026" in `#6b7280`
- Badges: pill badges — "{N} visites" (pink `#fdf2f8`/`#c06`), "Fidèle" if 5+ visits (purple `#f3e8ff`/`#7b2cbf`)
- Card: white, `border-radius: 12px`, `box-shadow: 0 1px 4px rgba(0,0,0,0.06)`

### Allergy Alert
- Visible only if `allergies` field is non-empty
- Background `#fef2f2`, border `1px solid #fecaca`, `border-radius: 10px`
- Warning icon + "Allergies:" bold red + allergy text

### Last Visit Section
- Section title "Dernière visite" + "Voir tout →" link (pink, expands full visit list)
- Card: care name (14px bold), date + practitioner name, photo thumbnails (before/after)
- When "Voir tout" clicked: section expands inline showing all visits in reverse chronological order
- Each visit: `visit-card` sub-component

### Practitioner Notes Section
- Section title "Notes praticien"
- Card: editable text area, audit line "Modifié par {name} · {date}" in `#9ca3af` 9px
- Edit button visible only if user has WRITE permission on PROFILE domain

### Client Info Section
- Section title "Fiche client"
- Card: 2×2 grid (skin type, hair type, preferences, reminder)
- Each cell: uppercase 9px label + 12px value
- Edit button visible only if user has WRITE permission on PROFILE domain

### Floating Action Button
- "+ Nouvelle visite" button, bottom of page
- Visible only if user has WRITE permission on VISITS domain

### Styling
- Host background: `#f5f4f2`
- Cards: white, `border-radius: 12px`, `box-shadow: 0 1px 4px rgba(0,0,0,0.06)`
- Section titles: 12px bold `#333`
- Consistent with manage page and notifications page patterns

## 3. Audit Trail

### Schema Changes

Add columns to existing tenant-scoped tables:

```sql
ALTER TABLE CLIENT_PROFILES ADD (
    UPDATED_AT TIMESTAMP,
    UPDATED_BY NUMBER(19)
);

ALTER TABLE VISIT_RECORDS ADD (
    UPDATED_AT TIMESTAMP,
    UPDATED_BY NUMBER(19)
);

ALTER TABLE VISIT_PHOTOS ADD (
    UPLOADED_BY NUMBER(19)
);

ALTER TABLE CLIENT_REMINDERS ADD (
    CREATED_BY NUMBER(19)
);
```

### Backend Changes

- `ClientProfile` entity: add `updatedAt`, `updatedBy` fields
- `VisitRecord` entity: add `updatedAt`, `updatedBy` fields
- `VisitPhoto` entity: add `uploadedBy` field
- `ClientReminder` entity: add `createdBy` field
- `TrackingService`: set `updatedBy = currentUser.id` and `updatedAt = now()` on every save/update
- Resolve modifier name: lookup `User` by `updatedBy` via `ApplicationSchemaExecutor`, return `modifiedByName` in DTOs

### DTO Changes

- `ClientProfileResponse`: add `updatedAt`, `updatedByName`
- `VisitRecordResponse`: add `updatedAt`, `updatedByName`
- `VisitPhotoResponse`: add `uploadedByName`
- `ReminderResponse`: add `createdByName`

### Frontend Display

- Below each editable section: "Modifié par {name} · {date}" in small gray text
- Photos: "Ajouté par {name}" caption
- Reminders: "Créé par {name}" caption

## 4. Employee Permissions

### New Table

```sql
CREATE TABLE EMPLOYEE_PERMISSIONS (
    ID NUMBER(19) GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    EMPLOYEE_ID NUMBER(19) NOT NULL,
    DOMAIN VARCHAR2(30 CHAR) NOT NULL,
    ACCESS_LEVEL VARCHAR2(10 CHAR) NOT NULL,
    CONSTRAINT FK_EMP_PERM_EMP FOREIGN KEY (EMPLOYEE_ID) REFERENCES EMPLOYEES(ID),
    CONSTRAINT UQ_EMP_PERM UNIQUE (EMPLOYEE_ID, DOMAIN)
);
```

### Domain Values
- `PROFILE` — client profile info (notes, skin type, allergies, preferences)
- `VISITS` — visit records (create, edit practitioner notes)
- `PHOTOS` — visit photos (upload, delete)
- `REMINDERS` — client reminders (create)

### Access Levels
- `NONE` — no access (page not accessible if all domains are NONE)
- `READ` — view only
- `WRITE` — view + create/edit

### Default
All domains set to `NONE` when employee is created.

### Backend Enforcement

- New `EmployeePermissionRepository` with `findByEmployeeId(Long employeeId)`
- New `EmployeePermissionService` with `getPermissions(Long employeeId)` and `updatePermissions(Long employeeId, List<PermissionUpdate>)`
- `TrackingController`: add permission check before each operation for EMPLOYEE role users
  - Resolve employee from `currentUser.id` → `Employee` → permissions
  - Check domain + access level
  - Return 403 if insufficient

### PRO UI (Employee Management Page)

In the employee detail/edit section, add a "Permissions" card with 4 rows:

| Domaine | Toggle |
|---------|--------|
| Profil client | [Aucun] [Lecture] [Écriture] |
| Visites | [Aucun] [Lecture] [Écriture] |
| Photos | [Aucun] [Lecture] [Écriture] |
| Rappels | [Aucun] [Lecture] [Écriture] |

Toggle group (3-state): styled as segmented buttons.

### Employee Route

- Add `/employee/clients/:userId` route with `roleGuard(Role.EMPLOYEE)`
- Same component as PRO, but with permissions applied
- Buttons/edit fields hidden when access level is `READ` or `NONE`
- If all domains `NONE`: redirect to employee bookings (no access)

### Frontend Permission Flow

- On page load: fetch permissions via `GET /api/employee/permissions`
- Pass permissions as `input()` to sub-components
- Each sub-component reads its domain's access level and shows/hides edit controls

## 5. Component Decomposition

### Current State
`pro-client-detail.component.ts` — 1146 lines, inline template + styles, handles everything.

### Target Structure

```
pages/pro/pro-client-detail.component.ts       — orchestrator, loads data, passes to children
pages/employee/employee-client-detail.component.ts — thin wrapper, loads permissions + reuses same children

features/tracking/components/
├── client-header/
│   └── client-header.component.ts              — avatar, name, badges, allergy alert
├── client-visits/
│   └── client-visits.component.ts              — visit list, expand/collapse, "new visit" form
├── client-notes/
│   └── client-notes.component.ts               — practitioner notes, editable, audit trail
├── client-info/
│   └── client-info.component.ts                — info grid 2×2, editable, audit trail
└── visit-card/
    └── visit-card.component.ts                 — single visit card (care, date, practitioner, photos, notes)
```

### Data Flow

- Parent loads `ClientHistoryResponse` (includes `clientName`, visits, profile, reminders)
- Parent fetches permissions (for employees) or defaults to full access (for PRO)
- Children receive data via `input()` signals
- Children emit actions via `output()`: `onSaveNotes`, `onSaveInfo`, `onCreateVisit`, `onUploadPhoto`, `onCreateReminder`
- Parent handles API calls via `TrackingService`

### Permission Input

Each sub-component receives:
```typescript
accessLevel = input<'NONE' | 'READ' | 'WRITE'>('WRITE'); // default WRITE for PRO
```

Components hide edit/create controls when `accessLevel() !== 'WRITE'`.

## Out of Scope

- Client search/creation flow (sub-project B)
- Client ↔ account association (sub-project B)
- Full modification history log (only last modifier tracked)
- Push notifications for reminders
- Photo deletion from visits UI
