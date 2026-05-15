# PR2 — Frontend Multi-Role Scoped RBAC Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Migrate the Angular frontend from the legacy `User.role: Role` field to scoped roles (`roles[]` + `activeTenantId` + `availableTenants`), ship a header tenant switcher with a "client mode" entry, and drop the legacy `role` field server-side now that no consumer reads it.

**Architecture:** Single source of truth in `AuthService` — adds computed signals for `roles`, `activeTenantId`, `availableTenants`, `isClientMode`, plus a `hasRole(...)` helper and `switchTenant(tenantId | null)` method. New `TenantSwitcher` component lives in the header. Backend `UserDto` gains `activeTenantId` + `availableTenants` and loses the legacy `role` String.

**Tech Stack:** Angular 20 (standalone, zoneless), Material 3, Transloco i18n, Spring Boot 3.5.4 backend.

**Spec:** `docs/superpowers/specs/2026-05-15-pr2-frontend-rbac-design.md`

---

## File Structure

### Backend changes

| Path | Action | Responsibility |
|---|---|---|
| `backend/src/main/java/com/luxpretty/app/auth/dto/UserDto.java` | Modify | drop `role: String`, add `activeTenantId: Long` + `availableTenants: List<TenantSummary>` |
| `backend/src/main/java/com/luxpretty/app/me/web/dto/SwitchTenantRequest.java` | Modify | drop `@NotNull` on `tenantId` |
| `backend/src/main/java/com/luxpretty/app/me/web/dto/TenantSummary.java` | Keep | already exists, reuse |
| `backend/src/main/java/com/luxpretty/app/auth/AuthController.java` | Modify | refactor `buildAuthResponse` / `buildUserDto` / `toUserDto` to populate new fields, drop `pickLegacyRole` |
| `backend/src/main/java/com/luxpretty/app/me/web/MeController.java` | Modify | accept null tenantId, drop `pickLegacyRole`, populate new UserDto fields |
| `backend/src/main/java/com/luxpretty/app/auth/OAuth2AuthenticationSuccessHandler.java` | No code change | already uses `tokenService.generateToken(...)` ; UserDto isn't built here |
| `backend/src/test/java/com/luxpretty/app/auth/AuthControllerTests.java` | Modify | migrate role assertions |
| `backend/src/test/java/com/luxpretty/app/me/web/MeControllerTests.java` | Modify | migrate + add null-tenantId test |
| `backend/src/test/java/com/luxpretty/app/auth/integration/AuthFlowIntegrationTests.java` | Modify | adapt to new shape |

### Frontend changes

| Path | Action | Responsibility |
|---|---|---|
| `frontend/src/app/core/auth/auth.model.ts` | Modify | drop `Role.USER`, add `TenantSummary`, change `User` shape |
| `frontend/src/app/core/auth/auth.service.ts` | Modify | add `roles` / `activeTenantId` / `availableTenants` / `isClientMode` signals, `hasRole(...)`, `switchTenant(...)`, revise `navigateByRole()` |
| `frontend/src/app/core/auth/role.guard.ts` | Modify | use `hasRole(requiredRole, Role.ADMIN)` |
| `frontend/src/app/shared/layout/header/tenant-switcher/tenant-switcher.component.ts` | Create | header dropdown with tenants + client mode entry |
| `frontend/src/app/shared/layout/header/tenant-switcher/tenant-switcher.component.html` | Create | template |
| `frontend/src/app/shared/layout/header/tenant-switcher/tenant-switcher.component.scss` | Create | minimal styles |
| `frontend/src/app/shared/layout/header/tenant-switcher/tenant-switcher.component.spec.ts` | Create | 5 unit tests |
| `frontend/src/app/shared/layout/header/header.ts` | Modify | import switcher, use `hasRole(...)` |
| `frontend/src/app/shared/layout/header/header.html` | Modify | embed `<lp-tenant-switcher>` + replace `?.role === 'PRO'` |
| `frontend/src/app/shared/layout/footer/footer.ts` | Modify | use `hasRole(Role.PRO, Role.ADMIN)` |
| `frontend/src/app/shared/layout/footer/footer.spec.ts` | Modify | drop `Role.USER`, use new fixtures |
| `frontend/src/app/shared/layout/bottom-nav/bottom-nav.component.ts` | Modify | use `hasRole(...)` |
| `frontend/src/app/shared/layout/navigation/sidenav-menu.ts` | Modify | use `hasRole(Role.PRO, Role.ADMIN)` |
| `frontend/src/app/core/tenant/tenant-features.service.ts` | Modify | use `hasRole(Role.PRO, Role.ADMIN)` |
| `frontend/src/app/features/posts/salon-posts-viewer/salon-posts-viewer.component.ts` | Modify | use `hasRole(Role.PRO, Role.ADMIN)` |
| `frontend/src/app/core/auth/auth.service.spec.ts` | Create or extend | tests for `hasRole`, `isClientMode`, `switchTenant` |
| `frontend/public/i18n/fr.json` | Modify | add 3 keys |
| `frontend/public/i18n/en.json` | Modify | add 3 keys |

---

## Cross-cutting decisions (settled in brainstorming + spec §9)

- `hasRole(Role.X)` does NOT auto-promote ADMIN. Callers pass `Role.ADMIN` explicitly when an ADMIN should bypass.
- `TenantSwitcher` is visible if `availableTenants().length >= 1` (covers both salon switching and client-mode toggle for single-tenant pros).
- `switchTenant(null)` is allowed for any authenticated user (privilege drop).
- Legacy `role: String` is dropped from `UserDto` in this PR — breaking change documented in PR description.

---

## Task 1: Backend — UserDto reshape + SwitchTenantRequest accepts null

**Goal:** UserDto now exposes `roles[]`, `activeTenantId`, `availableTenants[]` ; legacy `role` String is dropped. `SwitchTenantRequest.tenantId` becomes nullable.

