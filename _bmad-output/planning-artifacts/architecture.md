---
stepsCompleted: [1, 2, 3, 4, 5, 6, 7, 8]
lastStep: 8
status: 'complete'
completedAt: '2026-02-23'
inputDocuments:
  - path: '_bmad-output/planning-artifacts/prd.md'
    type: 'prd'
    description: 'PRD complet Pretty Face - 56 exigences fonctionnelles, multi-tenant, SaaS B2B'
  - path: '_bmad-output/brainstorming/brainstorming-session-2026-02-20.md'
    type: 'brainstorming'
    description: 'Session complète avec 42 idées, 5 thèmes, roadmap 18 mois'
workflowType: 'architecture'
project_name: 'Pretty Face'
user_name: 'Gustavo.alves'
date: '2026-02-23'
classification:
  projectType: 'SaaS B2B + Web App'
  domain: 'Services / Beauté'
  complexity: 'low-medium'
  projectContext: 'brownfield'
  multiTenant: true
  techStack: 'Angular 20 + Spring Boot 3.5 + Oracle'
---

# Architecture Decision Document - Pretty Face

_This document builds collaboratively through step-by-step discovery. Sections are appended as we work through each architectural decision together._

## Project Context Analysis

### Requirements Overview

**Functional Requirements:**
56 exigences fonctionnelles couvrant l'ensemble du parcours utilisateur :
- Authentification multi-provider (OAuth2 Google/Facebook/Apple + email)
- Configuration vitrine pro (prestations, catégories, horaires)
- Réservation en ligne 24/7 avec gestion créneaux
- Dashboard statistiques (CA, réservations, progression)
- Multi-tenant avec isolation par salon

**Non-Functional Requirements:**
- Disponibilité haute (heures ouvrées critiques)
- Performance fluide sur mobile et desktop
- Sécurité données (RGPD, isolation tenant)
- Scalabilité pour 5-10 salons initialement, puis croissance

**Scale & Complexity:**
- Primary domain: SaaS B2B Full-Stack (Web + API)
- Complexity level: Medium
- Estimated architectural components: ~15 bounded contexts

### Technical Constraints & Dependencies

| Contrainte | Détail |
|------------|--------|
| Stack imposée | Angular 20 + Spring Boot 3.5 + Oracle |
| Code existant | FleurDeCoquillage avec prestations, catégories, booking partiel, OAuth2 Google |
| Multi-tenant | Schéma Oracle par tenant (décision PRD) |
| Développeur solo | MVP lean, éviter sur-ingénierie |
| Intégrations | Google/Facebook/Apple OAuth, Email service, Google Calendar/iCal |

### Cross-Cutting Concerns Identified

1. **Authentication & Authorization** — OAuth2 multi-provider, JWT, RBAC (Admin/Pro/Client)
2. **Multi-tenancy** — Tenant resolution, schema routing, data isolation
3. **Notifications** — Email transactionnel (confirmation, rappels, annulations)
4. **Internationalization** — FR/EN en place (Transloco)
5. **Error Handling** — Global exception handler, error responses cohérentes
6. **Audit & Logging** — Traçabilité actions critiques (réservations, annulations)
7. **GDPR Compliance** — Consentement, droit à l'effacement, export données

### Existing Codebase State

| Domaine | État | Notes |
|---------|------|-------|
| Auth OAuth2 | ✅ Google fonctionne | Facebook, Apple à ajouter |
| Gestion Prestations | ✅ CRUD complet + images | Prêt |
| Catégories | ✅ CRUD complet | Prêt |
| Réservations | ⏳ Partiel | Calendar, times en place, manque notifications |
| Multi-tenant | ❌ Non implémenté | Architecture majeure à définir |
| Dashboard Stats | ❌ Non implémenté | Post-MVP possible |
| Notifications Email | ❌ Non implémenté | Critique pour MVP |
| Sync Calendrier | ❌ Non implémenté | Google Calendar, iCal |

## Starter Template Evaluation

### Primary Technology Domain

Full-stack SaaS B2B (Angular + Spring Boot + Oracle) — **Brownfield project**

### Evaluation Outcome

**Decision: Continue with Existing Codebase**

The existing FleurDeCoquillage codebase provides a solid, well-structured foundation that already implements modern best practices. No starter template migration is needed.

### Existing Architecture Assessment

**Frontend (Angular 20):**
- ✅ Standalone components (no NgModules)
- ✅ Zoneless change detection
- ✅ SSR-ready with hydration
- ✅ NgRx SignalStore for state management
- ✅ Feature-first organization
- ✅ Modern control flow (@if, @for)
- ✅ Angular Material 3 + Tailwind CSS
- ✅ Transloco i18n (FR/EN)

