# Booking Policy — Garde-fous anti-spam (PR1)

**Date** : 2026-05-11
**Statut** : design validé, prêt pour plan d'implémentation
**Scope** : limites configurables côté pro pour éviter qu'un même client monopolise les créneaux. Première moitié d'un chantier plus large dont la suite (PR2 — réputation client + annulations + modale 24h) fera l'objet d'un spec dédié.

## Contexte

Aujourd'hui un client peut prendre autant de rendez-vous qu'il veut chez un même salon, le même jour ou la même semaine. Cas problématiques :
- Un nouveau client (ou bot) réserve 5 créneaux et n'en honore qu'un.
- Un client habituel bloque la disponibilité d'autres clients en multipliant les rdv.

Le pro n'a aucun levier pour limiter ça. On introduit une **booking policy** au niveau du salon, configurable depuis l'UI pro, avec des valeurs par défaut sensées.

## Objectifs

1. Le pro peut configurer 2 limites depuis `/pro/availability` :
   - `maxBookingsPerDayPerClient` : nombre max de rdv qu'un même client peut prendre le même jour (défaut **1**).
   - `maxBookingsPerWeekForNewClient` : nombre max de rdv pour un client qui n'a jamais eu de rdv confirmé chez ce salon (défaut **1**).
2. Une violation de limite renvoie une **HTTP 409** avec un code typé pour permettre au front d'afficher un message localisé spécifique.
3. Tests unitaires + intégration backend ; tests Karma frontend.

Hors scope (couverts au PR2) : annulation client/pro, système de réputation, modale post-booking avec règle 24h, no-show automatique.

## Modèle de données

Nouvelle entité **`BookingPolicy`** dans `bookings/domain/`. Une seule row par tenant (multitenancy par schéma Oracle existante).

```java
@Entity
@Table(name = "BOOKING_POLICY")
public class BookingPolicy {
  @Id @GeneratedValue Long id;
  @Column(name = "max_bookings_per_day_per_client", nullable = false)
  Integer maxBookingsPerDayPerClient;       // défaut 1
  @Column(name = "max_bookings_per_week_for_new_client", nullable = false)
  Integer maxBookingsPerWeekForNewClient;   // défaut 1
  @Column(name = "updated_at", nullable = false)
  LocalDateTime updatedAt;
}
```

Pas de FK vers `Tenant` : la séparation est déjà au niveau schéma JPA. La singletoneté par tenant est garantie par le service (`findFirst()` + insertion conditionnelle au boot).

`CareBooking` reste inchangé.

## Migration Flyway

Nouveau fichier per-tenant `db/migration/tenant/V{next}__create_booking_policy.sql` :

```sql
CREATE TABLE BOOKING_POLICY (
  id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  max_bookings_per_day_per_client NUMBER(2) NOT NULL,
  max_bookings_per_week_for_new_client NUMBER(2) NOT NULL,
  updated_at TIMESTAMP NOT NULL
);
INSERT INTO BOOKING_POLICY (max_bookings_per_day_per_client, max_bookings_per_week_for_new_client, updated_at)
VALUES (1, 1, CURRENT_TIMESTAMP);
```

Le `TenantProvisioningService` exécute déjà les migrations tenant à la création d'un nouveau salon, donc la row par défaut est insérée pour les nouveaux et les existants au prochain boot.

## Logique de validation backend

### Service `BookingPolicyService` (`bookings/app/`)

```java
void validateClientLimits(User client, LocalDate appointmentDate);
```

Appelé depuis `BookingService.create(...)` **avant** l'INSERT du `CareBooking`, dans la même transaction.

### Algorithme

1. Charger la policy (`bookingPolicyRepository.findFirst()`). Si absente, créer la row avec les défauts (1/1) — cas dégradé pour tenants legacy.
2. **Check journalier** :
   ```
   count = countByUserIdAndAppointmentDateAndStatusIn(
     userId, appointmentDate, [PENDING, CONFIRMED]
   )
   if count >= policy.maxBookingsPerDayPerClient
     throw BookingLimitExceededException("BOOKING_LIMIT_DAILY_EXCEEDED", limit, count)
   ```
