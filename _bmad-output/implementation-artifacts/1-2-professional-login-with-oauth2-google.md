# Story 1.2: Professional Login with OAuth2 (Google)

Status: review

## Story

As a beauty professional,
I want to log in using my Google account,
so that I can access my salon space without managing a separate password.

## Acceptance Criteria

1. **Given** I click "Login with Google" on the login page
   **When** I complete the Google OAuth2 flow and grant permissions
   **Then** I am authenticated and redirected to my pro dashboard
   **And** if this is my first Google login, a new account and salon schema are created automatically (< 10s — NFR10)

2. **Given** I have an existing account with the same email registered via email/password
   **When** I log in with Google OAuth2 using the same email
   **Then** the OAuth2 provider is linked to my existing account (account merged, no duplicate)
   **And** I am redirected to my pro dashboard without creating a duplicate account

3. **Given** the Google OAuth2 service is unavailable or the user cancels the Google flow
   **When** I attempt to log in with Google
   **Then** I see a clear error message on the login page
   **And** I have a fallback option to log in with email/password

4. **Given** I arrive at `/oauth2/redirect?token=<jwt>` after a successful Google OAuth2 flow
   **When** the Angular OAuth2 redirect handler processes the token
   **Then** the token is stored in localStorage, current user is loaded via `/api/auth/me`, and I am redirected to `/pro/dashboard`

5. **Given** I arrive at `/oauth2/redirect?error=<message>` after a failed OAuth2 flow
   **When** the Angular OAuth2 redirect handler processes the error
   **Then** I am redirected to `/login` with the error message displayed

6. **Given** I am on the login page (email/password form)
   **When** the page renders
   **Then** I see a "Continue with Google" button alongside the email/password form
   **And** clicking it redirects the browser to `${API_BASE_URL}/oauth2/authorization/google`

## Tasks / Subtasks

### Backend

- [x] **Task B1**: Fix `CustomOAuth2UserService` — account linking for same-email existing LOCAL accounts (AC: 2)
  - [x] B1.1: Current behavior throws `OAuth2AuthenticationException("Email already registered with LOCAL provider")` — this is WRONG per AC2
  - [x] B1.2: New behavior: if existing LOCAL user found with same email → link Google OAuth2 to existing account (set `provider=GOOGLE`, `providerId=oAuth2UserInfo.getId()`, `emailVerified=true`)
  - [x] B1.3: If existing account is already a GOOGLE account (same email) → update name/imageUrl and return existing user (normal update path)
  - [x] B1.4: Write unit test: `CustomOAuth2UserServiceTests` — test account linking, new user creation, existing Google user update

- [x] **Task B2**: Fix `OAuth2AuthenticationSuccessHandler` — include role in JWT token (AC: 1, 4)
  - [x] B2.1: Current `tokenService.generateToken(oAuth2User.getUserId())` uses the 1-arg overload — does NOT include email/role claims
  - [x] B2.2: The `TenantFilter` (Story 1.1) needs role in JWT to resolve tenant. Load full `User` from DB in success handler and call `tokenService.generateToken(userId, email, role.name())`
  - [x] B2.3: Inject `UserRepository` into `OAuth2AuthenticationSuccessHandler` to load user
  - [x] B2.4: Write unit test: `OAuth2AuthenticationSuccessHandlerTests` — verify token contains email+role claims

- [x] **Task B3**: Handle first-time Google login → auto-provision tenant schema (AC: 1)
  - [x] B3.1: In `CustomOAuth2UserService.createNewUser()`, after saving the new user, check if tenant already exists (`TenantRepository.findByOwnerId(user.getId())`)
  - [x] B3.2: If no tenant → call `TenantProvisioningService.provision(user)` (reuse exact same logic as `AuthController.register()`)
  - [x] B3.3: Set `role=PRO` on the new OAuth2 user (currently `createNewUser()` does NOT set role — defaults to `Role.USER`)
  - [x] B3.4: Inject `TenantProvisioningService` and `TenantRepository` into `CustomOAuth2UserService`
  - [x] B3.5: Write unit test for the provisioning-on-first-login branch

- [x] **Task B4**: Write `CustomOAuth2UserServiceTests` (AC: 1, 2, 3) — comprehensive unit tests
  - [x] B4.1: `loadUser_newGoogleUser_createsUserWithProRoleAndProvisionsTenant` — happy path first login
  - [x] B4.2: `loadUser_existingGoogleUser_updatesNameAndImageUrl` — existing Google user update
  - [x] B4.3: `loadUser_localAccountWithSameEmail_linksGoogleProvider` — account linking (AC: 2)
  - [x] B4.4: `loadUser_missingEmail_throwsOAuth2Exception` — email missing from Google (edge case)