**Backend (Spring Boot 3.5.4):**
- ✅ Java 21 with modern features
- ✅ Layered architecture (web → app → domain → repo)
- ✅ Feature-first package organization
- ✅ Spring Security with OAuth2
- ✅ JWT authentication
- ✅ JPA/Hibernate with Oracle
- ✅ DTO/Mapper pattern

**DevOps:**
- ✅ Docker Compose (dev + prod profiles)
- ✅ SSR API_BASE_URL configuration
- ✅ Environment-based configuration

### Evolution Strategy (No Starter Migration)

Instead of migrating to a new starter, the project will evolve incrementally:

| Feature | Approach |
|---------|----------|
| Multi-tenant | Add tenant resolution + Oracle schema routing |
| OAuth2 providers | Extend existing SecurityConfig |
| Email service | Add Spring Mail module |
| Calendar sync | Add Google Calendar API client |
| Stats dashboard | New feature module in existing structure |

### Rationale

1. **No code waste** — Existing code is production-quality
2. **Consistent patterns** — Team already familiar with codebase
3. **Lower risk** — No migration bugs or compatibility issues
4. **Faster delivery** — Focus on features, not infrastructure

## Core Architectural Decisions

### Decision Priority Analysis

**Critical Decisions (Block Implementation):**
- Multi-tenant architecture (schema-per-tenant)
- Tenant resolution strategy (path prefix)
- Authentication extension (OAuth2 multi-provider)

**Important Decisions (Shape Architecture):**
- Email service integration
- Calendar synchronization approach
- Caching strategy
- Monitoring & error tracking

**Deferred Decisions (Post-MVP):**
- Google Calendar API bidirectional sync
- Redis distributed cache
- Admin dashboard platform
- Stripe payment integration

### Data Architecture

#### Decision 1: Multi-Tenant Strategy — Schema-per-Tenant

| Aspect | Detail |
|--------|--------|
| **Choice** | One Oracle schema per salon (tenant) |
| **Isolation** | Strong — data physically separated |
| **Provisioning** | Automatic script creates schema on signup |
| **Routing** | Backend resolves tenant and routes to correct schema |
| **Migrations** | Flyway/Liquibase applied to all schemas |
| **Rationale** | Strong isolation, individual backup, future scalability |

**Technical Implementation:**
- `TenantContext` thread-local holder
- `AbstractRoutingDataSource` for dynamic schema routing
- Schema template for automatic provisioning
- Multi-schema migration scripts

#### Decision 2: Tenant Resolution — Path Prefix

| Aspect | Detail |
|--------|--------|
| **Choice** | Path prefix (`prettyface.com/salon/{slug}`) |
| **Public storefront** | `prettyface.com/salon/sophie` |
| **APIs** | `/api/salon/{slug}/...` or tenant resolved via JWT |
| **Advantage** | Simple, no DNS/wildcard certificates needed |
| **Evolution** | Migration to subdomains possible later |
| **Rationale** | MVP-friendly, works immediately |

**Technical Implementation:**
- Angular route: `/salon/:slug` for storefronts
- Backend: `@PathVariable slug` or resolution via JWT claim
- `tenants` table with unique `slug` (URL-friendly)

### Authentication & Security

#### Decision 5: OAuth2 Strategy — Spring Security Native

| Aspect | Detail |
|--------|--------|
| **Choice** | Extend existing Spring Security OAuth2 config |
| **Google** | ✅ Already functional |
| **Facebook** | Add registration in `application.properties` |
| **Apple** | Add registration (Sign in with Apple) |
| **Fallback** | Email/password for users without social accounts |
| **Rationale** | Build on existing architecture, full control |

**Technical Implementation:**
- Add `facebook` and `apple` in `spring.security.oauth2.client.registration`
- `CustomOAuth2UserService` already handles user creation/update
- Apple requires Apple Developer account ($99/year)
- Existing JWT tokens work for all providers

### API & Communication Patterns

#### Decision 3: Email Service — Hostinger SMTP

| Aspect | Detail |
|--------|--------|
| **Choice** | Hostinger SMTP (existing hosting) |
| **Cost** | Included in hosting plan |
| **Integration** | Spring Mail with SMTP config |
| **Templates** | Thymeleaf or FreeMarker for HTML emails |
| **Rationale** | No additional cost, use existing infrastructure |

**Technical Implementation:**
- Add `spring-boot-starter-mail`
- SMTP configuration in `application.properties`
- `EmailService` for transactional templates
- Templates: booking confirmation, J-1 reminder, cancellation

#### Decision 4: Calendar Sync — iCal Download + URL Subscription

