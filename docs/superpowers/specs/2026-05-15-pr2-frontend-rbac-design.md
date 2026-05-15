# PR2 — Frontend Multi-Role Scoped RBAC

**Date :** 2026-05-15
**Pattern :** Frontend consumer for the backend Scoped RBAC shipped in PR1.
**Scope :** Frontend (Angular 20) + minimal backend adjustments (drop legacy `role`, add tenant fields to `UserDto`, accept null in switch-tenant).
**Spec PR1 backend :** `docs/superpowers/specs/2026-05-11-multi-role-scoped-rbac-pr1-backend.md`

---

## 1. Vision

PR1 backend shipped `USER_ROLE_ASSIGNMENTS` + `/api/me/tenants` + `/api/me/switch-tenant`. The frontend still consumes the legacy `role` String field and ignores `availableTenants` / `activeTenantId`. PR2 finishes the migration:

- Replace `User.role: Role` with `User.roles: Role[]` + `activeTenantId` + `availableTenants`.
- Add `AuthService.hasRole(...)` helper, migrate 17 callsites.
- Add a header tenant switcher component for users with ≥1 TENANT-scoped role, with a "client mode" entry (activeTenantId = null).
- Drop the legacy `role` field from `UserDto` server-side now that no consumer reads it.

**Goals :**
- A PRO of multiple salons can switch context from the header without re-logging.
- A PRO can flip to "client mode" to browse / book at any salon.
- A CLIENT (no assignment) sees no switcher and gets the existing UX.
- One declarative source of truth (`AuthService` signals) for roles + tenant context across the app.

**Non-goals (for PR2) :**
- Commercial role UI (PR3+).
- Per-tenant employee profile editing (covered elsewhere).
- Audit log for switch-tenant (backlog).

---

## 2. Architecture

Single service (`AuthService`) holds the full session state: token + decoded user + roles + active tenant + available tenants. Components and guards consume it via signals. No new service — the JWT carries everything in one payload, so splitting auth and tenant session would just duplicate plumbing.

```
┌─────────────────────────────────────────────────────────┐
│                     AuthService                         │
│                                                         │
│  signals: token, currentUser                            │
│  computed: roles, activeTenantId, availableTenants,     │
│            isAuthenticated, isClientMode                │
│  methods: login, logout, refresh, switchTenant,         │
│           hasRole, navigateByRole                       │
└──┬──────────────────────────┬──────────────────────┬────┘
   │                          │                      │
   ▼                          ▼                      ▼
roleGuard (any feature)   TenantSwitcher           Layout
(canActivate)             (header component)       (header/footer/
                                                    sidenav/bottom-nav)
```

Data flow (login / refresh / switch) :

```
Login (POST /api/auth/login)
  ↓
backend returns AuthResponse { accessToken, user: { roles[], activeTenantId, availableTenants[] } }
  ↓
AuthService.currentUser.set(response.user) + token.set(response.accessToken)
  ↓
header re-renders → TenantSwitcher visible if availableTenants.length ≥ 1
guards re-evaluate → hasRole() resolved from new roles[]
```

```
Switch tenant (POST /api/me/switch-tenant { tenantId: 42 | null })
  ↓
backend returns AuthResponse with re-issued JWT scoped to new tenantId
  ↓
AuthService updates currentUser + token
  ↓
authService.navigateByRole() redirects to /pro/dashboard or /employee/bookings or /
```

---

## 3. Backend changes

Frontend depends on three small backend tweaks. They land in the same PR.

### 3.1 `UserDto` — add tenant fields, drop legacy `role`

```java
package com.luxpretty.app.auth.dto;

@Data @Builder @AllArgsConstructor
public class UserDto {
    private Long id;
    private String name;
    private String email;
    private String imageUrl;
    private AuthProvider provider;
    private List<String> roles;
    private Long activeTenantId;
    private List<TenantSummaryDto> availableTenants;
    // dropped: private String role;
}
```