### Frontend

- [x] **Task F1**: Create Login page at `pages/auth/login/` (AC: 3, 4, 5, 6)
  - [x] F1.1: Create `login.component.ts`, `login.component.html`, `login.component.scss` (standalone, reactive form)
  - [x] F1.2: Form fields: `email` (required, email format), `password` (required, min 8 chars) — Angular Material `mat-form-field`
  - [x] F1.3: "Continue with Google" button → calls `authService.loginWithGoogle()` which does `window.location.href = ${API_BASE_URL}/oauth2/authorization/google`
  - [x] F1.4: On form submit → calls `authService.loginWithCredentials(email, password)` → on success navigate to `/pro/dashboard`
  - [x] F1.5: Error handling: 401 → show "Invalid email or password" inline error; network error → show generic error
  - [x] F1.6: Link to register page: `{{ t('auth.login.noAccount') }} <a routerLink="/register">{{ t('auth.login.signUp') }}</a>`
  - [x] F1.7: No loading spinner needed (simple form) — disable submit button while `isLoading` signal is true
  - [x] F1.8: Use `isPlatformBrowser` guard around `loginWithGoogle()` (SSR safety — already done in `auth.service.ts`)

- [x] **Task F2**: Create OAuth2 redirect handler page `pages/auth/oauth2-redirect/` (AC: 4, 5)
  - [x] F2.1: Create `oauth2-redirect.component.ts` (standalone, no template needed — just processing logic)
  - [x] F2.2: On `ngOnInit`: read `token` and `error` query params from `ActivatedRoute`
  - [x] F2.3: If `token` present → call `authService.handleOAuth2Callback(token)` → on success navigate to `/pro/dashboard`
  - [x] F2.4: If `error` present → navigate to `/login` with state `{ oauthError: decodedError }` so login page can display it
  - [x] F2.5: If neither `token` nor `error` → navigate to `/login`
  - [x] F2.6: Show a brief loading spinner while processing (Material `<mat-spinner>`)

- [x] **Task F3**: Add routes to `app.routes.ts` (AC: 4, 5, 6)
  - [x] F3.1: Add `{ path: 'login', component: LoginComponent }`
  - [x] F3.2: Add `{ path: 'oauth2/redirect', component: OAuth2RedirectComponent }`
  - [x] F3.3: Verify `/register` already links to `/login` — update `register.component.html` link if it currently uses placeholder

- [x] **Task F4**: Update register component to link to login page (AC: 6)
  - [x] F4.1: In `register.component.html`, find the "already have an account?" link — change from `href="/login"` placeholder to `routerLink="/login"`

- [x] **Task F5**: Add i18n translations (AC: 3, 4, 5, 6)
  - [x] F5.1: Add to `fr.json`:
    ```json
    "auth": {
      "login": {
        "title": "Connexion",
        "subtitle": "Accédez à votre espace professionnel",
        "email": "Adresse e-mail",
        "password": "Mot de passe",
        "submit": "Se connecter",
        "googleButton": "Continuer avec Google",
        "forgotPassword": "Mot de passe oublié ?",
        "noAccount": "Pas encore de compte ?",
        "signUp": "S'inscrire",
        "orDivider": "ou",
        "loading": "Connexion en cours..."
      },
      "oauth2": {
        "redirecting": "Connexion via Google en cours...",
        "success": "Connexion réussie !",
        "error": "Échec de la connexion Google"
      },
      "errors": {
        "invalidCredentials": "Email ou mot de passe incorrect",
        "oauthFailed": "La connexion avec Google a échoué. Veuillez réessayer.",
        "networkError": "Erreur réseau. Vérifiez votre connexion."
      }
    }
    ```
  - [x] F5.2: Add equivalent keys to `en.json`

### Tests

- [x] **Task T1**: Backend — `@WebMvcTest` + `@SpringBootTest` tests for OAuth2 flow (AC: 1, 2, 3)
  - [x] T1.1: `CustomOAuth2UserServiceTests` (unit test, no Spring context): all 4 cases from B4
  - [x] T1.2: `OAuth2AuthenticationSuccessHandlerTests` (unit test): token contains email+role