| Aspect | Detail |
|--------|--------|
| **Choice** | iCal file export + subscription URL |
| **Download** | "Add to calendar" button → .ics file |
| **Subscription** | URL `/salon/{slug}/calendar.ics` auto-refresh |
| **Compatibility** | Google Calendar, Apple Calendar, Outlook |
| **Post-MVP** | Google Calendar API bidirectional sync |
| **Rationale** | Maximum coverage, reasonable complexity |

**Technical Implementation:**
- `ical4j` library for iCal generation
- Public endpoint `/api/salon/{slug}/calendar.ics`
- Include: confirmed appointments, blocked slots
- Cache with invalidation on booking changes

### Infrastructure & Deployment

#### Decision 6: Caching Strategy — Caffeine + HTTP Headers

| Aspect | Detail |
|--------|--------|
| **Choice** | Local memory cache + HTTP caching |
| **Caffeine** | Storefronts, services, categories (TTL 5-15 min) |
| **HTTP Cache** | `Cache-Control` on public endpoints |
| **Invalidation** | Manual eviction on modifications |
| **Post-MVP** | Redis if multi-instance needed |
| **Rationale** | Performance without infrastructure overhead |

**Technical Implementation:**
- Add `spring-boot-starter-cache` + `caffeine`
- `@Cacheable` on public storefront services
- Headers `Cache-Control: public, max-age=300` on `/api/salon/{slug}/...`
- `@CacheEvict` on write operations

#### Decision 7: Monitoring — Actuator + Sentry

| Aspect | Detail |
|--------|--------|
| **Choice** | Spring Boot Actuator + Sentry |
| **Health checks** | `/actuator/health` for uptime monitoring |
| **Error tracking** | Sentry captures exceptions with stack trace |
| **Frontend** | Sentry Angular SDK for client errors |
| **Cost** | Free up to 5K events/month |
| **Rationale** | Error visibility from day 1 |

**Technical Implementation:**
- Add `sentry-spring-boot-starter`
- Sentry DSN config in `application.properties`
- `@sentry/angular` for frontend
- Actuator secured (admin only or IP whitelist)

### Inherited Decisions (Existing Codebase)

| Category | Decision | Source |
|----------|----------|--------|
| Frontend Framework | Angular 20 standalone | Existing code |
| State Management | NgRx SignalStore | Existing code |
| UI Library | Angular Material 3 + Tailwind | Existing code |
| Backend Framework | Spring Boot 3.5.4 | Existing code |
| Database | Oracle Free | Existing code |
| Auth Base | OAuth2 + JWT | Existing code |
| i18n | Transloco FR/EN | Existing code |
| Container | Docker Compose | Existing code |

### Decision Impact Analysis

**Implementation Sequence:**
1. Multi-tenant infrastructure (schema routing, provisioning)
2. Tenant resolution (path prefix, JWT claim)
3. Email service integration (Spring Mail + templates)
4. OAuth2 extension (Facebook, Apple)
5. Calendar sync (iCal export + URL)
6. Caching layer (Caffeine + HTTP headers)
7. Monitoring setup (Actuator + Sentry)

**Cross-Component Dependencies:**
- Multi-tenant must be implemented before any tenant-specific features
- Email service needed for booking confirmations (blocks booking completion)
- OAuth2 extension independent, can be parallelized
- Caching can be added incrementally after core features work

## Implementation Patterns & Consistency Rules

### Pattern Categories Defined

**8 pattern categories** established to ensure AI agents and developers write consistent, compatible code.

### Naming Patterns

#### Database Naming Conventions

| Element | Convention | Example |
|---------|------------|---------|
| Tables | `UPPER_SNAKE_CASE` | `CARE_BOOKINGS`, `USERS` |
| Columns | `snake_case` | `appointment_date`, `created_at` |
| Foreign keys | `{entity}_id` | `user_id`, `care_id` |
| Index | `idx_{table}_{column}` | `idx_users_email` |
| Sequences | `{table}_seq` | `users_seq` |

#### API Naming Conventions

| Element | Convention | Example |
|---------|------------|---------|
| Base path | `/api/` | `/api/care` |
| Resources | Plural, kebab-case | `/api/care-bookings` |
| Path params | `{id}` | `/api/care/{id}` |
| Query params | camelCase | `?userId=123&startDate=...` |
| Custom actions | POST + verb suffix | `POST /api/bookings/{id}/cancel` |
| Multi-tenant public | `/api/salon/{slug}/...` | `/api/salon/sophie/care` |
| Multi-tenant auth | Tenant via JWT claim | `/api/bookings` |

#### Code Naming Conventions