**Files:**
- Modify: `backend/src/main/java/com/luxpretty/app/auth/dto/UserDto.java`
- Modify: `backend/src/main/java/com/luxpretty/app/me/web/dto/SwitchTenantRequest.java`
- Modify: `backend/src/main/java/com/luxpretty/app/auth/AuthController.java`
- Modify: `backend/src/main/java/com/luxpretty/app/me/web/MeController.java`

### Steps

- [ ] **Step 1: Reshape `UserDto`**

Replace the contents of `backend/src/main/java/com/luxpretty/app/auth/dto/UserDto.java`:

```java
package com.luxpretty.app.auth.dto;

import com.luxpretty.app.me.web.dto.TenantSummary;
import com.luxpretty.app.users.domain.AuthProvider;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
public class UserDto {
    private Long id;
    private String name;
    private String email;
    private String imageUrl;
    private AuthProvider provider;
    private List<String> roles;
    private Long activeTenantId;
    private List<TenantSummary> availableTenants;
}
```

The legacy `role: String` + `roles: List<String>` (both kept in PR1) becomes only `roles: List<String>`, plus new `activeTenantId` + `availableTenants`.

- [ ] **Step 2: Relax `SwitchTenantRequest`**

Replace `backend/src/main/java/com/luxpretty/app/me/web/dto/SwitchTenantRequest.java`:

```java
package com.luxpretty.app.me.web.dto;

public record SwitchTenantRequest(Long tenantId) {}
```

- [ ] **Step 3: Refactor AuthController helpers**

In `backend/src/main/java/com/luxpretty/app/auth/AuthController.java`:

a. Inject `TenantRepository` (add field + constructor parameter). Look for the existing constructor block and add:

```java
    private final com.luxpretty.app.tenant.repo.TenantRepository tenantRepository;
```

Add it to the constructor:

```java
    public AuthController(UserRepository userRepository, PasswordEncoder passwordEncoder, TokenService tokenService,
                          TenantProvisioningService tenantProvisioningService, TenantRepository tenantRepository,
                          MailOutboxService mailOutbox, UserRoleService userRoleService) {
        // (TenantRepository was already injected — no change needed, just verified)
```

Actually `TenantRepository` is already injected. Verify by grepping :

```bash
grep -n "TenantRepository tenantRepository" backend/src/main/java/com/luxpretty/app/auth/AuthController.java
```

Should return one line in the constructor params + one in the field declarations. No change needed.

b. Replace the helper methods at the bottom of the class. Find the block starting with `private AuthResponse buildAuthResponse(`:

```java
    private AuthResponse buildAuthResponse(User user, Long activeTenantId) {
        UserDto dto = buildUserDto(user, activeTenantId);
        String token = tokenService.generateToken(user.getId(), user.getEmail(), dto.getRoles(), activeTenantId);
        return new AuthResponse(token, dto);
    }

    private UserDto buildUserDto(User user, Long activeTenantId) {
        Set<Role> resolved = userRoleService.resolveRoles(user.getId(), activeTenantId);
        List<String> roleNames = resolved.stream().map(Enum::name).toList();
        List<com.luxpretty.app.me.web.dto.TenantSummary> tenants = userRoleService.findUserTenantIds(user.getId()).stream()
                .map(tenantRepository::findById)
                .flatMap(java.util.Optional::stream)
                .map(t -> new com.luxpretty.app.me.web.dto.TenantSummary(t.getId(), t.getSlug(), t.getName()))
                .toList();
        return UserDto.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .imageUrl(user.getImageUrl())
                .provider(user.getProvider())
                .roles(roleNames)
                .activeTenantId(activeTenantId)
                .availableTenants(tenants)
                .build();
    }
```

Delete the old `toUserDto(User, Set<Role>, List<String>)` and `pickLegacyRole(Set<Role>)` methods.

- [ ] **Step 4: Refactor MeController.switchTenant for null tenant + new UserDto shape**

In `backend/src/main/java/com/luxpretty/app/me/web/MeController.java`, replace the `switchTenant` method:

```java
    @PostMapping("/switch-tenant")
    public ResponseEntity<AuthResponse> switchTenant(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody SwitchTenantRequest req) {

        Long requested = req.tenantId();
        if (requested != null) {
            List<Long> allowed = userRoleService.findUserTenantIds(principal.getId());
            if (!allowed.contains(requested)) {
                throw new AccessDeniedException("User has no role on tenant " + requested);
            }
        }

        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new IllegalStateException("User missing"));
        String newToken = tokenService.generateToken(user, requested);

        Set<Role> resolved = userRoleService.resolveRoles(user.getId(), requested);
        List<String> roleNames = resolved.stream().map(Enum::name).toList();
        List<TenantSummary> tenants = userRoleService.findUserTenantIds(user.getId()).stream()
                .map(tenantRepository::findById)
                .flatMap(java.util.Optional::stream)
                .map(t -> new TenantSummary(t.getId(), t.getSlug(), t.getName()))
                .toList();

        UserDto dto = UserDto.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .imageUrl(user.getImageUrl())
                .provider(user.getProvider())
                .roles(roleNames)
                .activeTenantId(requested)
                .availableTenants(tenants)
                .build();

        return ResponseEntity.ok(new AuthResponse(newToken, dto));
    }
```

Delete the `pickLegacyRole(Set<Role>)` method at the bottom — no longer referenced.

- [ ] **Step 5: Compile**

```bash
cd backend && mvn compile
```

Expected: BUILD SUCCESS. If a caller still references `.role(...)` or `pickLegacyRole(...)` on UserDto, grep and migrate — there shouldn't be any.

```bash
grep -rn "pickLegacyRole\|userDto.getRole()\|UserDto.*\.role(" backend/src/main backend/src/test
```

Expected: zero hits (other than the method definitions themselves which we deleted).

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/luxpretty/app/auth/dto/UserDto.java \
        backend/src/main/java/com/luxpretty/app/me/web/dto/SwitchTenantRequest.java \
        backend/src/main/java/com/luxpretty/app/auth/AuthController.java \
        backend/src/main/java/com/luxpretty/app/me/web/MeController.java