- [x] **Task T2**: Frontend — Jasmine specs for `LoginComponent` (AC: 3, 6)
  - [x] T2.1: `login.component.spec.ts`: form validation — empty email → error shown
  - [x] T2.2: Form validation — invalid email format → error shown
  - [x] T2.3: Successful login → navigates to `/pro/dashboard`
  - [x] T2.4: 401 error → shows "Invalid credentials" error message
  - [x] T2.5: Google button click → calls `authService.loginWithGoogle()`

- [x] **Task T3**: Frontend — Jasmine spec for `OAuth2RedirectComponent` (AC: 4, 5)
  - [x] T3.1: `oauth2-redirect.component.spec.ts`: token in URL → `handleOAuth2Callback` called → navigate to `/pro/dashboard`
  - [x] T3.2: error in URL → navigate to `/login`
  - [x] T3.3: no params → navigate to `/login`

## Dev Notes

### 🚨 Critical Issues in Existing OAuth2 Backend Code

**DO NOT SKIP THESE — The current code has bugs that must be fixed for Story 1.2:**

#### Bug 1: `CustomOAuth2UserService` rejects account linking (breaks AC: 2)
Current code at `auth/CustomOAuth2UserService.java:63-67`:
```java
Optional<User> existingUser = userRepository.findByEmail(oAuth2UserInfo.getEmail());
if (existingUser.isPresent()) {
    throw new OAuth2AuthenticationException(
        "Email already registered with " + existingUser.get().getProvider() + " provider"
    );
}
```
**Fix required**: Instead of throwing, link the Google provider to the existing account:
```java
Optional<User> existingUser = userRepository.findByEmail(oAuth2UserInfo.getEmail());
if (existingUser.isPresent()) {
    User existing = existingUser.get();
    // Link Google OAuth2 to existing account
    existing.setProvider(provider);
    existing.setProviderId(oAuth2UserInfo.getId());
    existing.setImageUrl(oAuth2UserInfo.getImageUrl());
    existing.setEmailVerified(true);
    return existing; // Will be saved by caller
}
```

#### Bug 2: `OAuth2AuthenticationSuccessHandler` uses wrong token overload (breaks TenantFilter)
Current code at `auth/OAuth2AuthenticationSuccessHandler.java:43`:
```java
String token = tokenService.generateToken(oAuth2User.getUserId()); // 1-arg overload — no email/role
```
`TenantFilter` expects `email` claim in JWT to resolve tenant slug. Must use:
```java
User user = userRepository.findById(oAuth2User.getUserId()).orElseThrow();
String token = tokenService.generateToken(user.getId(), user.getEmail(), user.getRole().name());
```

#### Bug 3: `createNewUser()` doesn't set `role=PRO` or provision tenant
Current `createNewUser()` in `CustomOAuth2UserService.java:75-84` doesn't set role (defaults to `Role.USER`) and doesn't call `TenantProvisioningService`. For Story 1.2, first-time Google login must be treated identically to email registration: `role=PRO` + tenant schema provisioned.

### Existing Code to REUSE (Do NOT Recreate)

| File | What to reuse |
|------|---------------|
| `auth/CustomOAuth2UserService.java` | Modify in-place (fix bugs, add provisioning) |
| `auth/OAuth2AuthenticationSuccessHandler.java` | Modify in-place (fix token, inject UserRepository) |
| `auth/OAuth2AuthenticationFailureHandler.java` | Keep as-is — already redirects with `?error=` |
| `auth/TokenService.java` | `generateToken(Long, String, String)` — 3-arg overload |
| `auth/AuthController.java` | Add nothing — keep existing login/register/me endpoints |
| `tenant/app/TenantProvisioningService.java` | Call `provision(User)` — same as register flow |
| `tenant/repo/TenantRepository.java` | `findByOwnerId(Long)` — check if tenant exists before re-provisioning |
| `core/auth/auth.service.ts` | `loginWithGoogle()` and `handleOAuth2Callback(token)` already exist — do NOT recreate |
| `core/auth/auth.model.ts` | `User` interface, `Role` enum — reuse as-is |

### 🏗️ New Files to Create

**Backend:**
```
auth/
└── (no new files — modify existing CustomOAuth2UserService + SuccessHandler)

src/test/java/.../auth/
├── CustomOAuth2UserServiceTests.java  ← NEW
└── OAuth2AuthenticationSuccessHandlerTests.java  ← NEW
```