| Element | Convention | Example |
|---------|------------|---------|
| Java classes | PascalCase | `CareBookingService` |
| Java methods | camelCase | `findByUserId()` |
| Java variables | camelCase | `appointmentDate` |
| Java constants | UPPER_SNAKE | `MAX_BOOKING_DAYS` |
| Angular components | PascalCase | `BookingCalendarComponent` |
| Angular files | kebab-case | `booking-calendar.component.ts` |
| Angular services | PascalCase + suffix | `CaresService` |
| Angular stores | PascalCase + suffix | `CaresStore` |
| TypeScript interfaces | PascalCase (no I prefix) | `CareBooking` |

### Structure Patterns

#### Frontend Feature Structure

```
features/{domain}/
├── {domain}.component.ts          # Main component
├── {domain}.component.html
├── {domain}.component.scss
├── services/
│   └── {domain}.service.ts        # HTTP calls
├── store/
│   └── {domain}.store.ts          # SignalStore
├── models/
│   └── {domain}.model.ts          # Interfaces
└── modals/
    └── {action}-{domain}/         # ex: create-care/
        ├── {action}-{domain}.component.ts
        ├── {action}-{domain}.component.html
        └── {action}-{domain}.component.scss
```

#### Backend Feature Structure

```
{domain}/
├── domain/
│   └── {Entity}.java              # JPA Entity (@Entity, Lombok)
├── repo/
│   └── {Entity}Repository.java    # JpaRepository + custom queries
├── app/
│   └── {Entity}Service.java       # @Service, @Transactional
└── web/
    ├── {Entity}Controller.java    # @RestController
    ├── dto/
    │   ├── Create{Entity}Request.java
    │   ├── Update{Entity}Request.java
    │   └── {Entity}Response.java
    └── mapper/
        └── {Entity}Mapper.java    # Entity ↔ DTO conversion
```

### Format Patterns

#### API Response Formats

| Case | Format | Example |
|------|--------|---------|
| GET single | Direct object | `{ "id": 1, "name": "Soin visage" }` |
| GET list | Spring `Page<T>` | `{ "content": [...], "totalPages": 5, ... }` |
| POST/PUT | Created/updated object | `{ "id": 1, "name": "..." }` |
| DELETE | 204 No Content | (no body) |
| Errors | `@ControllerAdvice` | `{ "status": 400, "message": "...", "errors": [...] }` |

### Communication Patterns

#### SignalStore Standard Pattern

```typescript
export const {Domain}Store = signalStore(
  withState<{Domain}State>({ items: [], selectedItem: null }),
  withRequestStatus(),                    // isPending, isError, error
  withComputed((store) => ({...})),       // Derived signals
  withMethods((store, service = inject({Domain}Service)) => ({
    load: rxMethod<void>(pipe(
      tap(() => patchState(store, setPending())),
      switchMap(() => service.list()),
      tap(items => patchState(store, { items }, setFulfilled()))
    )),
  })),
  withHooks({ onInit(store) { store.load(); } })
);
```

### Process Patterns

#### Error Handling

**Backend:**
| Element | Approach |
|---------|----------|
| HTTP Status | 400, 401, 403, 404, 500 as appropriate |
| Error body | `{ "status": 400, "message": "...", "timestamp": "..." }` |
| Validation | `{ "errors": [{ "field": "email", "message": "Invalid format" }] }` |
| Exceptions | `@ControllerAdvice` + custom exceptions |

**Frontend:**
| Element | Approach |
|---------|----------|
| Store | `withRequestStatus()` + `catchError` |
| UI | Material Toast/Snackbar for user errors |
| Logging | Sentry for technical errors |

### Enforcement Guidelines

**All AI Agents MUST:**
- ✅ Follow feature-first structure (frontend AND backend)
- ✅ Use SignalStore with `withRequestStatus()` for all async state
- ✅ Create separate DTOs (Request/Response) — never expose entities
- ✅ Use `@Transactional` on service methods that modify data
- ✅ Add FR + EN translations for all UI text
- ✅ Use modern control flow (`@if`, `@for`)
- ✅ Use `inject()` function, not constructor injection

**All AI Agents MUST NOT:**
- ❌ Expose JPA entities directly in controllers
- ❌ Use `*ngIf` / `*ngFor` (legacy syntax)
- ❌ Create NgModules
- ❌ Hardcode text in templates (use Transloco)
- ❌ Skip translations when adding UI elements

### Pattern Examples

**Good Example — Creating a new feature:**
```
backend: bookings/domain/Booking.java → repo/ → app/ → web/dto/
frontend: features/bookings/ with service, store, models, modals
```

**Anti-Pattern:**
```
❌ Returning Booking entity from controller
❌ Using *ngIf in new component
❌ Hardcoding "Réserver" instead of {{ 'booking.reserve' | transloco }}
```

## Project Structure & Boundaries

### Application Sections Overview