New record `TenantSummaryDto(id, slug, name)` — already exists as `com.luxpretty.app.me.web.dto.TenantSummary`; reuse it (move to `auth.dto.` or keep in `me.web.dto.` and import).

### 3.2 Populate `activeTenantId` + `availableTenants` in `UserDto`

`AuthController` (helpers `buildAuthResponse` / `buildUserDto`) + `MeController.switchTenant` + `OAuth2AuthenticationSuccessHandler` need to set these fields on `UserDto`. Same data the JWT already carries.

Helper :
```java
private UserDto toUserDto(User user, Long activeTenantId) {
    Set<Role> resolved = userRoleService.resolveRoles(user.getId(), activeTenantId);
    List<String> roleNames = resolved.stream().map(Enum::name).toList();
    List<TenantSummary> tenants = userRoleService.findUserTenantIds(user.getId()).stream()
            .map(tenantRepository::findById)
            .flatMap(Optional::stream)
            .map(t -> new TenantSummary(t.getId(), t.getSlug(), t.getName()))
            .toList();
    return UserDto.builder()
            .id(user.getId()).name(user.getName()).email(user.getEmail())
            .imageUrl(user.getImageUrl()).provider(user.getProvider())
            .roles(roleNames)
            .activeTenantId(activeTenantId)
            .availableTenants(tenants)
            .build();
}
```

The `pickLegacyRole` helpers in `AuthController` + `MeController` are deleted.

### 3.3 `POST /api/me/switch-tenant` accepts `tenantId: null`

```java
public record SwitchTenantRequest(Long tenantId) {} // @NotNull removed
```

`MeController.switchTenant` behavior :
- `tenantId == null` → emit JWT with `activeTenantId=null` ; resolved roles are GLOBAL only (`resolveRoles(userId, null)`). No assignment check needed (any user can drop into client mode).
- `tenantId != null` → current behavior (verify allow-list, 403 if not assigned).

### 3.4 Backend tests

- `AuthControllerTests` : assertions `$.user.role` → `$.user.roles[0]` ; add `$.user.activeTenantId` + `$.user.availableTenants` checks where relevant.
- `MeControllerTests` : same migration + new test `switchTenant_acceptsNullTenantId_emitsGlobalRolesOnly`.
- `AuthFlowIntegrationTests` : already CLIENT implicite path (PR1 fixed), confirm `roles` is `[]` and `activeTenantId` null.
- `OAuth2AuthenticationSuccessHandlerTests` : adjust `UserDto` mock if assertions touch it.

---

## 4. Frontend models

### 4.1 `auth.model.ts`

```ts
export enum AuthProvider {
  LOCAL = 'LOCAL',
  GOOGLE = 'GOOGLE',
  FACEBOOK = 'FACEBOOK',
  APPLE = 'APPLE',
}

export enum Role {
  PRO = 'PRO',
  EMPLOYEE = 'EMPLOYEE',
  COMMERCIAL = 'COMMERCIAL',
  ADMIN = 'ADMIN',
  // Role.USER removed — absence of a role = CLIENT implicite.
}

export interface TenantSummary {
  id: number;
  slug: string;
  name: string;
}

export interface User {
  id: number;
  name: string;
  email: string;
  imageUrl?: string;
  provider: AuthProvider;
  roles: Role[];
  activeTenantId: number | null;
  availableTenants: TenantSummary[];
}

export interface AuthResponse {
  accessToken: string;
  tokenType: string;
  user: User;
}
```

---

## 5. `AuthService` changes

### 5.1 New computed signals

```ts
readonly roles = computed<Role[]>(() => this.currentUser()?.roles ?? []);
readonly activeTenantId = computed<number | null>(() => this.currentUser()?.activeTenantId ?? null);
readonly availableTenants = computed<TenantSummary[]>(() => this.currentUser()?.availableTenants ?? []);
readonly isClientMode = computed<boolean>(() => this.activeTenantId() === null);
```

### 5.2 `hasRole(...roles: Role[]): boolean`