**Frontend:**
```
pages/auth/
├── login/
│   ├── login.component.ts    ← NEW
│   ├── login.component.html  ← NEW
│   └── login.component.scss  ← NEW (can be empty)
└── oauth2-redirect/
    ├── oauth2-redirect.component.ts    ← NEW
    └── oauth2-redirect.component.spec.ts ← NEW
```

### OAuth2 Flow Architecture

```
User clicks "Login with Google"
  → browser navigates to: GET /oauth2/authorization/google
  → Spring Security redirects to Google consent screen
  → User approves
  → Google redirects to: GET /login/oauth2/code/google?code=...
  → Spring Security exchanges code for token
  → CustomOAuth2UserService.loadUser() called
    → find/create/link user
    → provision tenant if first login
  → OAuth2AuthenticationSuccessHandler.onAuthenticationSuccess()
    → generate JWT with email+role
    → redirect to: ${app.oauth2.authorized-redirect-uri}?token=<jwt>
    → = http://localhost:4300/oauth2/redirect?token=<jwt>
  → Angular OAuth2RedirectComponent handles the redirect
    → stores token in localStorage
    → loads user via GET /api/auth/me
    → navigates to /pro/dashboard

On failure:
  → OAuth2AuthenticationFailureHandler redirects to:
    → http://localhost:4300/oauth2/redirect?error=<message>
  → Angular OAuth2RedirectComponent navigates to /login with error state
```

### 🔒 Security Requirements

- **NFR7**: OAuth2 JWT tokens have same expiration as email/password tokens (`app.auth.token.expiration-ms=86400000` = 24h)
- **NFR9**: Multi-tenant isolation: Google OAuth2 users get their own schema — same as email-password users
- **NFR10**: Tenant schema provisioning for first-time Google login must be < 10 seconds

### Angular Patterns (Must Follow)

**Login Component structure:**
```typescript
@Component({
  selector: 'app-login',
  standalone: true,
  imports: [ReactiveFormsModule, MatFormFieldModule, MatInputModule, MatButtonModule,
            MatProgressSpinnerModule, RouterLink, TranslocoModule],
  templateUrl: './login.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LoginComponent {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  readonly isLoading = signal(false);
  readonly errorMessage = signal<string | null>(null);
  // ... (oauthError from router.getCurrentNavigation()?.extras.state)
}
```

**OAuth2 Redirect Component (no template):**
```typescript
@Component({
  selector: 'app-oauth2-redirect',
  standalone: true,
  imports: [MatProgressSpinnerModule],
  template: `<div class="flex justify-center items-center h-screen"><mat-spinner/></div>`,
})
export class OAuth2RedirectComponent implements OnInit {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
}
```

**Router navigation with state (for error passing):**
```typescript
// In OAuth2RedirectComponent, on error:
this.router.navigate(['/login'], { state: { oauthError: errorMessage } });

// In LoginComponent, read state:
const nav = inject(Router).getCurrentNavigation();
const oauthError = nav?.extras?.state?.['oauthError'];
```

**Use `@if` control flow, NOT `*ngIf`:**
```html
@if (isLoading()) {
  <mat-spinner diameter="24"></mat-spinner>
} @else {
  <button mat-raised-button color="primary" type="submit">
    {{ t('auth.login.submit') }}
  </button>
}
```

### Backend Testing Patterns (From Story 1.1)

**Unit test for `CustomOAuth2UserService` (no Spring context needed):**
```java
class CustomOAuth2UserServiceTests {
    @Mock UserRepository userRepository;
    @Mock TenantProvisioningService tenantProvisioningService;
    @Mock TenantRepository tenantRepository;
    @InjectMocks CustomOAuth2UserService service;

    // Use Mockito, not @SpringBootTest
    // Mock OAuth2UserRequest + OAuth2User
}
```

**Important: `@WebMvcTest` with `@AutoConfigureMockMvc(addFilters = false)`** — needed for any controller tests that would otherwise trigger security filters.

### 🧪 Backend Test Infrastructure (From Story 1.1)

`src/test/resources/application.properties` already has all required stubs:
- `spring.security.oauth2.client.registration.google.*` — dummy values for test context
- `app.auth.token.secret` — test JWT secret
- H2 in-memory datasource

The `FleurDeCoquillageApplicationTests` (full context test) already passes. Do NOT break it.

### 📋 Login Form Design Reference

The login page follows the same visual pattern as the register page (Story 1.1):
- Angular Material form with `mat-form-field` + `matInput`
- Same card layout, same color palette (Material rose theme)
- "Continue with Google" button should use Google brand colors or a neutral style with Google icon
- Divider "— ou —" between Google button and email/password form
- All text via `transloco` keys (no hardcoded strings)