Pretty Face is organized into 4 distinct sections:

```
┌─────────────────────────────────────────────────────────────────────┐
│                        PRETTY FACE                                   │
├─────────────────────────────────────────────────────────────────────┤
│  1️⃣ LANDING / MARKETING                                             │
│     • Homepage presenting Pretty Face SaaS                          │
│     • "Create your professional storefront"                         │
│     • Pro registration / Login                                      │
├─────────────────────────────────────────────────────────────────────┤
│  2️⃣ DISCOVERY (Public - Clients)                                    │
│     • Category menu: Coiffure | Esthétique | Ongles | ...           │
│     • Salon listings by category                                    │
│     • Global feed (posts from all salons)                           │
│     • Search / Geographic filters                                   │
├─────────────────────────────────────────────────────────────────────┤
│  3️⃣ SALON PAGE (Public - Individual storefront)                     │
│     • /salon/{slug}                                                 │
│     • Salon info (name, logo, description)                          │
│     • Services list                                                 │
│     • Salon's own feed (its posts)                                  │
│     • Booking button                                                │
├─────────────────────────────────────────────────────────────────────┤
│  4️⃣ PRO SPACE (Authenticated - Salon owner)                         │
│     • Dashboard (stats, today's bookings)                           │
│     • Manage services / categories                                  │
│     • Manage schedule / availability                                │
│     • Post to feed                                                  │
│     • Salon settings                                                │
└─────────────────────────────────────────────────────────────────────┘
```

### Complete Backend Structure

```
backend/src/main/java/com/prettyface/app/
│
├── tenant/                          # Salon management (tenants)
│   ├── domain/
│   │   └── Tenant.java
│   ├── repo/
│   │   └── TenantRepository.java
│   ├── app/
│   │   ├── TenantService.java
│   │   └── TenantProvisioningService.java
│   └── web/
│       ├── TenantController.java
│       ├── dto/
│       │   ├── CreateTenantRequest.java
│       │   ├── UpdateTenantRequest.java
│       │   └── TenantResponse.java
│       └── mapper/
│           └── TenantMapper.java
│
├── post/                            # Feed posts
│   ├── domain/
│   │   └── Post.java
│   ├── repo/
│   │   └── PostRepository.java
│   ├── app/
│   │   └── PostService.java
│   └── web/
│       ├── PostController.java
│       ├── dto/
│       │   ├── CreatePostRequest.java
│       │   └── PostResponse.java
│       └── mapper/
│           └── PostMapper.java
│
├── category/                        # Business categories (Coiffure, Esthétique...)
│   ├── domain/
│   │   └── BusinessCategory.java
│   ├── repo/
│   ├── app/
│   └── web/
│
├── care/                            # Services/prestations (existing)
│   ├── domain/
│   │   ├── Care.java
│   │   └── CareImage.java
│   ├── repo/
│   ├── app/
│   └── web/
│
├── booking/                         # Bookings (existing)
│   ├── domain/
│   │   └── Booking.java
│   ├── repo/
│   ├── app/
│   └── web/
│
├── user/                            # Users (existing)
│   ├── domain/
│   │   ├── User.java
│   │   ├── Role.java
│   │   └── AuthProvider.java
│   ├── repo/
│   ├── app/
│   └── web/
│
├── auth/                            # Authentication (existing)
│   ├── OAuth2SuccessHandler.java
│   ├── CustomOAuth2UserService.java
│   ├── JwtAuthenticationFilter.java
│   └── TokenService.java
│
├── notification/                    # Emails
│   ├── app/
│   │   └── EmailService.java
│   └── templates/
│       ├── booking-confirmation.html
│       ├── booking-reminder.html
│       └── booking-cancellation.html
│
├── calendar/                        # iCal export
│   ├── app/
│   │   └── ICalService.java
│   └── web/
│       └── CalendarController.java
│
├── multitenancy/                    # Multi-tenant infrastructure
│   ├── TenantContext.java
│   ├── TenantRoutingDataSource.java
│   ├── TenantFilter.java
│   └── TenantSchemaManager.java
│
├── config/                          # Configuration (existing)
│   ├── SecurityConfig.java
│   ├── CacheConfig.java
│   └── DataSourceConfig.java
│
└── common/                          # Utilities (existing)
    ├── error/
    │   └── GlobalExceptionHandler.java
    └── storage/
        └── FileStorageService.java
```

### Complete Frontend Structure