3. **Check nouveau client** :
   ```
   isNew = !existsByUserIdAndStatusAndAppointmentDateBefore(
     userId, CONFIRMED, today
   )
   if isNew:
     weekStart = appointmentDate.with(DayOfWeek.MONDAY)
     weekEnd = weekStart.plusDays(6)
     weekCount = countByUserIdAndAppointmentDateBetweenAndStatusIn(
       userId, weekStart, weekEnd, [PENDING, CONFIRMED]
     )
     if weekCount >= policy.maxBookingsPerWeekForNewClient
       throw BookingLimitExceededException("BOOKING_LIMIT_NEW_CLIENT_WEEKLY_EXCEEDED", limit, weekCount)
   ```

### Race conditions

Deux INSERT concurrents peuvent passer le check en même temps. Acceptable :
- La contrainte SQL `UK_BOOKING_SLOT` (déjà existante) bloque le double-booking *du même créneau*.
- Pour 2 créneaux différents le même jour, le pire cas est qu'un client ait 2 rdv au lieu d'1 — non bloquant.
- Un verrou applicatif (lock pessimiste) coûterait plus que le bénéfice.

### Exception

```java
public class BookingLimitExceededException extends RuntimeException {
  String code;       // "BOOKING_LIMIT_DAILY_EXCEEDED" | "BOOKING_LIMIT_NEW_CLIENT_WEEKLY_EXCEEDED"
  int limit;
  int currentCount;
}
```

Mappée dans `common/error/GlobalExceptionHandler` → **HTTP 409** :

```json
{
  "code": "BOOKING_LIMIT_DAILY_EXCEEDED",
  "message": "Vous avez déjà un rendez-vous ce jour-là.",
  "limit": 1,
  "currentCount": 1
}
```

### Repository — méthodes à ajouter

Dans `CareBookingRepository` :

```java
long countByUserIdAndAppointmentDateAndStatusIn(Long userId, LocalDate date, Collection<CareBookingStatus> statuses);
long countByUserIdAndAppointmentDateBetweenAndStatusIn(Long userId, LocalDate from, LocalDate to, Collection<CareBookingStatus> statuses);
boolean existsByUserIdAndStatusAndAppointmentDateBefore(Long userId, CareBookingStatus status, LocalDate today);
```

Nouveau `BookingPolicyRepository extends JpaRepository<BookingPolicy, Long>`.

## API

### `BookingPolicyController` (`bookings/web/`)

```
GET  /api/pro/booking-policy   → BookingPolicyResponse
PUT  /api/pro/booking-policy   → BookingPolicyResponse
```

- Sécurité : `@PreAuthorize("hasRole('PRO')")` (même pattern que les autres `/api/pro/*`).
- DTO request `UpdateBookingPolicyRequest` :
  ```java
  @Min(1) @Max(10) Integer maxBookingsPerDayPerClient;
  @Min(1) @Max(10) Integer maxBookingsPerWeekForNewClient;
  ```
- DTO response `BookingPolicyResponse` : 2 champs + `updatedAt`.
- Mapper simple dans `web/mapper/BookingPolicyMapper.java`.

## UI pro — onglet « Règles de réservation »

Page `/pro/availability` passe d'un layout simple à un `mat-tab-group` :
- **Onglet 1** : « Horaires » (le `availability-timeline` actuel).
- **Onglet 2** : « Règles de réservation » (nouveau).

### Composant `BookingPolicyComponent`