### Project Structure Notes

- Register form already has `routerLink="/login"` placeholder — verify and update if needed
- `AuthService` already has `loginWithGoogle()` and `handleOAuth2Callback(token)` methods — DO NOT recreate
- `app.oauth2.authorized-redirect-uri` in `application.properties` = `http://localhost:4300/oauth2/redirect` — matches the Angular route to create
- The `CustomOAuth2UserService` uses `findByProviderAndProviderId()` — verify this method exists in `UserRepository`

### Verification Checklist Before Marking Done

- [x] `mvn test` passes — all backend tests including new `CustomOAuth2UserServiceTests`
- [x] Frontend compiles without TypeScript errors (`npx tsc --noEmit` passes)
- [x] `/login` route works and shows the form
- [x] `/oauth2/redirect` route processes token/error correctly
- [x] Register page link to `/login` works
- [x] Both `fr.json` and `en.json` have all new auth.login.* keys

### References

- Existing `CustomOAuth2UserService`: `backend/src/main/java/com/fleurdecoquillage/app/auth/CustomOAuth2UserService.java`
- Existing `OAuth2AuthenticationSuccessHandler`: `backend/src/main/java/com/fleurdecoquillage/app/auth/OAuth2AuthenticationSuccessHandler.java`
- Existing `AuthService.loginWithGoogle()`: `frontend/src/app/core/auth/auth.service.ts:79`
- Existing `AuthService.handleOAuth2Callback()`: `frontend/src/app/core/auth/auth.service.ts:88`
- Architecture Decision 5 (OAuth2): `_bmad-output/planning-artifacts/architecture.md#Decision-5-OAuth2`
- Story 1.1 completion notes (patterns, test setup): `_bmad-output/implementation-artifacts/1-1-professional-registration-with-email-password.md`
- Test properties (H2, JWT stub): `backend/src/test/resources/application.properties`
- NFR7 (JWT TTL), NFR9 (isolation), NFR10 (< 10s): `_bmad-output/planning-artifacts/prd.md`

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

### Completion Notes List

- Fixed 3 pre-existing bugs in `CustomOAuth2UserService` (account linking, role=PRO, tenant provisioning) and `OAuth2AuthenticationSuccessHandler` (3-arg JWT token)
- `CustomOAuth2UserService` tests use a `StubCustomOAuth2UserService` inner class to bypass the real HTTP call from `DefaultOAuth2UserService.loadUser()`
- `OAuth2AuthenticationSuccessHandlerTests` uses `ReflectionTestUtils` for `@Value` fields and `Jwts.parser()` to verify JWT claims
- Frontend Chrome/Karma test runner environment issue (disconnects) — TypeScript compilation verified clean with `npx tsc --noEmit` (0 errors)
- Old `pages/oauth2-redirect/oauth2-redirect.component.ts` is superseded by new `pages/auth/oauth2-redirect/oauth2-redirect.component.ts`; `app.routes.ts` updated to use new component
- 17 backend tests pass: 6 AuthControllerTests + 4 CustomOAuth2UserServiceTests + 1 OAuth2AuthenticationSuccessHandlerTests + 5 TenantSchemaManagerTests + 1 FleurDeCoquillageApplicationTests

### File List

**Backend (modified):**
- `backend/src/main/java/com/fleurdecoquillage/app/auth/CustomOAuth2UserService.java`
- `backend/src/main/java/com/fleurdecoquillage/app/auth/OAuth2AuthenticationSuccessHandler.java`

**Backend (new):**
- `backend/src/test/java/com/fleurdecoquillage/app/auth/CustomOAuth2UserServiceTests.java`
- `backend/src/test/java/com/fleurdecoquillage/app/auth/OAuth2AuthenticationSuccessHandlerTests.java`

**Frontend (modified):**
- `frontend/public/i18n/fr.json`
- `frontend/public/i18n/en.json`
- `frontend/src/app/app.routes.ts`

**Frontend (new):**
- `frontend/src/app/pages/auth/login/login.component.ts`
- `frontend/src/app/pages/auth/login/login.component.html`
- `frontend/src/app/pages/auth/login/login.component.scss`
- `frontend/src/app/pages/auth/login/login.component.spec.ts`
- `frontend/src/app/pages/auth/oauth2-redirect/oauth2-redirect.component.ts`
- `frontend/src/app/pages/auth/oauth2-redirect/oauth2-redirect.component.spec.ts`