```
frontend/src/app/
│
├── pages/                           # Route components
│   │
│   ├── landing/                     # 1️⃣ MARKETING PAGE
│   │   ├── landing.component.ts
│   │   ├── landing.component.html
│   │   └── landing.component.scss
│   │
│   ├── discover/                    # 2️⃣ DISCOVERY
│   │   ├── discover.component.ts
│   │   ├── discover.component.html
│   │   ├── category/
│   │   │   ├── category.component.ts
│   │   │   └── category.component.html
│   │   └── feed/
│   │       ├── feed.component.ts
│   │       └── feed.component.html
│   │
│   ├── salon/                       # 3️⃣ SALON STOREFRONT
│   │   ├── salon.component.ts
│   │   ├── salon.component.html
│   │   ├── salon.component.scss
│   │   └── booking/
│   │       ├── salon-booking.component.ts
│   │       └── salon-booking.component.html
│   │
│   ├── pro/                         # 4️⃣ PRO SPACE
│   │   ├── pro.component.ts
│   │   ├── dashboard/
│   │   │   ├── dashboard.component.ts
│   │   │   └── dashboard.component.html
│   │   ├── services/
│   │   │   ├── pro-services.component.ts
│   │   │   └── pro-services.component.html
│   │   ├── bookings/
│   │   │   ├── pro-bookings.component.ts
│   │   │   └── pro-bookings.component.html
│   │   ├── posts/
│   │   │   ├── pro-posts.component.ts
│   │   │   └── pro-posts.component.html
│   │   ├── availability/
│   │   │   ├── availability.component.ts
│   │   │   └── availability.component.html
│   │   └── settings/
│   │       ├── settings.component.ts
│   │       └── settings.component.html
│   │
│   ├── auth/
│   │   ├── login/
│   │   ├── register/
│   │   └── oauth2-redirect/
│   │
│   └── not-found/
│
├── features/                        # Business logic
│   │
│   ├── tenants/
│   │   ├── services/
│   │   │   └── tenants.service.ts
│   │   ├── store/
│   │   │   └── tenants.store.ts
│   │   └── models/
│   │       └── tenant.model.ts
│   │
│   ├── posts/
│   │   ├── services/
│   │   │   └── posts.service.ts
│   │   ├── store/
│   │   │   └── posts.store.ts
│   │   └── models/
│   │       └── post.model.ts
│   │
│   ├── cares/                       # (existing)
│   │   ├── services/
│   │   ├── store/
│   │   ├── models/
│   │   └── modals/
│   │
│   ├── bookings/                    # (existing)
│   │   ├── services/
│   │   ├── store/
│   │   ├── models/
│   │   └── modals/
│   │
│   ├── categories/                  # (existing)
│   │   ├── services/
│   │   ├── store/
│   │   └── models/
│   │
│   ├── users/                       # (existing)
│   │   ├── services/
│   │   ├── store/
│   │   └── models/
│   │
│   └── stats/
│       ├── services/
│       │   └── stats.service.ts
│       ├── store/
│       │   └── stats.store.ts
│       └── models/
│           └── stats.model.ts
│
├── shared/
│   │
│   ├── uis/
│   │   ├── salon-card/
│   │   ├── post-card/
│   │   ├── service-card/
│   │   ├── booking-calendar/
│   │   ├── booking-times/
│   │   ├── image-carousel/
│   │   └── crud-table/
│   │
│   ├── layout/
│   │   ├── header/
│   │   ├── footer/
│   │   ├── pro-sidebar/
│   │   └── pro-header/
│   │
│   └── modals/
│       ├── auth-modal/
│       └── confirm-modal/
│
├── core/
│   ├── auth/
│   │   ├── auth.service.ts
│   │   └── auth.interceptor.ts
│   ├── tenant/
│   │   └── tenant-context.service.ts
│   └── config/
│       └── api.config.ts
│
├── i18n/
│   └── transloco-loader.ts
│
├── app.routes.ts
├── app.config.ts
└── app.component.ts
```

### Route Definitions

```typescript
// app.routes.ts
export const routes: Routes = [
  // 1️⃣ LANDING / MARKETING
  { path: '', component: LandingComponent },

  // 2️⃣ DISCOVERY (Public)
  { path: 'discover', component: DiscoverComponent },
  { path: 'discover/:category', component: CategoryComponent },
  { path: 'feed', component: FeedComponent },

  // 3️⃣ SALON STOREFRONT (Public)
  { path: 'salon/:slug', component: SalonComponent },
  { path: 'salon/:slug/book', component: SalonBookingComponent },

  // 4️⃣ PRO SPACE (Authenticated)
  {
    path: 'pro',
    component: ProComponent,
    canActivate: [authGuard],
    children: [
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
      { path: 'dashboard', component: DashboardComponent },
      { path: 'services', component: ProServicesComponent },
      { path: 'bookings', component: ProBookingsComponent },
      { path: 'posts', component: ProPostsComponent },
      { path: 'availability', component: AvailabilityComponent },
      { path: 'settings', component: SettingsComponent },
    ]
  },

  // AUTH
  { path: 'login', component: LoginComponent },
  { path: 'register', component: RegisterComponent },
  { path: 'oauth2/redirect', component: OAuth2RedirectComponent },

  // 404
  { path: '**', component: NotFoundComponent },
];
```

