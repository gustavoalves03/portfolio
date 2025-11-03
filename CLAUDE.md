# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Fleur de Coquillage** - Beauty salon management application with booking system, care management, and e-commerce features.

**Stack:**
- **Frontend:** Angular 20 (standalone, zoneless, SSR-ready) + Angular Material + Tailwind CSS
- **Backend:** Spring Boot 3.5.4 (Java 21) + Spring Security + JPA/Hibernate
- **Database:** Oracle Free (via Docker)
- **Architecture:** Feature-first monorepo with frontend/backend separation

## Development Commands

### Frontend (from `frontend/`)
```bash
npm ci && npm start              # Dev server at http://localhost:4200
npm run build                    # Production build with SSR
npm test                         # Run Karma/Jasmine tests
npm run serve:ssr:app           # Serve SSR build
```

### Backend (from `backend/`)
```bash
mvn clean spring-boot:run        # Run API at http://localhost:8080
mvn test                         # Run JUnit tests
mvn package                      # Build JAR
```

### Docker Development
```bash
# Development with hot reload
docker compose --profile dev up

# Frontend: http://localhost:4300
# Backend: http://localhost:8080 (with debug port 5006)
# Oracle DB: localhost:1521

# Production build
docker compose --profile prod up
```

**Important:** Set environment variables in `.env`:
```
ORACLE_PASSWORD=yourpassword
APP_USER=youruser
APP_USER_PASSWORD=yourpassword
```

## Architecture Patterns

### Frontend Structure

**Feature-First Organization:**
```
frontend/src/app/
├── features/           # Business domains
│   ├── cares/         # Care management
│   │   ├── modals/    # Feature-specific modals
│   │   ├── models/    # TypeScript interfaces
│   │   ├── services/  # HTTP services
│   │   └── store/     # NgRx SignalStore
│   ├── bookings/      # Appointment scheduling
│   ├── categories/    # Care categories
│   └── users/         # User management
├── shared/
│   ├── uis/           # Reusable UI components (crud-table, etc.)
│   ├── features/      # Shared features (request-status)
│   ├── layout/        # Header, footer, navigation
│   └── models/        # Shared types
├── pages/             # Route components (home, about, etc.)
├── core/              # Singleton services, config, interceptors
└── i18n/              # Transloco i18n setup
```

**State Management with NgRx SignalStore:**
- Each feature has a store (e.g., `cares.store.ts`)
- Pattern: `withState()` → `withRequestStatus()` → `withComputed()` → `withMethods()` → `withHooks()`
- Use `rxMethod()` for async operations with RxJS operators
- Services are injected into store methods, not into components directly
- Store is provided at component level in `providers` array

**Example Store Pattern:**
```typescript
export const CaresStore = signalStore(
  withState<CaresState>({cares: []}),
  withRequestStatus(),  // Adds isPending/isFullfilled signals
  withComputed((store) => ({
    availableCares: computed(() => store.cares().filter(c => c.status === 'ACTIVE'))
  })),
  withMethods((store, service = inject(CaresService)) => ({
    getCares: rxMethod<void>(
      pipe(
        tap(() => patchState(store, setPending())),
        switchMap(() => service.list()),
        tap(cares => patchState(store, {cares}, setFulfilled()))
      )
    )
  })),
  withHooks({onInit(store) { store.getCares(); }})
);
```

**Modal Pattern (Material Dialog):**
- Modals live in `features/{domain}/modals/` subdirectories
- Use standalone components with `MatDialogModule`
- Inject `MatDialogRef` for closing and returning data
- Open via `MatDialog.open()` in parent component
- Return form data via `dialogRef.close(data)`

**Styling Approach:**
- **Angular Material 3** theming with CSS variables (`--mat-sys-*`)
- **Tailwind CSS** utility classes available (configured with PostCSS)
- **SCSS** for component-specific styles
- Prefer Tailwind utilities in templates, SCSS for complex component styles
- Material theme: primary=rose, tertiary=red, typography=Roboto

### Backend Structure

**Feature-First with Layered Architecture:**
```
backend/src/main/java/com/fleurdecoquillage/app/
├── care/
│   ├── domain/        # JPA entities
│   ├── repo/          # Spring Data repositories
│   ├── app/           # Service layer (business logic)
│   └── web/           # Controllers, DTOs, Mappers
│       ├── dto/       # Request/Response DTOs
│       └── mapper/    # Entity ↔ DTO mappers
├── category/
├── bookings/
├── users/
├── config/            # Spring configuration classes
└── common/
    └── error/         # Global exception handling
```

**Naming Conventions:**
- Entities: `Care.java`, `Category.java`
- Repositories: `CareRepository.java` (interface extending `JpaRepository`)
- Services: `CareService.java` (in `app/` layer)
- Controllers: `CareController.java` (in `web/`)
- DTOs: `CreateCareRequest.java`, `CareResponse.java` (in `web/dto/`)
- Mappers: `CareMapper.java` (in `web/mapper/`)

**Spring Boot Configuration:**
- Profile-based config: `application.properties` + `application-docker.properties`
- Use `@Value` or `@ConfigurationProperties` for externalized config
- Database connection configured via environment variables in Docker
- Basic Auth enabled (dev credentials: `dev/dev`)

### HTTP Communication