```ts
/**
 * True if the user holds ANY of the given roles.
 * ADMIN is NOT auto-promoted — explicit roles only. Callers that want
 * "ADMIN bypasses everything" should pass Role.ADMIN explicitly:
 *   hasRole(Role.PRO, Role.ADMIN)
 */
hasRole(...required: Role[]): boolean {
  const userRoles = this.roles();
  return required.some(r => userRoles.includes(r));
}
```

### 5.3 `switchTenant(tenantId: number | null): Observable<AuthResponse>`

```ts
switchTenant(tenantId: number | null): Observable<AuthResponse> {
  const base = this.apiBaseUrl?.replace(/\/$/, '') ?? '';
  return this.http.post<AuthResponse>(`${base}/api/me/switch-tenant`, { tenantId }).pipe(
    tap(response => {
      this.token.set(response.accessToken);
      this.currentUser.set(response.user);
      if (isPlatformBrowser(this.platformId)) {
        localStorage.setItem(TOKEN_KEY, response.accessToken);
      }
    })
  );
}
```

### 5.4 `navigateByRole()` revised

```ts
navigateByRole(): void {
  const inClientMode = this.isClientMode();
  if (!inClientMode && this.hasRole(Role.PRO, Role.ADMIN)) {
    this.router.navigate(['/pro/dashboard']);
  } else if (!inClientMode && this.hasRole(Role.EMPLOYEE)) {
    this.router.navigate(['/employee/bookings']);
  } else {
    this.router.navigate(['/']);
  }
}
```

Pro/employee dashboards are scoped to a tenant — in client mode (`activeTenantId === null`) the user is treated as a plain client.

---

## 6. TenantSwitcher component

### 6.1 Location

`frontend/src/app/shared/layout/header/tenant-switcher/tenant-switcher.component.ts` + `.html` + `.scss` + `.spec.ts`.

Integrated in `header.html` between the logo and the avatar menu, after the existing `@if (authService.isAuthenticated())` block.

### 6.2 Visibility

Visible only when the authenticated user has at least 1 TENANT-scoped role :

```ts
readonly visible = computed(() =>
  this.auth.isAuthenticated() && this.auth.availableTenants().length >= 1);
```

A pure CLIENT (no assignments) sees no chip. A pro with exactly 1 salon still sees the chip — they need it to switch to client mode and back. (The choice of "hide if exactly 1 tenant" decided during brainstorming applies only to the *salon switcher* aspect ; the chip still serves the client-mode toggle.)

### 6.3 Template

```html
@if (visible()) {
  <button mat-stroked-button [matMenuTriggerFor]="menu" class="tenant-chip">
    <mat-icon>{{ iconName() }}</mat-icon>
    <span>{{ label() }}</span>
    <mat-icon>arrow_drop_down</mat-icon>
  </button>
  <mat-menu #menu="matMenu">
    @for (t of tenants(); track t.id) {
      <button mat-menu-item (click)="switch(t.id)">
        <mat-icon>{{ activeId() === t.id ? 'check' : '' }}</mat-icon>
        <span>{{ t.name || t.slug }}</span>
      </button>
    }
    <mat-divider></mat-divider>
    <button mat-menu-item (click)="switch(null)">
      <mat-icon>{{ activeId() === null ? 'check' : '' }}</mat-icon>
      <span>{{ 'common.clientMode' | transloco }}</span>
    </button>
  </mat-menu>
}
```

### 6.4 Computed labels

```ts
readonly iconName = computed(() => this.activeId() === null ? 'shopping_bag' : 'store');
readonly label = computed(() => {
  if (this.activeId() === null) return this.i18n.translate('common.clientMode');
  const t = this.tenants().find(t => t.id === this.activeId());
  return t?.name ?? t?.slug ?? '—';
});
```

### 6.5 Switch handler

```ts
switch(tenantId: number | null): void {
  if (tenantId === this.activeId()) return; // no-op
  this.auth.switchTenant(tenantId).subscribe({
    next: () => this.auth.navigateByRole(),
    error: () => this.snackbar.open(
      this.i18n.translate('errors.tenantSwitchFailed'), undefined, { duration: 3000 }),
  });
}
```