### API Endpoints

| Method | Endpoint | Section | Description |
|--------|----------|---------|-------------|
| **DISCOVERY** |
| GET | `/api/categories` | Public | Business category list |
| GET | `/api/tenants?category=coiffure` | Public | Salons by category |
| GET | `/api/tenants/{slug}` | Public | Salon details |
| GET | `/api/feed` | Public | Global feed |
| GET | `/api/feed?tenant={slug}` | Public | Salon's feed |
| **SALON STOREFRONT** |
| GET | `/api/tenants/{slug}/cares` | Public | Salon's services |
| GET | `/api/tenants/{slug}/availability` | Public | Available slots |
| POST | `/api/tenants/{slug}/bookings` | Auth | Book appointment |
| GET | `/api/tenants/{slug}/calendar.ics` | Public | iCal export |
| **PRO SPACE** |
| GET | `/api/pro/dashboard` | Pro | Dashboard stats |
| GET | `/api/pro/bookings` | Pro | My bookings |
| CRUD | `/api/pro/cares` | Pro | Manage services |
| CRUD | `/api/pro/posts` | Pro | Manage posts |
| PUT | `/api/pro/availability` | Pro | Manage availability |
| PUT | `/api/pro/settings` | Pro | Salon settings |

### Architectural Boundaries

**API Boundaries:**
- `/api/` — Public endpoints (discovery, salon storefronts)
- `/api/pro/` — Authenticated pro endpoints (tenant resolved via JWT)
- `/api/admin/` — Platform admin (future)

**Component Boundaries:**
- `pages/` — Route-level components (smart, inject stores)
- `features/` — Business logic (services, stores, models)
- `shared/uis/` — Reusable dumb components (receive inputs, emit outputs)

**Data Boundaries:**
- Each tenant has isolated schema in Oracle
- Global data (categories, users) in shared schema
- Posts reference tenant_id for filtering

### Requirements to Structure Mapping

| Feature | Backend | Frontend |
|---------|---------|----------|
| Landing/Marketing | — | `pages/landing/` |
| Category Discovery | `category/` | `pages/discover/` |
| Salon Listing | `tenant/` | `features/tenants/` |
| Feed (Posts) | `post/` | `features/posts/`, `pages/discover/feed/` |
| Salon Storefront | `tenant/`, `care/` | `pages/salon/` |
| Booking | `booking/` | `features/bookings/`, `pages/salon/booking/` |
| Pro Dashboard | Stats queries | `features/stats/`, `pages/pro/dashboard/` |
| Pro Services | `care/` | `pages/pro/services/` |
| Pro Posts | `post/` | `pages/pro/posts/` |
| Pro Availability | `booking/` (slots) | `pages/pro/availability/` |
| Pro Settings | `tenant/` | `pages/pro/settings/` |

## Architecture Validation Results

### Coherence Validation ✅

**Decision Compatibility:**
All 7 architectural decisions are fully compatible with each other and the existing technology stack:
- Schema-per-tenant Oracle works with Spring Boot JPA via `AbstractRoutingDataSource`
- Path prefix routing integrates naturally with Angular Router and Spring MVC
- OAuth2 extension builds on existing Spring Security configuration
- All supporting services (Caffeine, Spring Mail, ical4j, Sentry) have native Spring Boot integration

**Pattern Consistency:**
Implementation patterns align perfectly with technology choices:
- Feature-first organization matches both Angular standalone and Spring Boot conventions
- SignalStore pattern leverages Angular 20 signals architecture
- DTO/Mapper pattern follows Spring MVC best practices
- Naming conventions are consistent across database, API, and code

**Structure Alignment:**
Project structure fully supports all architectural decisions:
- 4 application sections (Landing, Discovery, Salon, Pro) are clearly separated
- Multi-tenant infrastructure has dedicated `multitenancy/` package
- New features (posts, notifications, calendar) have designated locations
- Existing code integrates naturally with new structure

### Requirements Coverage Validation ✅

**Functional Requirements Coverage:**
All 56 functional requirements from the PRD are architecturally supported:
- Authentication (FR1-FR10): OAuth2 Spring Security + JWT ✅
- Vitrine configuration (FR11-FR19): `tenant/`, `care/` packages ✅
- Availability management (FR20-FR24): `booking/` package ✅
- Public storefront (FR25-FR28): `pages/salon/` + public APIs ✅
- Booking flow (FR29-FR34): `booking/` + `notification/` ✅
- Client booking management (FR35-FR39): Frontend + API ✅
- Pro booking management (FR40-FR44): `pages/pro/bookings/` ✅
- Calendar sync (FR45-FR47): `calendar/` + iCal ✅
- Dashboard stats (FR48-FR52): `features/stats/` ✅
- Multi-tenancy (FR53-FR56): `multitenancy/` infrastructure ✅

