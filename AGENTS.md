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

## 🐚 Contexte de l’application — Fleur de Coquillage

### Vision & philosophie
- Marque : Atelier d’esthétique “Fleur de Coquillage”.
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
