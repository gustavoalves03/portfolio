# Repository Guidelines

## Project Structure & Module Organization
- `backend/`: Spring Boot (Java 21). Feature-first packages: `users/`, `category/`, `care/`, `bookings/` with layers `domain/`, `app/` (services), `web/` (controllers, `dto/`, `mapper/`), and `repo/`. Config under `config/`. Tests in `backend/src/test/...`.
- `frontend/`: Angular 20 (SSR-ready). Feature areas under `src/app/` like `features/video-games/`, `shared/uis/`, `pages/`, and `Tasks/`. Public assets in `frontend/public/`.
- Orchestration: `docker-compose.yml` (+ `docker-compose.override.yml` for hot-reload dev), service Dockerfiles under each module.

## Build, Test, and Development Commands
- Backend (from `backend/`):
  - `mvn clean spring-boot:run`: run API in dev.
  - `mvn test`: JUnit tests.
  - `mvn package`: build jar.
- Frontend (from `frontend/`):
  - `npm ci && npm start`: local dev at `http://localhost:4200`.
  - `npm run build`: production build (SSR output in `dist/app/server`).
  - `npm test`: Karma/Jasmine tests. `npm run serve:ssr:app` to serve built SSR.
- Full stack via Docker:
  - `docker compose up -d` (DB+API+Web). For hot reload: `docker compose -f docker-compose.override.yml up` (frontend on `http://localhost:4300`).

## Coding Style & Naming Conventions
- Java: 4-space indent; package-by-feature. Suffixes: `*Controller`, `*Service`, `*Repository`. DTOs under `web/dto`, mappers under `web/mapper`. Use Lombok for boilerplate.
- TypeScript/Angular: Prettier enforced (`singleQuote: true`, `printWidth: 100`). Kebab-case filenames (`table-video-games.ts`), PascalCase classes, `*.component.ts/html/scss` for components. SCSS for styles.

## Testing Guidelines
- Backend: JUnit 5 + Spring Boot Test. Place tests mirroring packages; name `*Tests.java`. Run `mvn test`. Prefer service, mapper, and controller slice tests.
- Frontend: Jasmine/Karma with `*.spec.ts` colocated with source. Run `npm test`. Add DOM screenshots to PR when UI changes.

## Commit & Pull Request Guidelines
- Current history uses `ok:`/`wip:` prefixes. Prefer Conventional Commits: `feat:`, `fix:`, `chore:`, `test:`, `refactor:`.
- PRs: clear description, linked issues, screenshots/GIFs for UI, API change notes, and steps to reproduce. Ensure `docker compose up` works locally.

## Security & Configuration Tips
- Database via Oracle image. Provide secrets via environment or `.env` that is not committed: `ORACLE_PASSWORD`, `APP_USER`, `APP_USER_PASSWORD`.
- Backend profile: `SPRING_PROFILES_ACTIVE=docker`; configure `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` via env.
- Frontend may read `API_BASE_URL` from compose for API calls.

## 🐚 Contexte de l’application — Pretty Face

### Vision & philosophie
- Marque : Atelier d’esthétique “Pretty Face”.
- Essence : beauté **subtile, naturelle et authentique**, inspirée par Vénus naissant d’un coquillage.
- Ton général : **doux, apaisant, pédagogique**, jamais agressif ni commercial à outrance.
- Objectif produit : simplifier la relation client (prise de RDV, suivi beauté), fluidifier la gestion interne (staff, congés, stock), et offrir un **conseil personnalisé** via IA.

### Public & scénario d’usage
- Clientes principales : adultes recherchant des soins visage/corps, conseils produits, suivi personnalisé.
- Parcours clés :
    1) Découverte des soins → prise de RDV → rappels automatiques.
    2) Suivi beauté : historique de prestations, produits utilisés, photos avant/après.
    3) Boutique : panier, paiement, gestion des stocks, recommandations IA.

### Périmètre fonctionnel (MVP +)
- Authentification : email + OAuth (Google, Facebook, Apple).
- Agenda : création/édition/annulation RDV, gestion congés/indisponibilités staff, buffers, durées.
- Suivi beauté : fiches client, notes esthéticienne, photos (stockage sécurisé), consentements.
- Boutique : catalogue, panier, paiement (PSP), stock, bons/disabled promos.
- IA assistée :
    - aide à la prise de RDV (créneaux, durées soins),
    - recommandations de soins/produits,
    - explications simples des procédures,
    - guidance dans la boutique.