**Non-Functional Requirements Coverage:**
- High availability: Actuator health checks + Sentry monitoring ✅
- Mobile/desktop performance: Caffeine cache + HTTP caching + SSR ✅
- Data security: Schema isolation + JWT + CORS/CSRF ✅
- Scalability: Schema-per-tenant with Redis evolution path ✅
- GDPR: Tenant isolation enables data export/deletion ✅

### Implementation Readiness Validation ✅

**Decision Completeness:**
- 7 core architectural decisions documented with rationale
- Technology versions verified against existing codebase
- Deferred decisions (post-MVP) clearly identified
- Implementation sequence defined

**Structure Completeness:**
- Complete backend package structure with all files
- Complete frontend pages/features/shared structure
- All Angular routes defined
- All API endpoints documented

**Pattern Completeness:**
- 8 pattern categories defined (naming, structure, format, communication, process)
- Concrete examples provided for each pattern
- Enforcement guidelines with MUST/MUST NOT rules
- Anti-patterns documented

### Gap Analysis Results

**Critical Gaps:** None ✅

**Important Gaps (addressed):**
- Post entity structure: Defined during validation
- Business categories vs service categories: Clarified (platform-level categories)

**Minor Gaps (for implementation phase):**
- Test structure: To be defined during implementation
- CI/CD pipeline: GitHub Actions recommended
- Production deployment: Based on final hosting choice

### Architecture Completeness Checklist

**✅ Requirements Analysis**
- [x] Project context thoroughly analyzed
- [x] Scale and complexity assessed (medium, 5-10 salons MVP)
- [x] Technical constraints identified (brownfield, solo developer)
- [x] Cross-cutting concerns mapped (7 concerns)

**✅ Architectural Decisions**
- [x] Critical decisions documented (7 decisions)
- [x] Technology stack fully specified (Angular 20 + Spring Boot 3.5 + Oracle)
- [x] Integration patterns defined (OAuth2, SMTP, iCal)
- [x] Performance considerations addressed (Caffeine + HTTP cache)

**✅ Implementation Patterns**
- [x] Naming conventions established (DB, API, code)
- [x] Structure patterns defined (feature-first)
- [x] Communication patterns specified (SignalStore)
- [x] Process patterns documented (error handling)

**✅ Project Structure**
- [x] Complete directory structure defined
- [x] Component boundaries established (4 sections)
- [x] Integration points mapped (API boundaries)
- [x] Requirements to structure mapping complete

### Architecture Readiness Assessment

**Overall Status:** ✅ READY FOR IMPLEMENTATION

**Confidence Level:** HIGH

**Key Strengths:**
1. Progressive evolution — Existing code reused, no rewrite needed
2. Strong isolation — Schema-per-tenant for security and scalability
3. Clear patterns — Precise rules for code consistency
4. 4 distinct sections — Landing, Discovery, Salon, Pro Space well separated
5. Modern stack — Angular 20 + Spring Boot 3.5 with best practices
6. Vision-aligned — Supports "beauty social network" future evolution

**Areas for Future Enhancement:**
- Google Calendar bidirectional sync (post-MVP)
- Redis distributed cache (when multi-instance needed)
- Platform admin dashboard (post-MVP)
- Payment integration with Stripe (Growth phase)

### Implementation Handoff

**AI Agent Guidelines:**
- Follow all architectural decisions exactly as documented
- Use implementation patterns consistently across all components
- Respect project structure and boundaries
- Refer to this document for all architectural questions
- Add FR + EN translations for all new UI text
- Use modern Angular patterns (signals, @if/@for, inject())

**First Implementation Priority:**
1. Multi-tenant infrastructure (`multitenancy/` package)
2. Tenant entity and public APIs
3. Landing page and Discovery pages
4. Salon storefront pages
5. Pro Space with dashboard

**Post Entity Structure:**
```java
@Entity
@Table(name = "POSTS")
public class Post {
    @Id @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(name = "image_url", nullable = false)
    private String imageUrl;

    @Column(name = "caption", length = 500)
    private String caption;

    @Column(name = "category")
    private String category;  // coiffure, ongles, esthétique...

    @Column(name = "likes_count")
    private Integer likesCount = 0;

    @CreationTimestamp
    @Column(name = "created_at")
    private Instant createdAt;
}
```