git commit -m "feat(auth): UserDto exposes activeTenantId + availableTenants, drops legacy role"
```

---

## Task 2: Backend tests — migrate role assertions + add null-tenant test

**Files:**
- Modify: `backend/src/test/java/com/luxpretty/app/auth/AuthControllerTests.java`
- Modify: `backend/src/test/java/com/luxpretty/app/me/web/MeControllerTests.java`
- Modify: `backend/src/test/java/com/luxpretty/app/auth/integration/AuthFlowIntegrationTests.java`

### Steps

- [ ] **Step 1: Migrate AuthControllerTests assertions**

In `backend/src/test/java/com/luxpretty/app/auth/AuthControllerTests.java`, find every `$.user.role` JSON path and replace with `$.user.roles[0]`. The 3 happy-path tests (`register_happyPath_returns200WithToken`, `login_happyPath_returnsTokenAndUser`, current `getCurrentUser` tests) need this. Grep first :

```bash
grep -n '\$\.user\.role' backend/src/test/java/com/luxpretty/app/auth/AuthControllerTests.java
```

For each line, replace `.andExpect(jsonPath("$.user.role").value("PRO"))` with `.andExpect(jsonPath("$.user.roles[0]").value("PRO"))`.

Add new assertions on `$.user.activeTenantId` + `$.user.availableTenants` to the `register_happyPath_returns200WithToken` test, after the existing `$.user.email` assertion:

```java
                .andExpect(jsonPath("$.user.roles[0]").value("PRO"))
                .andExpect(jsonPath("$.user.activeTenantId").value(42))
                .andExpect(jsonPath("$.user.availableTenants").isArray());
```

The mock stub for that test already returns `tenant.id=42L`, so 42 is the expected activeTenantId.

- [ ] **Step 2: Migrate MeControllerTests + add null-tenant test**

In `backend/src/test/java/com/luxpretty/app/me/web/MeControllerTests.java`:

a. In `switchTenant_returnsNewToken_whenUserHasAssignment`, replace `$.user.role` with `$.user.roles[0]` :

```java
                .andExpect(jsonPath("$.accessToken").value("new.jwt.token"))
                .andExpect(jsonPath("$.user.roles[0]").value("PRO"))
                .andExpect(jsonPath("$.user.activeTenantId").value(42));