### 6.6 Translations

`fr.json` + `en.json` :

| Key | fr | en |
|---|---|---|
| `common.clientMode` | "Mode client" | "Client mode" |
| `tenants.switcher.title` | "Changer de salon" | "Switch salon" |
| `errors.tenantSwitchFailed` | "Impossible de changer de salon" | "Could not switch salon" |

---

## 7. Migration of existing callsites

17 callsites in 8 files. Pattern : `user?.role === Role.X || user?.role === Role.ADMIN` becomes `authService.hasRole(Role.X, Role.ADMIN)`.

| File | Line | Change |
|---|---|---|
| `core/auth/auth.service.ts` | 184-191 | `navigateByRole()` uses `hasRole()` + `isClientMode()` (see §5.4) |
| `core/tenant/tenant-features.service.ts` | 51 | `const isPro = this.auth.hasRole(Role.PRO, Role.ADMIN)` |
| `core/auth/role.guard.ts` | 26 | `if (authService.hasRole(requiredRole, Role.ADMIN))` |
| `features/posts/salon-posts-viewer/salon-posts-viewer.component.ts` | 806 | `this.isPro.set(this.auth.hasRole(Role.PRO, Role.ADMIN))` |
| `shared/layout/bottom-nav/bottom-nav.component.ts` | 183 | `if (this.auth.hasRole(Role.PRO, Role.ADMIN, Role.EMPLOYEE) && ...)` |
| `shared/layout/bottom-nav/bottom-nav.component.ts` | 213 | `if (this.auth.hasRole(Role.PRO, Role.ADMIN))` |
| `shared/layout/footer/footer.ts` | 22 | `return this.auth.hasRole(Role.PRO, Role.ADMIN)` |
| `shared/layout/navigation/sidenav-menu.ts` | 43 | `const isPro = this.auth.hasRole(Role.PRO, Role.ADMIN)` |
| `shared/layout/header/header.ts` | 34 | `return this.auth.hasRole(Role.PRO, Role.ADMIN, Role.EMPLOYEE)` |
| `shared/layout/header/header.html` | 126 | `@if (auth.hasRole(Role.PRO, Role.ADMIN))` |
| `app.routes.ts` | 46 | unchanged — `roleGuard(Role.PRO)` ; guard internals migrated |

`Role.USER` removed from enum → any leftover reference is a compile error to fix.

---

## 8. Tests

### 8.1 Backend (existing tests)

- `AuthControllerTests` : 13 tests touch `$.user.role` → migrate to `$.user.roles[0]`. Add `$.user.activeTenantId` + `$.user.availableTenants` assertions for register/login happy paths.
- `MeControllerTests.switchTenant_returnsNewToken_whenUserHasAssignment` : assert `$.user.roles[0]` + `$.user.activeTenantId`.
- `MeControllerTests.switchTenant_acceptsNullTenantId_emitsGlobalRolesOnly` : NEW.
- `OAuth2AuthenticationSuccessHandlerTests` : confirm `UserDto` shape still matches.

### 8.2 Frontend

**New** : `auth.service.spec.ts` additions for `hasRole`, `isClientMode`, `switchTenant`.