`frontend/src/app/features/availability/booking-policy/` :
- `booking-policy.component.ts/.html/.scss/.spec.ts`
- 2 inputs Material `<input matInput type="number" min="1" max="10">` + bouton « Enregistrer ».
- Snackbar succès/erreur via le pattern existant.
- Helper text expliquant chaque limite (« Limite le nombre de rendez-vous qu'un même client peut prendre le même jour. »).

### Service + store

- `BookingPolicyService` (HTTP : `getCurrent()`, `update(req)`).
- `BookingPolicyStore` (NgRx SignalStore : `withState({policy: null})`, `withRequestStatus()`, `withMethods({load, update})`).
- Provided au niveau du composant `BookingPolicyComponent`.

### Côté booking client

Dans le flow de réservation client (composant qui catche l'erreur HTTP du POST booking), brancher sur le code 409 :

```ts
if (err.status === 409 && err.error?.code === 'BOOKING_LIMIT_DAILY_EXCEEDED') {
  showError(transloco.translate('errors.booking.limitDaily'));
} else if (err.status === 409 && err.error?.code === 'BOOKING_LIMIT_NEW_CLIENT_WEEKLY_EXCEEDED') {
  showError(transloco.translate('errors.booking.limitNewClientWeekly'));
}
```

## Internationalisation

Clés à ajouter dans **`fr.json` ET `en.json`** :

```
pro.bookingPolicy.tab                    "Règles de réservation" / "Booking rules"
pro.bookingPolicy.title                  "Limites de réservation client" / "Client booking limits"
pro.bookingPolicy.maxPerDay.label        "Rendez-vous max par jour pour un même client" / "Max bookings per day per client"
pro.bookingPolicy.maxPerDay.help         "Empêche un client de prendre plusieurs rendez-vous le même jour." / "..."
pro.bookingPolicy.maxPerWeekNew.label    "Rendez-vous max par semaine pour un nouveau client" / "Max bookings per week for a new client"
pro.bookingPolicy.maxPerWeekNew.help     "Limite les nouveaux clients (jamais venus) à ce nombre de rdv par semaine." / "..."
pro.bookingPolicy.save                   "Enregistrer" / "Save"
pro.bookingPolicy.saved                  "Règles mises à jour" / "Rules updated"
pro.bookingPolicy.error                  "Erreur lors de la sauvegarde" / "Save failed"
errors.booking.limitDaily                "Vous avez déjà un rendez-vous ce jour-là chez ce salon." / "You already have a booking that day at this salon."
errors.booking.limitNewClientWeekly      "Pour une première visite, un seul rendez-vous est autorisé cette semaine." / "First visits are limited to one booking per week."
```

## Tests

### Backend

1. **`BookingPolicyServiceTests.java`** (unit, mocks) :
   - Limite journalière respectée (count < limit) → pas d'exception.
   - Limite journalière atteinte (count == limit) → `BookingLimitExceededException` avec code `BOOKING_LIMIT_DAILY_EXCEEDED`, `limit` et `currentCount` corrects.
   - Statuts CANCELLED et NO_SHOW ne comptent pas (mock repo renvoie count basé sur PENDING+CONFIRMED uniquement).
   - Client avec ≥ 1 booking CONFIRMED passé → règle nouveau-client ignorée.
   - Nouveau client + 0 booking dans la semaine → OK.
   - Nouveau client + 1 booking PENDING dans la semaine → exception `BOOKING_LIMIT_NEW_CLIENT_WEEKLY_EXCEEDED`.
   - Frontière de semaine : booking dimanche puis lundi suivant → 2 semaines distinctes, OK.
   - Policy absente → repo crée la row par défaut (1/1).

2. **`BookingPolicyControllerTests.java`** (`@WebMvcTest`) :
   - GET sans auth → 401.
   - GET avec rôle CLIENT → 403.
   - GET avec rôle PRO → 200 + body conforme.
   - PUT valide → 200, body retourné.
   - PUT avec `maxBookingsPerDayPerClient = 0` → 400 (validation `@Min(1)`).
   - PUT avec `maxBookingsPerWeekForNewClient = 99` → 400 (validation `@Max(10)`).

3. **`BookingServiceLimitsIntegrationTests.java`** (`@DataJpaTest` + service réel) :
   - User avec 1 booking PENDING le 2026-05-15 → 2ème booking le même jour → 409.
   - Nouveau user, 1 booking le mardi → 2ème le jeudi de la même semaine → 409.
   - User avec 1 booking CONFIRMED dans le passé → 2 bookings la même semaine → OK.

### Frontend (Karma/Jasmine)

Toutes les nouvelles surfaces frontend sont testées.

1. **`booking-policy.component.spec.ts`** (UI pro) :
   - Affiche les valeurs venant du store au chargement.
   - Inputs liés (typer une valeur met à jour le form state).
   - Validation client : `< 1` ou `> 10` désactive le bouton « Enregistrer ».
   - Clic « Enregistrer » avec form valide → `store.update({...})` appelé avec les bonnes valeurs.
   - Snackbar succès si l'update est fulfilled.
   - Snackbar erreur si l'update échoue (pending error).
   - État `isPending` désactive le bouton et affiche un spinner.

2. **`booking-policy.store.spec.ts`** :
   - `load()` → appelle `service.getCurrent()`, patch state, isFullfilled.
   - `update(req)` → appelle `service.update(req)`, patch state avec la réponse.
   - Erreur HTTP au load → setError, isPending=false, état `policy` reste null.
   - Erreur HTTP à l'update → setError, état `policy` non modifié (rollback implicite).

3. **`booking-policy.service.spec.ts`** :
   - `getCurrent()` → GET `/api/pro/booking-policy`, parse réponse.
   - `update(req)` → PUT `/api/pro/booking-policy` avec body sérialisé.
   - Erreur HTTP propagée à l'observable (pour que le store puisse setError).

4. **`pro-availability-page.component.spec.ts`** (mise à jour de l'existant si présent, sinon nouveau) :
   - 2 onglets rendus (`mat-tab-group` avec 2 `mat-tab`).
   - Onglet 1 affiche le timeline existant.
   - Onglet 2 affiche le `BookingPolicyComponent`.
   - Switch d'onglet préserve l'état (l'edit en cours dans Règles n'est pas perdu en revenant à Horaires).

5. **`booking-flow.spec.ts` (ou mise à jour du composant booking client existant)** — couvre la consommation côté client :
   - POST booking renvoie 409 avec `code: 'BOOKING_LIMIT_DAILY_EXCEEDED'` → snackbar avec le texte de `errors.booking.limitDaily`.
   - POST booking renvoie 409 avec `code: 'BOOKING_LIMIT_NEW_CLIENT_WEEKLY_EXCEEDED'` → snackbar avec le texte de `errors.booking.limitNewClientWeekly`.
   - POST booking renvoie 409 avec un code inconnu → fallback message générique.
   - POST booking renvoie 500 → branche d'erreur générique existante non perturbée.
   - Le créneau choisi reste sélectionné après l'erreur (le client peut re-tenter sans tout refaire).

Pas de Cypress/Playwright dans ce PR : la stack n'a pas encore d'E2E browser. Karma + tests d'intégration HTTP couvrent le flow.

## Récapitulatif livrable

| Couche | Fichiers |
|---|---|
| Domain | `BookingPolicy.java` |
| Repo | `BookingPolicyRepository.java` (nouveau), +3 méthodes dans `CareBookingRepository.java` |
| App | `BookingPolicyService.java`, branchement dans `BookingService.create()` |
| Web | `BookingPolicyController.java`, `UpdateBookingPolicyRequest.java`, `BookingPolicyResponse.java`, `BookingPolicyMapper.java`, `BookingLimitExceededException.java` + handler dans `GlobalExceptionHandler` |
| Migration | `db/migration/tenant/V{next}__create_booking_policy.sql` |
| Frontend | `BookingPolicyComponent` (4 fichiers), `BookingPolicyService.ts`, `BookingPolicyStore.ts` + spec |
| Frontend booking | catch 409 + 2 messages localisés dans le flow client |
| Tests | 3 fichiers backend + 5 frontend (component, store, service, tabs, flow client) |
| i18n | `pro.bookingPolicy.*` et `errors.booking.limit*` (fr + en) |

## Décisions clés

- **Une row par tenant** plutôt que policy par soin/employé : 95 % des cas couverts, ergonomique pour le pro.
- **Logique en code** plutôt que contrainte SQL : permet la nuance « nouveau client », testable, lisible.
- **HTTP 409 + code typé** : front affiche message localisé spécifique, code stable pour les tests.
- **Onglet dans `/pro/availability`** : cohérent thématiquement, évolutif pour PR2 (annulation, no-show).
- **Statuts comptés : PENDING + CONFIRMED** : un client qui annule libère son créneau et peut re-réserver.
- **Semaine ISO Lun-Dim** : simple à expliquer, simple à calculer, aligne avec l'affichage hebdo.
- **Defaut 1/1** : strict mais cohérent ; le pro peut assouplir jusqu'à 10/10.

## Suite (hors scope ce PR)

PR2 — Réputation + annulation, à brainstormer dans une session dédiée :
- Modèle `ClientReputation` (points cumulés par tenant).
- Endpoint annulation client `/api/bookings/{id}/cancel` avec calcul de pénalité si < 24 h.
- Endpoint annulation pro `/api/pro/bookings/{id}/cancel`.
- Modale post-booking côté client affichant la règle 24 h.
- Mise à jour réputation à chaque visite honorée / annulation tardive / no-show.