```

Drop the line `.andExpect(jsonPath("$.user.role").value("PRO"))` if present.

b. Add a new test method `switchTenant_acceptsNullTenantId_emitsClientMode` (after the existing 4 tests):

```java
    @Test
    void switchTenant_acceptsNullTenantId_emitsClientMode() throws Exception {
        when(userRoleService.findUserTenantIds(1L)).thenReturn(List.of(42L));
        User u = User.builder().id(1L).email("a@a.com").name("A").build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(u));
        when(tokenService.generateToken(any(User.class), eq((Long) null))).thenReturn("client.mode.token");
        when(userRoleService.resolveRoles(1L, null)).thenReturn(Set.of());

        mvc.perform(MockMvcRequestBuilders.post("/api/me/switch-tenant")
                        .with(authentication(authFor(1L)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\": null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("client.mode.token"))
                .andExpect(jsonPath("$.user.activeTenantId").doesNotExist())
                .andExpect(jsonPath("$.user.roles").isEmpty());
    }
```

- [ ] **Step 3: Migrate AuthFlowIntegrationTests**

In `backend/src/test/java/com/luxpretty/app/auth/integration/AuthFlowIntegrationTests.java`, find the assertion :

```java
                .andExpect(jsonPath("$.role").doesNotExist())
                .andExpect(jsonPath("$.roles").isEmpty());
```

(Already migrated in PR1.) Verify it still passes after Task 1 — no change needed unless the test starts failing because `activeTenantId` or `availableTenants` is null/missing.

Run the full integration test once to confirm :

```bash
cd backend && mvn test -Dtest=AuthFlowIntegrationTests
```

Expected: 4/4 PASS.

- [ ] **Step 4: Run the full suite**

```bash
cd backend && mvn test
```

Expected: all green. New test count should be +1 (the null-tenant test).

- [ ] **Step 5: Commit**

```bash
git add backend/src/test/java/com/luxpretty/app/auth/AuthControllerTests.java \
        backend/src/test/java/com/luxpretty/app/me/web/MeControllerTests.java \
        backend/src/test/java/com/luxpretty/app/auth/integration/AuthFlowIntegrationTests.java
git commit -m "test(auth): migrate role assertions + add switchTenant null path"
```

---

## Task 3: Frontend models — drop Role.USER, add TenantSummary, reshape User

**Files:**
- Modify: `frontend/src/app/core/auth/auth.model.ts`

### Steps

- [ ] **Step 1: Reshape models**

Replace the contents of `frontend/src/app/core/auth/auth.model.ts`:

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

`Role.USER` is removed. Any code that references it becomes a compile error — those will be fixed in subsequent tasks.

- [ ] **Step 2: Compile (expect failures)**

```bash
cd frontend && npx tsc --noEmit
```

Expected: errors in `auth.service.ts` (line 184 `user.role`), `tenant-features.service.ts`, `role.guard.ts`, layout components, and `footer.spec.ts` (`Role.USER`). These will all be fixed in subsequent tasks. For now, accept the failures and move on.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/app/core/auth/auth.model.ts
git commit -m "feat(auth): drop Role.USER, add TenantSummary, reshape User with roles[] + tenant fields"
```

---

## Task 4: AuthService — add `hasRole`, `switchTenant`, computed signals

**Files:**
- Modify: `frontend/src/app/core/auth/auth.service.ts`

### Steps

- [ ] **Step 1: Add new imports + signals**

In `frontend/src/app/core/auth/auth.service.ts`, replace the import block at the top:

```ts
import { Injectable, signal, computed, inject, PLATFORM_ID } from '@angular/core';
import { Role, TenantSummary, User, AuthResponse } from './auth.model';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, tap, catchError, of, map } from 'rxjs';
import { isPlatformBrowser } from '@angular/common';
import { API_BASE_URL } from '../config/api-base-url.token';
import { TenantStatusService } from '../tenant/tenant-status.service';
```

Inside the class, after the existing `readonly user = this.currentUser.asReadonly();` line, add the new computed signals:

```ts
  // Scoped RBAC signals derived from the JWT-driven currentUser
  readonly roles = computed<Role[]>(() => this.currentUser()?.roles ?? []);
  readonly activeTenantId = computed<number | null>(() => this.currentUser()?.activeTenantId ?? null);
  readonly availableTenants = computed<TenantSummary[]>(() => this.currentUser()?.availableTenants ?? []);
  readonly isClientMode = computed<boolean>(() => this.activeTenantId() === null);
```

- [ ] **Step 2: Add `hasRole` helper**

In the same file, add at the end of the class (before the closing `}`):

```ts
  /**
   * True if the user holds ANY of the given roles.
   * ADMIN is NOT auto-promoted — callers that want ADMIN to bypass must
   * include Role.ADMIN explicitly: hasRole(Role.PRO, Role.ADMIN).
   */
  hasRole(...required: Role[]): boolean {
    const userRoles = this.roles();
    return required.some(r => userRoles.includes(r));
  }
```

- [ ] **Step 3: Add `switchTenant` method**

In the same file, add another method at the end of the class:

```ts
  /**
   * Switch the active tenant. Pass null to drop into client mode (no tenant
   * context, only GLOBAL roles apply). Re-issues the JWT and updates the
   * stored user.
   */
  switchTenant(tenantId: number | null): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(
      `${this.apiBaseUrl}/api/me/switch-tenant`,
      { tenantId }
    ).pipe(
      tap(response => {
        this.setToken(response.accessToken);
        this.currentUser.set(response.user);
      })
    );
  }
```

- [ ] **Step 4: Revise `navigateByRole`**

In the same file, replace the existing `navigateByRole` method (lines around 180-192):

```ts
  /**
   * Navigate to the appropriate dashboard based on the user's roles and
   * whether a tenant context is active. In client mode (no activeTenantId),
   * pros and employees go to the public home — they're acting as clients.
   */
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

- [ ] **Step 5: Compile (errors elsewhere expected)**

```bash
cd frontend && npx tsc --noEmit 2>&1 | grep "error TS" | head -20
```

Expected: errors in `role.guard.ts`, `tenant-features.service.ts`, layout components, and a few `.spec.ts`. `auth.service.ts` itself should compile clean.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/app/core/auth/auth.service.ts
git commit -m "feat(auth): AuthService.hasRole + switchTenant + scoped-RBAC signals"
```

---

## Task 5: Migrate `role.guard.ts` + `tenant-features.service.ts` + non-layout consumers

**Files:**
- Modify: `frontend/src/app/core/auth/role.guard.ts`
- Modify: `frontend/src/app/core/tenant/tenant-features.service.ts`
- Modify: `frontend/src/app/features/posts/salon-posts-viewer/salon-posts-viewer.component.ts`

### Steps

- [ ] **Step 1: Migrate `role.guard.ts`**

Replace the contents of `frontend/src/app/core/auth/role.guard.ts`:

```ts
import { inject, PLATFORM_ID } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { CanActivateFn, Router } from '@angular/router';
import { map, of } from 'rxjs';
import { AuthService } from './auth.service';
import { Role } from './auth.model';

export const roleGuard = (requiredRole: Role): CanActivateFn => {
  return () => {
    const platformId = inject(PLATFORM_ID);

    // During SSR, always allow — the real check happens after client hydration
    if (!isPlatformBrowser(platformId)) {
      return of(true);
    }

    const authService = inject(AuthService);
    const router = inject(Router);

    return authService.checkAuthentication().pipe(
      map((authenticated) => {
        if (!authenticated) {
          return router.createUrlTree(['/login']);
        }
        if (authService.hasRole(requiredRole, Role.ADMIN)) {
          return true;
        }
        return router.createUrlTree(['/']);
      })
    );
  };
};
```

- [ ] **Step 2: Migrate `tenant-features.service.ts`**

In `frontend/src/app/core/tenant/tenant-features.service.ts`, find the line :

```ts
      const isPro = user?.role === Role.PRO || user?.role === Role.ADMIN;
```

Replace with :

```ts
      const isPro = this.authService.hasRole(Role.PRO, Role.ADMIN);
```

(The `user` variable in the effect is still useful for the `loadFeatures()` trigger — keep the surrounding `effect(() => { const user = this.authService.user(); ... })` block, just change the `isPro` derivation.)

- [ ] **Step 3: Migrate `salon-posts-viewer.component.ts`**

In `frontend/src/app/features/posts/salon-posts-viewer/salon-posts-viewer.component.ts`, find around line 806 :

```ts
    this.isPro.set(user?.role === Role.PRO || user?.role === Role.ADMIN);
```

Replace with :

```ts
    this.isPro.set(this.authService.hasRole(Role.PRO, Role.ADMIN));
```

Verify `this.authService` is the inject name in this file (it may be `auth` or `authService`). Grep :

```bash
grep -n "inject(AuthService)\|authService:\|auth:" frontend/src/app/features/posts/salon-posts-viewer/salon-posts-viewer.component.ts
```

Adjust the replacement to match the local field name.

- [ ] **Step 4: Compile**

```bash
cd frontend && npx tsc --noEmit 2>&1 | grep "error TS" | head -20
```

Expected: remaining errors only in layout components and footer.spec.ts.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/core/auth/role.guard.ts \
        frontend/src/app/core/tenant/tenant-features.service.ts \
        frontend/src/app/features/posts/salon-posts-viewer/salon-posts-viewer.component.ts
git commit -m "refactor(auth): migrate guard + tenant-features + salon-posts to hasRole()"
```

---

## Task 6: Migrate layout components (header / footer / sidenav / bottom-nav)

**Files:**
- Modify: `frontend/src/app/shared/layout/header/header.ts`
- Modify: `frontend/src/app/shared/layout/header/header.html`
- Modify: `frontend/src/app/shared/layout/footer/footer.ts`
- Modify: `frontend/src/app/shared/layout/footer/footer.spec.ts`
- Modify: `frontend/src/app/shared/layout/bottom-nav/bottom-nav.component.ts`
- Modify: `frontend/src/app/shared/layout/navigation/sidenav-menu.ts`

### Steps

- [ ] **Step 1: Migrate `header.ts`**

In `frontend/src/app/shared/layout/header/header.ts`, find around line 34 :

```ts
    return role === Role.PRO || role === Role.ADMIN || role === Role.EMPLOYEE;
```

Find the surrounding method context (likely a computed or getter). Replace the body with :

```ts
    return this.authService.hasRole(Role.PRO, Role.ADMIN, Role.EMPLOYEE);
```

Remove any now-unused `role` local variable.

- [ ] **Step 2: Migrate `header.html`**

In `frontend/src/app/shared/layout/header/header.html`, find line 126 :

```html
        @if (authService.user()?.role === 'PRO' || authService.user()?.role === 'ADMIN') {
```

Replace with :

```html
        @if (authService.hasRole(Role.PRO, Role.ADMIN)) {
```

If `Role` is not exposed to the template (only the service is), use the existing helper getter on the component or expose `Role` :

In `header.ts`, add at the top of the class (if not already there):

```ts
  protected readonly Role = Role;
```

And in the import block, ensure `Role` is imported from `../../../core/auth/auth.model`.

- [ ] **Step 3: Migrate `footer.ts`**

In `frontend/src/app/shared/layout/footer/footer.ts`, find around line 22 :

```ts
    return role === Role.PRO || role === Role.ADMIN;
```

Replace the method body with :

```ts
    return this.authService.hasRole(Role.PRO, Role.ADMIN);
```

Remove the `role` local variable.

- [ ] **Step 4: Migrate `footer.spec.ts`**

In `frontend/src/app/shared/layout/footer/footer.spec.ts`, find lines 62 and 73 :

```ts
      user: { id: 1, role: Role.USER },
```

```ts
      { user: { id: 1, role: Role.USER }, isAuthenticated: true },
```

Replace both with the new User shape (a CLIENT implicite has `roles: []`):

```ts
      user: { id: 1, name: 'X', email: 'x@x.com', provider: AuthProvider.LOCAL, roles: [], activeTenantId: null, availableTenants: [] },
```

And :

```ts
      { user: { id: 1, name: 'X', email: 'x@x.com', provider: AuthProvider.LOCAL, roles: [], activeTenantId: null, availableTenants: [] }, isAuthenticated: true },
```

Make sure `AuthProvider` is imported at the top of the file. Drop the `Role.USER` import if no longer needed.

- [ ] **Step 5: Migrate `bottom-nav.component.ts`**

In `frontend/src/app/shared/layout/bottom-nav/bottom-nav.component.ts`, find around line 183 :

```ts
      if ((role === Role.PRO || role === Role.ADMIN || role === Role.EMPLOYEE) && this.authService.isAuthenticated()) {
```

Replace with :

```ts
      if (this.authService.hasRole(Role.PRO, Role.ADMIN, Role.EMPLOYEE) && this.authService.isAuthenticated()) {
```

And around line 213 :

```ts
    if (role === Role.PRO || role === Role.ADMIN) {
```

Replace with :

```ts
    if (this.authService.hasRole(Role.PRO, Role.ADMIN)) {
```

Remove any `const role = ...` declaration that is now unused.

- [ ] **Step 6: Migrate `sidenav-menu.ts`**

In `frontend/src/app/shared/layout/navigation/sidenav-menu.ts`, find around line 43 :

```ts
    const isPro = role === Role.PRO || role === Role.ADMIN;
```

Replace with :

```ts
    const isPro = this.authService.hasRole(Role.PRO, Role.ADMIN);
```

Remove the now-unused `role` local variable.

- [ ] **Step 7: Compile**

```bash
cd frontend && npx tsc --noEmit 2>&1 | grep "error TS" | head -20
```

Expected: zero TypeScript errors (the remaining error from Task 4's compile was the `user.role` access in `auth.service.ts` which Task 4 fixed via `navigateByRole` revision).

- [ ] **Step 8: Run unit tests**

```bash
cd frontend && npm test -- --watch=false --browsers=ChromeHeadless 2>&1 | tail -40
```

Expected: existing tests still pass (or only the footer.spec.ts has been updated and now passes).

- [ ] **Step 9: Commit**

```bash
git add frontend/src/app/shared/layout/
git commit -m "refactor(layout): migrate header/footer/sidenav/bottom-nav to hasRole()"
```

---

## Task 7: Create TenantSwitcher component

**Files:**
- Create: `frontend/src/app/shared/layout/header/tenant-switcher/tenant-switcher.component.ts`
- Create: `frontend/src/app/shared/layout/header/tenant-switcher/tenant-switcher.component.html`
- Create: `frontend/src/app/shared/layout/header/tenant-switcher/tenant-switcher.component.scss`

### Steps

- [ ] **Step 1: Create the component class**

Create `frontend/src/app/shared/layout/header/tenant-switcher/tenant-switcher.component.ts`:

```ts
import { Component, inject, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatMenuModule } from '@angular/material/menu';
import { MatIconModule } from '@angular/material/icon';
import { MatDividerModule } from '@angular/material/divider';
import { MatSnackBar } from '@angular/material/snack-bar';
import { TranslocoModule, TranslocoService } from '@jsverse/transloco';
import { AuthService } from '../../../../core/auth/auth.service';

@Component({
  selector: 'lp-tenant-switcher',
  standalone: true,
  imports: [
    CommonModule,
    MatButtonModule,
    MatMenuModule,
    MatIconModule,
    MatDividerModule,
    TranslocoModule,
  ],
  templateUrl: './tenant-switcher.component.html',
  styleUrl: './tenant-switcher.component.scss',
})
export class TenantSwitcherComponent {
  private readonly auth = inject(AuthService);
  private readonly snackbar = inject(MatSnackBar);
  private readonly i18n = inject(TranslocoService);

  protected readonly tenants = this.auth.availableTenants;
  protected readonly activeId = this.auth.activeTenantId;

  protected readonly visible = computed(() =>
    this.auth.isAuthenticated() && this.tenants().length >= 1
  );

  protected readonly iconName = computed(() =>
    this.activeId() === null ? 'shopping_bag' : 'store'
  );

  protected readonly label = computed(() => {
    if (this.activeId() === null) {
      return this.i18n.translate('common.clientMode');
    }
    const t = this.tenants().find(t => t.id === this.activeId());
    return t?.name || t?.slug || '—';
  });

  protected switch(tenantId: number | null): void {
    if (tenantId === this.activeId()) {
      return; // no-op
    }
    this.auth.switchTenant(tenantId).subscribe({
      next: () => this.auth.navigateByRole(),
      error: () => this.snackbar.open(
        this.i18n.translate('errors.tenantSwitchFailed'),
        undefined,
        { duration: 3000 }
      ),
    });
  }
}
```

- [ ] **Step 2: Create the template**

Create `frontend/src/app/shared/layout/header/tenant-switcher/tenant-switcher.component.html`:

```html
@if (visible()) {
  <button mat-stroked-button [matMenuTriggerFor]="menu" class="tenant-chip" data-testid="tenant-switcher-chip">
    <mat-icon>{{ iconName() }}</mat-icon>
    <span class="tenant-label">{{ label() }}</span>
    <mat-icon>arrow_drop_down</mat-icon>
  </button>
  <mat-menu #menu="matMenu">
    @for (t of tenants(); track t.id) {
      <button mat-menu-item (click)="switch(t.id)" [attr.data-testid]="'tenant-item-' + t.id">
        @if (activeId() === t.id) {
          <mat-icon>check</mat-icon>
        } @else {
          <mat-icon></mat-icon>
        }
        <span>{{ t.name || t.slug }}</span>
      </button>
    }
    <mat-divider></mat-divider>
    <button mat-menu-item (click)="switch(null)" data-testid="tenant-item-client-mode">
      @if (activeId() === null) {
        <mat-icon>check</mat-icon>
      } @else {
        <mat-icon></mat-icon>
      }
      <span>{{ 'common.clientMode' | transloco }}</span>
    </button>
  </mat-menu>
}
```

- [ ] **Step 3: Create the styles**

Create `frontend/src/app/shared/layout/header/tenant-switcher/tenant-switcher.component.scss`:

```scss
.tenant-chip {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  border-radius: 1.25rem;
  padding: 0 0.75rem;

  .tenant-label {
    max-width: 12ch;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}
```

- [ ] **Step 4: Add translation keys**

In `frontend/public/i18n/fr.json`, add under the appropriate top-level keys :

```json
{
  "common": {
    "...": "...",
    "clientMode": "Mode client"
  },
  "errors": {
    "...": "...",
    "tenantSwitchFailed": "Impossible de changer de salon"
  }
}
```

In `frontend/public/i18n/en.json`:

```json
{
  "common": {
    "...": "...",
    "clientMode": "Client mode"
  },
  "errors": {
    "...": "...",
    "tenantSwitchFailed": "Could not switch salon"
  }
}
```

(Open the existing JSON files, find the `common` and `errors` blocks, and add the new keys preserving alphabetical order if that's the project convention.)

- [ ] **Step 5: Compile**

```bash
cd frontend && npx tsc --noEmit
```

Expected: zero errors.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/app/shared/layout/header/tenant-switcher/ \
        frontend/public/i18n/fr.json \
        frontend/public/i18n/en.json
git commit -m "feat(layout): TenantSwitcher header component + i18n keys"
```

---

## Task 8: Integrate TenantSwitcher into header

**Files:**
- Modify: `frontend/src/app/shared/layout/header/header.ts`
- Modify: `frontend/src/app/shared/layout/header/header.html`

### Steps

- [ ] **Step 1: Import the component in `header.ts`**

In `frontend/src/app/shared/layout/header/header.ts`, find the `imports` array in the `@Component` decorator. Add `TenantSwitcherComponent`:

```ts
import { TenantSwitcherComponent } from './tenant-switcher/tenant-switcher.component';

// ...

@Component({
  selector: 'lp-header',
  standalone: true,
  imports: [
    // ... existing imports ...
    TenantSwitcherComponent,
  ],
  // ...
})
```

- [ ] **Step 2: Place the switcher in the header template**

In `frontend/src/app/shared/layout/header/header.html`, find the section where the authenticated user's avatar / menu is rendered. Add `<lp-tenant-switcher>` just before the user menu, wrapped in the existing `@if (authService.isAuthenticated())` block. The component handles its own visibility (≥1 tenant), but wrapping in the auth check avoids rendering for anonymous users.

Locate the block (grep `authService.isAuthenticated\|authService.user()` in the file) and insert :

```html
@if (authService.isAuthenticated()) {
  <lp-tenant-switcher></lp-tenant-switcher>
  <!-- existing avatar / menu code -->
}
```

If the existing code already has `@if (authService.isAuthenticated()) { ... }`, just add the `<lp-tenant-switcher></lp-tenant-switcher>` line inside it.

- [ ] **Step 3: Visual smoke (start the dev server)**

```bash
cd frontend && npm start
```

Open `http://localhost:4200`. Login as a pro with at least one tenant. The chip should appear in the header. Click it — the menu should show the tenant + the "Mode client" entry.

Stop the dev server when done.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/app/shared/layout/header/header.ts \
        frontend/src/app/shared/layout/header/header.html
git commit -m "feat(layout): integrate TenantSwitcher into header"
```

---

## Task 9: Unit tests for TenantSwitcher and AuthService extensions

**Files:**
- Create: `frontend/src/app/shared/layout/header/tenant-switcher/tenant-switcher.component.spec.ts`
- Create or extend: `frontend/src/app/core/auth/auth.service.spec.ts`

### Steps

- [ ] **Step 1: Check whether `auth.service.spec.ts` exists**

```bash
ls frontend/src/app/core/auth/auth.service.spec.ts 2>/dev/null
```

If it does, append the new tests below to the existing file. If not, create it with the skeleton :

```ts
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { AuthService } from './auth.service';
import { AuthProvider, Role, User } from './auth.model';

describe('AuthService', () => {
  let service: AuthService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
    });
    service = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  function setUser(overrides: Partial<User> = {}): void {
    const user: User = {
      id: 1, name: 'Sophie', email: 'sophie@x.com',
      provider: AuthProvider.LOCAL,
      roles: [Role.PRO],
      activeTenantId: 42,
      availableTenants: [{ id: 42, slug: 'salon-x', name: 'Salon X' }],
      ...overrides,
    };
    (service as any).currentUser.set(user);
  }

  // ... tests below ...
});
```

- [ ] **Step 2: Add `hasRole` tests**

Inside the `describe('AuthService', ...)` block, add :

```ts
  describe('hasRole', () => {
    it('returns false when user has no roles', () => {
      setUser({ roles: [] });
      expect(service.hasRole(Role.PRO)).toBe(false);
    });

    it('returns true when user has the required role', () => {
      setUser({ roles: [Role.PRO] });
      expect(service.hasRole(Role.PRO)).toBe(true);
    });

    it('returns true when user has any of multiple required roles', () => {
      setUser({ roles: [Role.EMPLOYEE] });
      expect(service.hasRole(Role.PRO, Role.ADMIN, Role.EMPLOYEE)).toBe(true);
    });

    it('does NOT auto-promote ADMIN — callers must include it explicitly', () => {
      setUser({ roles: [Role.ADMIN] });
      expect(service.hasRole(Role.PRO)).toBe(false);
      expect(service.hasRole(Role.PRO, Role.ADMIN)).toBe(true);
    });

    it('returns false when there is no current user', () => {
      (service as any).currentUser.set(null);
      expect(service.hasRole(Role.PRO)).toBe(false);
    });
  });
```

- [ ] **Step 3: Add `switchTenant` tests**

```ts
  describe('switchTenant', () => {
    it('POSTs tenantId and updates currentUser', () => {
      setUser({ activeTenantId: 42 });

      service.switchTenant(43).subscribe();

      const req = httpMock.expectOne(req => req.url.endsWith('/api/me/switch-tenant'));
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({ tenantId: 43 });
      req.flush({
        accessToken: 'new.jwt',
        tokenType: 'Bearer',
        user: {
          id: 1, name: 'Sophie', email: 'sophie@x.com',
          provider: AuthProvider.LOCAL,
          roles: ['PRO'],
          activeTenantId: 43,
          availableTenants: [{ id: 42, slug: 'salon-x', name: 'Salon X' }, { id: 43, slug: 'salon-y', name: 'Salon Y' }],
        },
      });

      expect(service.activeTenantId()).toBe(43);
      expect(service.getToken()).toBe('new.jwt');
    });

    it('accepts null tenantId (client mode)', () => {
      setUser({ activeTenantId: 42 });

      service.switchTenant(null).subscribe();

      const req = httpMock.expectOne(req => req.url.endsWith('/api/me/switch-tenant'));
      expect(req.request.body).toEqual({ tenantId: null });
      req.flush({
        accessToken: 'client.jwt',
        tokenType: 'Bearer',
        user: {
          id: 1, name: 'Sophie', email: 'sophie@x.com',
          provider: AuthProvider.LOCAL,
          roles: [],
          activeTenantId: null,
          availableTenants: [{ id: 42, slug: 'salon-x', name: 'Salon X' }],
        },
      });

      expect(service.isClientMode()).toBe(true);
      expect(service.roles()).toEqual([]);
    });
  });
```

- [ ] **Step 4: Run the AuthService tests**

```bash
cd frontend && npm test -- --watch=false --browsers=ChromeHeadless --include='**/auth.service.spec.ts' 2>&1 | tail -20
```

Expected: tests pass (or compile-fix any minor stub issues like the cast `(service as any).currentUser`).

- [ ] **Step 5: Create TenantSwitcher test**

Create `frontend/src/app/shared/layout/header/tenant-switcher/tenant-switcher.component.spec.ts`:

```ts
import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { signal } from '@angular/core';
import { provideTransloco } from '@jsverse/transloco';
import { of } from 'rxjs';
import { TenantSwitcherComponent } from './tenant-switcher.component';
import { AuthService } from '../../../../core/auth/auth.service';
import { TenantSummary } from '../../../../core/auth/auth.model';

describe('TenantSwitcherComponent', () => {
  let fixture: ComponentFixture<TenantSwitcherComponent>;
  let mockAuth: jasmine.SpyObj<AuthService> & {
    isAuthenticated: () => boolean;
    availableTenants: () => TenantSummary[];
    activeTenantId: () => number | null;
  };

  function setupAuth(overrides: {
    isAuthenticated?: boolean;
    availableTenants?: TenantSummary[];
    activeTenantId?: number | null;
  } = {}): void {
    const authenticated = signal(overrides.isAuthenticated ?? true);
    const tenants = signal(overrides.availableTenants ?? []);
    const active = signal(overrides.activeTenantId ?? null);

    mockAuth = jasmine.createSpyObj<AuthService>(
      'AuthService',
      ['switchTenant', 'navigateByRole'],
      {
        isAuthenticated: authenticated,
        availableTenants: tenants,
        activeTenantId: active,
      }
    ) as any;
    mockAuth.switchTenant.and.returnValue(of({} as any));

    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [TenantSwitcherComponent],
      providers: [
        provideNoopAnimations(),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        provideTransloco({
          config: {
            availableLangs: ['fr', 'en'],
            defaultLang: 'fr',
            fallbackLang: 'fr',
            reRenderOnLangChange: false,
            prodMode: false,
          },
          loader: { getTranslation: () => of({ common: { clientMode: 'Mode client' }, errors: { tenantSwitchFailed: 'Erreur' } }) } as any,
        }),
        { provide: AuthService, useValue: mockAuth },
      ],
    });
    fixture = TestBed.createComponent(TenantSwitcherComponent);
    fixture.detectChanges();
  }

  it('is hidden when availableTenants is empty', () => {
    setupAuth({ availableTenants: [] });
    const chip = fixture.nativeElement.querySelector('[data-testid="tenant-switcher-chip"]');
    expect(chip).toBeNull();
  });

  it('is visible when user has at least one tenant', () => {
    setupAuth({
      availableTenants: [{ id: 42, slug: 'salon-x', name: 'Salon X' }],
      activeTenantId: 42,
    });
    const chip = fixture.nativeElement.querySelector('[data-testid="tenant-switcher-chip"]');
    expect(chip).not.toBeNull();
  });

  it('calls auth.switchTenant when a tenant menu item is clicked', () => {
    setupAuth({
      availableTenants: [
        { id: 42, slug: 'salon-x', name: 'Salon X' },
        { id: 43, slug: 'salon-y', name: 'Salon Y' },
      ],
      activeTenantId: 42,
    });
    fixture.componentInstance['switch'](43);
    expect(mockAuth.switchTenant).toHaveBeenCalledWith(43);
  });

  it('calls auth.switchTenant(null) when client mode is clicked', () => {
    setupAuth({
      availableTenants: [{ id: 42, slug: 'salon-x', name: 'Salon X' }],
      activeTenantId: 42,
    });
    fixture.componentInstance['switch'](null);
    expect(mockAuth.switchTenant).toHaveBeenCalledWith(null);
  });

  it('skips the switch when clicking the already-active tenant', () => {
    setupAuth({
      availableTenants: [{ id: 42, slug: 'salon-x', name: 'Salon X' }],
      activeTenantId: 42,
    });
    fixture.componentInstance['switch'](42);
    expect(mockAuth.switchTenant).not.toHaveBeenCalled();
  });
});
```

- [ ] **Step 6: Run the TenantSwitcher tests**

```bash
cd frontend && npm test -- --watch=false --browsers=ChromeHeadless --include='**/tenant-switcher.component.spec.ts' 2>&1 | tail -20
```

Expected: 5/5 PASS.

- [ ] **Step 7: Run the full frontend test suite**

```bash
cd frontend && npm test -- --watch=false --browsers=ChromeHeadless 2>&1 | tail -30
```

Expected: all green. Existing tests should still pass after the migration.

- [ ] **Step 8: Commit**

```bash
git add frontend/src/app/core/auth/auth.service.spec.ts \
        frontend/src/app/shared/layout/header/tenant-switcher/tenant-switcher.component.spec.ts
git commit -m "test(auth): unit tests for hasRole, switchTenant, TenantSwitcher"
```

---

## Task 10: Final verification + manual smoke

**Goal:** Confirm the full stack works end-to-end and nothing regressed.

### Steps

- [ ] **Step 1: Full backend test suite**

```bash
cd backend && mvn test 2>&1 | grep -E "Tests run:.*Failures.*Errors.*Skipped:|BUILD" | tail -3
```

Expected: total tests = previous count (648) + 1 (the new null-tenant test) = 649. All green.

- [ ] **Step 2: Full frontend test suite**

```bash
cd frontend && npm test -- --watch=false --browsers=ChromeHeadless 2>&1 | tail -10
```

Expected: green. New tests : 5 (`hasRole`) + 2 (`switchTenant`) + 5 (`TenantSwitcher`) = +12 specs.

- [ ] **Step 3: Frontend typecheck**

```bash
cd frontend && npx tsc --noEmit
```

Expected: zero errors.

- [ ] **Step 4: Manual smoke — full flow**

a. Start the backend :

```bash
cd backend && mvn spring-boot:run
```

(Wait for Spring to start, then in a new terminal :)

b. Start the frontend :

```bash
cd frontend && npm start
```

c. In a browser, navigate to `http://localhost:4200`. Use the dev seed user `sophie@luxpretty.com / Password1!` (created by `DataInitializer`).

d. After login :
- Header chip should display "L'Atelier de Sophie" (the tenant name) — Sophie has 1 tenant.
- Open the dropdown : 1 entry for the tenant + "Mode client" entry.
- Click "Mode client" : redirects to `/`, chip now shows "Mode client" with a different icon.
- Open dropdown again : Mode client has the check icon, the tenant doesn't.
- Click the tenant : redirects to `/pro/dashboard`, chip back to tenant name.

e. Stop both servers when done.

- [ ] **Step 5: Final commit if anything was tweaked**

```bash
git status
# If any minor tweaks during the smoke (e.g. label too long, missing translation key), commit them:
git add -A && git commit -m "chore: smoke-test polish for tenant switcher"
```

If nothing to commit, skip this step.

---

## Post-implementation summary

After all 10 tasks :

- Backend `UserDto` no longer carries the legacy `role: String` ; consumers MUST use `roles: List<String>` + `activeTenantId` + `availableTenants`.
- `POST /api/me/switch-tenant` accepts `{tenantId: null}` for client mode.
- Frontend `AuthService` exposes `roles()`, `activeTenantId()`, `availableTenants()`, `isClientMode()`, `hasRole(...)`, `switchTenant(...)`.
- `Role.USER` is removed from the frontend enum.
- 17 callsites migrated to `hasRole(...)`.
- `TenantSwitcher` header component shipped with i18n.
- Tests: backend +1, frontend +12.

Out of scope for PR2 (deferred) :

- Commercial role UI (Role.COMMERCIAL is in the enum but no dashboard yet — PR3+).
- Audit log for `/api/me/switch-tenant`.
- Per-tenant context in `tenant-features.service.ts` (today re-loads on user change ; should also re-load on activeTenantId change — backlog).