### Design & identité visuelle
- Mots-clés esthétiques : **nacre, coquillage rosé, sable, mer brume**, formes arrondies, ombres très **douces**.
- Micro-animations : transitions légères (hover/focus), durées 150–250ms, **jamais** flashy.
- Accessibilité : WCAG **AA** (contrastes, focus visible, aria-labels, clavier ok).

## Angular 20 — pratiques recommandées

> Objectif: éviter les API dépréciées et aligner le code sur Angular 20 (standalone, control flow, signals, SSR, tests).

### Démarrage & Providers
- Préférer `bootstrapApplication` et les providers fonctionnels, pas les modules.

```ts
// frontend/src/main.ts
import { bootstrapApplication } from '@angular/platform-browser';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors, withFetch, withTransferCache } from '@angular/common/http';
import { provideClientHydration } from '@angular/platform-browser';
import { provideAnimations } from '@angular/platform-browser/animations';
import { AppComponent } from './app/app.component';
import { routes } from './app/app.routes';

bootstrapApplication(AppComponent, {
  providers: [
    provideRouter(routes),
    provideHttpClient(
      withFetch(),
      withTransferCache(),
      withInterceptors([authInterceptor])
    ),
    provideClientHydration(),
    provideAnimations(),
  ],
});

// Exemple d'intercepteur fonctionnel (Angular 20)
import { HttpInterceptorFn } from '@angular/common/http';
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  // ajouter headers/token si besoin
  return next(req);
};
```

### Standalone components (Sans NgModule)
- Déclarer des composants standalone et importer ce qu'ils utilisent dans `imports`.

```ts
import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-example',
  standalone: true,
  imports: [RouterLink],
  template: `<a routerLink="/home">Home</a>`
})
export class ExampleComponent {}
```

### Routing moderne
- Définir `Routes` avec lazy via `loadComponent`/`loadChildren`.
- Utiliser des guards/résolveurs fonctionnels avec `inject()`.

```ts
// frontend/src/app/app.routes.ts
import { Routes } from '@angular/router';
import { inject } from '@angular/core';
import { AuthService } from './shared/auth.service';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./pages/home/home.component').then(m => m.HomeComponent),
  },
  {
    path: 'dashboard',
    loadComponent: () => import('./pages/dashboard/dashboard.component').then(m => m.DashboardComponent),
    canActivate: [() => inject(AuthService).requireAuth()],
  },
];
```

### Control flow de template
- Préférer `@if`, `@for`, `@switch`, `@defer` à `*ngIf/*ngFor` dans le nouveau code.

```html
@if (items().length === 0) { Aucun élément }
@for (item of items(); track item.id) { {{ item.name }} }
@defer (on viewport) { <heavy-widget /> }
```

### Signals pour l'état local
- Utiliser `signal/computed/effect` pour l'état UI local; interop RxJS via `toSignal/toObservable`.

```ts
import { signal, computed, effect } from '@angular/core';

const count = signal(0);
const doubled = computed(() => count() * 2);
effect(() => console.log('valeur', doubled()));
```

### SSR & Hydration
- Builder SSR via `@angular/ssr`. Activer `provideClientHydration()` et `withTransferCache()` pour le cache des requêtes.
- Commandes du repo: `npm run build` puis `npm run serve:ssr:app`.

### Tests (v20)
- Utiliser les providers fonctionnels dans `TestBed` (pas `*Module`).

```ts
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';

beforeEach(() => {
  TestBed.configureTestingModule({
    providers: [
      provideHttpClient(),
      provideRouter([]),
      provideNoopAnimations(),
    ],
  });
});
```

### À éviter (déprécié/legacy dans ce repo)
- `NgModule`, `BrowserModule`, `HttpClientModule`, `RouterModule.forRoot/forChild` dans le nouveau code.
- Intercepteurs/guards class‑based héritant d'interfaces; préférer les variantes fonctionnelles.
- Ancien control flow `*ngIf/*ngFor` pour les nouveaux écrans (toléré en existant).