**Frontend → Backend:**
- Base URL injection: `API_BASE_URL` token in `app.config.ts` (default: `http://localhost:8080`)
- HTTP client with `withFetch()` for SSR compatibility
- Basic Auth interceptor: `basicAuthInterceptor`
- Services in `features/{domain}/services/` make HTTP calls
- Return Observables, consumed by SignalStore's `rxMethod()`

### i18n (Internationalization)

**Transloco Setup:**
- Available languages: `fr` (default), `en`
- Translation files: `frontend/src/assets/i18n/{lang}.json`
- Locale formatting synced with language (dates, numbers, currency)
- Use `TranslocoPipe` in templates: `{{ 'key' | transloco }}`
- Locale data registered: `localeFr`, `localeEn`

### Angular 20 Modern Patterns

**Use these patterns (aligned with AGENTS.md):**
- ✅ Standalone components (no `NgModule`)
- ✅ `inject()` function over constructor injection
- ✅ Control flow: `@if`, `@for`, `@switch`, `@defer` (not `*ngIf`/`*ngFor`)
- ✅ Signals for local state: `signal()`, `computed()`, `effect()`
- ✅ Zoneless change detection (`provideZonelessChangeDetection()`)
- ✅ Functional guards/interceptors: `canActivate: [() => inject(Service).method()]`
- ✅ `provideHttpClient(withFetch())` for SSR
- ✅ `provideClientHydration(withEventReplay())` for SSR

**Avoid these (legacy):**
- ❌ `NgModule`, `BrowserModule`, `HttpClientModule`
- ❌ Class-based interceptors/guards
- ❌ `*ngIf`, `*ngFor` in new code

### Testing

**Frontend:**
- Jasmine/Karma with `*.spec.ts` files colocated with components
- Use functional providers in `TestBed`: `provideHttpClient()`, `provideRouter([])`, `provideNoopAnimations()`
- Run single test: `npm test -- --include='**/my.component.spec.ts'`

**Backend:**
- JUnit 5 + Spring Boot Test
- Test files in `src/test/java/` mirroring package structure
- Name tests: `*Tests.java` (e.g., `CareServiceTests.java`)
- Prefer `@WebMvcTest` for controller slices, `@DataJpaTest` for repositories

## Code Style

**TypeScript/Angular:**
- Prettier enforced: `singleQuote: true`, `printWidth: 100`, Angular HTML parser
- Kebab-case filenames: `create-care.component.ts`
- PascalCase classes: `CreateCareComponent`
- Component file structure: `*.component.ts`, `*.component.html`, `*.component.scss`

**Java:**
- 4-space indentation
- Package-by-feature (not layer-by-layer)
- Use Lombok (`@Data`, `@Builder`, etc.) for boilerplate reduction
- Suffixes: `*Controller`, `*Service`, `*Repository`

**Git Commits:**
- Prefer Conventional Commits: `feat:`, `fix:`, `chore:`, `test:`, `refactor:`
- Legacy prefixes `ok:`, `wip:` exist in history but use Conventional going forward

## Domain Context: Beauty Salon Application

**Brand:** Fleur de Coquillage - Esthetics salon
**Vision:** Subtle, natural beauty inspired by "Venus rising from a shell"
**Tone:** Soft, soothing, educational (never aggressive or overly commercial)

**Key Features:**
- **Bookings:** Appointment scheduling with staff availability, buffer times, automatic reminders
- **Care Management:** Catalog of beauty services (facials, body treatments) with categories, prices, durations
- **Beauty Tracking:** Client history, practitioner notes, before/after photos (secure storage), consent forms
- **E-commerce:** Product catalog, shopping cart, payment (PSP integration), inventory management
- **AI-Assisted:** Appointment suggestions, service/product recommendations, procedure explanations

**Design Aesthetic:**
- Keywords: nacre, rosy shell, sand, sea mist, rounded shapes, soft shadows
- Micro-animations: 150-250ms transitions, never flashy
- Accessibility: WCAG AA (contrast, visible focus, ARIA labels, keyboard navigation)

**Target Users:**
- Clients: Adults seeking facial/body treatments, personalized skincare advice, appointment booking
- Staff: Practitioners managing appointments, client notes, inventory

## Important Notes

- **Environment Setup:** Requires Oracle DB via Docker. Set credentials in `.env` before running `docker compose`.
- **SSR Compatibility:** Use `withFetch()` in HTTP client, avoid browser-only APIs in components (use `isPlatformBrowser` check if needed).
- **Shared Components:** Check `shared/uis/` before creating new UI components (e.g., `crud-table` for tables with search/add).
- **Store Lifecycle:** Stores are provided at component level, initialized via `onInit` hook, destroyed with component.
- **API Authentication:** Basic Auth currently used (dev: `dev/dev`). Will evolve to OAuth2 (Google, Facebook, Apple).
- **Database Schema:** JPA entities auto-create schema. Check `domain/` classes for entity relationships.

## Additional Resources

- **Full Angular 20 guidelines:** See `AGENTS.md` (sections on Control Flow, Signals, SSR, Testing)
- **Docker profiles:** `dev` (hot reload, volumes mounted) vs `prod` (build images, no volumes)
- **Material Theme Variables:** Available at runtime via `--mat-sys-*` CSS variables (e.g., `--mat-sys-primary`)