```ts
describe('hasRole', () => {
  it('returns false when user has no roles', () => {
    service['currentUser'].set({ roles: [], ... } as User);
    expect(service.hasRole(Role.PRO)).toBe(false);
  });

  it('returns true when user has the required role', () => {
    service['currentUser'].set({ roles: [Role.PRO], ... } as User);
    expect(service.hasRole(Role.PRO)).toBe(true);
  });

  it('returns true when user has any of multiple required roles', () => {
    service['currentUser'].set({ roles: [Role.EMPLOYEE], ... } as User);
    expect(service.hasRole(Role.PRO, Role.ADMIN, Role.EMPLOYEE)).toBe(true);
  });

  it('does NOT auto-promote ADMIN — caller must include it explicitly', () => {
    service['currentUser'].set({ roles: [Role.ADMIN], ... } as User);
    expect(service.hasRole(Role.PRO)).toBe(false);
    expect(service.hasRole(Role.PRO, Role.ADMIN)).toBe(true);
  });
});

describe('switchTenant', () => {
  it('POSTs tenantId and updates token + currentUser', () => {
    service.switchTenant(42).subscribe();
    const req = httpMock.expectOne('http://localhost:8080/api/me/switch-tenant');
    expect(req.request.body).toEqual({ tenantId: 42 });
    req.flush({ accessToken: 'new.jwt', user: { id: 1, roles: ['PRO'], activeTenantId: 42, ... } });
    expect(service.activeTenantId()).toBe(42);
  });

  it('accepts null tenantId (client mode)', () => {
    service.switchTenant(null).subscribe();
    const req = httpMock.expectOne(/switch-tenant/);
    expect(req.request.body).toEqual({ tenantId: null });
    req.flush({ accessToken: 'new.jwt', user: { id: 1, roles: [], activeTenantId: null, ... } });
    expect(service.isClientMode()).toBe(true);
  });
});
```

**New** : `tenant-switcher.component.spec.ts`

```ts
describe('TenantSwitcherComponent', () => {
  it('is hidden when availableTenants is empty', () => { ... });
  it('shows the active tenant name when activeTenantId is set', () => { ... });
  it('shows "Client mode" when activeTenantId is null', () => { ... });
  it('calls auth.switchTenant when a menu item is clicked', () => { ... });
  it('skips switch when clicking the already-active tenant', () => { ... });
});
```

**Update** : existing component specs that mock `User` need `roles[]` instead of `role`, plus the new tenant fields. Fixture helper to add :

```ts
export function mockUser(overrides: Partial<User> = {}): User {
  return {
    id: 1, name: 'Sophie', email: 'sophie@x.com',
    provider: AuthProvider.LOCAL,
    roles: [Role.PRO],
    activeTenantId: 42,
    availableTenants: [{ id: 42, slug: 'salon-x', name: 'Salon X' }],
    ...overrides,
  };
}
```

---

## 9. Open decisions (settled by brainstorming)

| Decision | Choice |
|---|---|
| Scope of PR2 | A (internal migration) + B (selector) + C (client mode) ; D (commercial UI) deferred |
| Selector placement | Header dropdown |
| Selector visibility | Hidden if 0 tenants ; shown if ≥1 (covers single-tenant + client-mode toggle) |
| Client mode mechanism | Item "Mode client" in the same selector |
| Legacy `role` migration | Remove from `UserDto` server-side in this PR |
| `hasRole(ADMIN)` semantics | No auto-promotion ; callers pass `Role.ADMIN` explicitly when intended |

---

## 10. Risks

- **`UserDto` shape change is a breaking API change.** Any third-party consumer of `/api/auth/me` or `/api/auth/login` reading `user.role` (String) breaks. Today only the frontend in this repo consumes it — confirmed by a code search at spec time. Document in PR description.
- **`switchTenant(null)` is a privilege drop, not an escalation** — safe by construction (we always emit fewer roles). No additional authz needed.
- **`Role` enum value removal** : `Role.USER` removal in frontend may leak via mocks that aren't migrated. Mitigation : delete the enum value first, fix all compile errors.

---

## 11. Estimated effort

| Block | Effort |
|---|---|
| Backend §3 (UserDto + switchTenant null + tests) | 1h |
| AuthService §5 + model §4 | 1h |
| TenantSwitcher §6 + translations | 2h |
| Migrate 17 callsites §7 | 1h30 |
| Tests §8 (frontend new + existing migration) | 2h |
| **Total** | **~7h30** |

---

## 12. Next steps after PR2

- **PR3** : Commercial UI (dashboard for COMMERCIAL role).
- **Backlog** : Per-tenant context in tenant-features.service (today it queries `/api/pro/tenant` against the JWT-driven TenantContext ; needs verification after switch-tenant).
- **Audit log** for switch-tenant (mentioned in PR1 code review).
