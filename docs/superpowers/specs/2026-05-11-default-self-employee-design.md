# Employé par défaut "pro-self" + UI fix flèche header

**Date :** 2026-05-11
**Auteur :** Claude (brainstorm validé Gustavo)
**Scope :** Backend (provisioning + soin + migration legacy) + Frontend (UI flèche stepper).
**Hors scope :** Notion de plan/subscription (Solo/Team/…), masquage UI de la page `/pro/employees` selon plan, page d'édition employé.

## Contexte

Depuis la PR `feat/booking-stepper-employee` (mergée 2026-05-11), tout soin doit être lié à au moins un employé pour pouvoir être réservé par le pro depuis `/pro/bookings`. Or :

1. À l'inscription pro, aucun employé n'est créé. Un pro fraîchement inscrit ne peut pas créer de réservation tant qu'il n'a pas manuellement ajouté un employé.
2. À la création de soin, aucun employé n'est attaché par défaut. Un soin créé sans sélectionner d'employé devient orphelin.
3. La modale stepper a un bouton "Retour" en bas à gauche (ergonomie cassée avec la nouvelle hauteur fixe).

Cette PR garantit l'invariant **"tout tenant a au moins un employé actif"** et **"tout soin est lié à au moins un employé"**, sans toucher au modèle de plans (concept futur).

## Décisions de design

| # | Décision | Pourquoi |
|---|---|---|
| D1 | L'employé par défaut = **le pro lui-même**. `userId = ownerId`, `name/email` copiés depuis `User`, `phone = null`, `active = true`. | Représente la réalité du pro solo (esthéticien qui se réserve ses propres créneaux). Visible nominativement dans les notifications client. |
| D2 | Création à la **provision du tenant** (`TenantProvisioningService.provision`). | Invariant simple : dès qu'un tenant existe, il a un employé. Pas de cas particulier "tenant vide". |
| D3 | Idempotent : si un Employee avec `userId = owner.id` existe déjà, on saute. | Résiste à un retry de la provision (rare mais possible). |
| D4 | **Fallback à la création de soin** : si la requête de création ne fournit AUCUN employé assigné, on attache automatiquement l'employé pro-self. Si le pro liste explicitement des employés (même un seul), on respecte son choix sans ajouter pro-self. | Donne l'autonomie au pro pour déléguer un soin à un autre employé sans lui-même. Préserve l'invariant "tout soin a ≥1 employé" pour le cas par défaut (mono-pro). |
| D5 | **Migration Flyway V4** tenant-scoped pour les tenants legacy. SQL idempotent qui (a) crée l'employé pro-self s'il n'existe pas, (b) lie cet employé à tous les soins existants qui n'ont aucun employé. | Reproductible, versionné, identique dev/staging/prod. Profite du `${appSchema}` placeholder et des synonymes USERS/TENANTS du baseline V1. |
| D6 | **UI** : retirer le bouton "Retour" en bas du stepper. Ajouter une flèche `arrow_back` dans le header, à gauche du titre, conditionnée à `currentStep() > 1`. Le bouton X reste à droite. | Compatible avec la nouvelle taille fixe de la modale (le bouton bas mangeait de la place inutilement). Plus standard. |
| D7 | **Pas de modèle de plans** dans cette PR. La logique "employé par défaut existe sans Team" est obtenue gratuitement : tout tenant a l'employé pro-self quoi qu'il arrive. Quand on introduira les plans, la page `/pro/employees` sera cachée pour les plans bas, mais l'employé pro-self continuera d'exister silencieusement en base. | Minimise le scope. Évite un changement de schéma prématuré. |

## Architecture

### Backend — `EmployeeService.createSelfEmployee`

Nouvelle méthode publique dans `EmployeeService` :

```java
@Transactional
public Employee createSelfEmployee(User owner) {
    String tenantSlug = TenantContext.requireActive();

    // Idempotent : si déjà présent dans ce tenant pour ce user, on le renvoie
    return employeeRepository.findByUserId(owner.getId())
            .orElseGet(() -> {
                Employee e = new Employee();
                e.setUserId(owner.getId());
                e.setName(owner.getName());
                e.setEmail(owner.getEmail());
                e.setPhone(null);  // User n'a pas de phone — pro le renseignera dans son profil employé
                e.setActive(true);
                return employeeRepository.save(e);
            });
}
```

**Ajout au repository :**
```java
Optional<Employee> findByUserId(Long userId);
```

### Backend — Hook dans `TenantProvisioningService`

Modification de `provision(User owner)` :

```java
@Transactional
public Tenant provision(User owner) {
    String baseSlug = SlugUtils.toSlug(owner.getName());
    String slug = ensureUniqueSlug(baseSlug);

    logger.info("Provisioning tenant for user {} with slug {}", owner.getId(), slug);
    tenantSchemaManager.provisionSchema(slug);

    Tenant tenant = Tenant.builder()
            .slug(slug)
            .name(null)
            .ownerId(owner.getId())
            .status(TenantStatus.DRAFT)
            .build();
    Tenant saved = tenantRepository.save(tenant);

    // NEW: switch tenant context and create self-employee
    TenantContext.setCurrentTenant(slug);
    try {
        employeeService.createSelfEmployee(owner);
    } finally {
        TenantContext.clear();
    }

    logger.info("Tenant {} provisioned successfully (id={})", slug, saved.getId());
    return saved;
}
```

Le `TenantProvisioningService` reçoit `EmployeeService` injecté en constructeur.

### Backend — Fallback à la création de soin

Modification du flux qui crée un `Care` (probablement `CareService.create(CreateCareRequest)`). À investiguer au moment du plan, mais la règle est :

```java
public Care create(CreateCareRequest req) {
    String tenantSlug = TenantContext.requireActive();
    Long ownerId = tenantService.findBySlug(tenantSlug)
            .orElseThrow()
            .getOwnerId();

    Care care = new Care();
    // ... set fields ...

    if (req.assignedEmployeeIds() == null || req.assignedEmployeeIds().isEmpty()) {
        // Fallback : attacher l'employé pro-self
        employeeRepository.findByUserId(ownerId)
                .ifPresent(e -> care.getAssignedEmployees().add(e));
    } else {
        // Le pro a choisi explicitement
        List<Employee> employees = employeeRepository.findAllById(req.assignedEmployeeIds());
        care.setAssignedEmployees(new HashSet<>(employees));
    }

    return careRepository.save(care);
}
```

⚠️ Le champ exact du DTO (`assignedEmployeeIds`, `employeeIds`, autre nom) sera vérifié au plan. Le principe est : **liste vide ou absente → fallback pro-self ; liste explicite → respecter**.

### Backend — Migration Flyway V4

Fichier : `backend/src/main/resources/db/migration/tenant/V4__seed_self_employee.sql`

```sql
-- Seed the "pro-self" employee for legacy tenants that were provisioned
-- before TenantProvisioningService started creating one automatically.
--
-- Idempotent in two ways:
--   1. The INSERT INTO EMPLOYEES only fires if no row with user_id = owner_id exists.
--   2. The INSERT INTO EMPLOYEE_CARES only fires for cares that have ZERO employees.
--
-- Placeholders resolved by TenantFlywayService:
--   ${tenantSchema} → the current tenant schema (e.g. TENANT_SOPHIE)
--   ${appSchema}    → the shared app schema (e.g. APPUSER)
--
-- Cross-schema joins use the synonyms USERS / TENANTS created in V1 baseline.

-- 1. Create the pro-self Employee from the tenant's owner (USERS row) if missing.
INSERT INTO "${tenantSchema}".EMPLOYEES (USER_ID, NAME, EMAIL, PHONE, ACTIVE, CREATED_AT)
SELECT u.ID, u.NAME, u.EMAIL, NULL, 1, CURRENT_TIMESTAMP
FROM "${tenantSchema}".USERS u
JOIN "${tenantSchema}".TENANTS t ON t.OWNER_ID = u.ID
WHERE t.SLUG = '${tenantSchema}'  -- TODO at plan time: the tenant slug case may need normalization (lower vs upper)
  AND NOT EXISTS (
      SELECT 1 FROM "${tenantSchema}".EMPLOYEES e WHERE e.USER_ID = u.ID
  );

-- 2. Link the (now-existing) pro-self employee to all orphan cares (cares with no employee).
INSERT INTO "${tenantSchema}".EMPLOYEE_CARES (EMPLOYEE_ID, CARE_ID)
SELECT e.ID, c.ID
FROM "${tenantSchema}".EMPLOYEES e
JOIN "${tenantSchema}".USERS u ON u.ID = e.USER_ID
JOIN "${tenantSchema}".TENANTS t ON t.OWNER_ID = u.ID
CROSS JOIN "${tenantSchema}".CARES c
WHERE t.SLUG = '${tenantSchema}'
  AND NOT EXISTS (
      SELECT 1 FROM "${tenantSchema}".EMPLOYEE_CARES ec WHERE ec.CARE_ID = c.ID
  );
```

⚠️ **Point sensible à creuser au plan** : le slug du tenant vs le nom du schéma. Dans le baseline V1 j'ai vu que `${tenantSchema}` est une chaîne du genre `TENANT_SALON_PARIS` (uppercase), alors que `Tenant.slug` côté Java est probablement `salon-paris` (kebab-case). Le `WHERE t.SLUG = '${tenantSchema}'` ne matchera pas tel quel. Solutions possibles :
- Passer un placeholder additionnel `${tenantSlug}` (kebab-case) dans `TenantFlywayService` et l'utiliser ici
- Faire un `WHERE UPPER(REPLACE(t.SLUG, '-', '_')) = '${tenantSchema}'` (fragile)
- Sélectionner via une jointure inverse depuis `EMPLOYEES.USER_ID` plutôt que de partir du slug
- À trancher au moment du plan en lisant `SlugUtils.toSlug` côté Java

### Frontend — Flèche header

Modifs dans `booking-stepper.component.ts` :

**Template — header :**
```html
<div class="stepper-header">
  @if (currentStep() > 1) {
    <button class="btn-header-back" data-testid="stepper-back" (click)="goBack()" type="button">
      <mat-icon>arrow_back</mat-icon>
    </button>
  }
  <button class="btn-close" data-testid="stepper-close" (click)="dialogRef.close()" type="button">
    <mat-icon>close</mat-icon>
  </button>
  <span class="stepper-title">{{ 'booking.stepper.confirm' | transloco }}</span>
  <span class="step-counter">{{ currentStep() }}/3</span>
</div>
```

Ordre visuel : `[arrow_back?] [×] [titre flex-1] [counter]`. La flèche arrive AVANT le X dans le DOM mais visuellement elle est positionnée à gauche grâce au flex order/CSS.

**Template — suppression du bouton Retour bas :**
- Retirer entièrement le bloc `@if (currentStep() > 1) { <button class="btn-back" ... > }` à la fin du template.

**Styles :** ajouter `.btn-header-back` (similaire à `.btn-close`), supprimer `.btn-back` du bloc styles.

## Plan de test

### Backend (JUnit + AssertJ + Spring Boot Test)

1. **`EmployeeServiceTests.createSelfEmployee`** (3 tests) :
   - `createSelfEmployee_createsEmployeeMatchingOwner_whenAbsent` — appelle, vérifie que l'employé créé a `userId = owner.id`, `name = owner.name`, `email = owner.email`, `phone = null`, `active = true`.
   - `createSelfEmployee_isIdempotent_returnsExisting` — appelle deux fois, vérifie qu'un seul Employee existe et que le 2e appel renvoie le même.
   - `createSelfEmployee_failsWithoutTenantContext` — appelle hors `TenantContext`, vérifie qu'une `IllegalStateException` est levée (`requireActive`).

2. **`TenantProvisioningServiceTests`** (2 tests) :
   - `provision_createsSelfEmployee_inTenantSchema` — provision, vérifie via `TenantContext.setCurrentTenant(slug) + employeeRepository.findByUserId(owner.id).isPresent()`.
   - `provision_doesNotDuplicateSelfEmployee_onRetry` — appelle `provision` deux fois (cas idempotency). À évaluer si la provision elle-même est idempotente — sinon ce test devient `createSelfEmployee_isIdempotent` (déjà couvert plus haut).

3. **`CareServiceTests` (ou équivalent selon nom réel)** (3 tests) :
   - `create_attachesProSelfEmployee_whenAssignedListIsNull` — `req.assignedEmployeeIds = null` → care contient pro-self.
   - `create_attachesProSelfEmployee_whenAssignedListIsEmpty` — `req.assignedEmployeeIds = []` → care contient pro-self.
   - `create_respectsExplicitAssignedList` — `req.assignedEmployeeIds = [bob.id, alice.id]` → care contient exactement Bob et Alice, PAS pro-self.

4. **Migration V4 test** (à évaluer faisabilité) :
   - Approche A : test d'intégration `@SpringBootTest` qui démarre avec un tenant baseliné à V3, applique V4, vérifie l'état post-migration. Si l'infra Flyway test est en place dans `LegacyTenantBaselineTests` ou équivalent, ajouter un cas dédié.
   - Approche B (fallback) : test SQL manuel documenté dans la PR. Pas idéal mais acceptable si Approche A demande trop d'infra.

### Frontend (Karma + Jasmine)

5. **`booking-stepper.component.spec.ts`** — étendre l'existant avec 2 tests :
   - `does not render header back arrow on step 1` — au mount, `currentStep() === 1`, queryselector sur `[data-testid="stepper-back"]` → null.
   - `renders header back arrow on step 2 and step 3` — `component.currentStep.set(2)` puis `detectChanges()` → l'élément existe.
   - `clicking header back arrow calls goBack and decrements currentStep` — clic simulé sur `[data-testid="stepper-back"]`, vérifier que `currentStep()` repasse de 2 → 1.

6. **Pas de changement** à `step-care.component.spec.ts` — la logique mono-employé existante couvre déjà le scénario "pro fraîchement inscrit avec seulement l'employé pro-self" (le bloc employé sera masqué).

### Validation manuelle (smoke)

- Démarrer le backend avec un tenant frais (ou recréer un tenant via `/api/auth/register/pro`). Vérifier que :
  - L'employé pro-self apparaît dans `/pro/employees` avec le nom/email du compte.
  - Créer un soin sans toucher à la section employés du form → le soin a pro-self attaché.
  - Aller sur `/pro/bookings` → "Ajouter une réservation" → étape 1 affiche le soin, section employé invisible (1 seul employé = silencieux), Suivant actif.
  - Réserver jusqu'au bout → booking créé avec `employeeId = <pro-self.id>` (vérifier en DB ou via Network tab).
- Sur la modale stepper, valider visuellement :
  - Étape 1 : pas de flèche à gauche du header, juste le X à droite.
  - Étape 2 et 3 : flèche `arrow_back` à gauche, clic = revient à l'étape précédente.
  - Plus de bouton Retour en bas.

### Régression à ne pas casser

- Karma : 630/630 + 3 nouveaux tests = 633 attendus.
- Maven : 590/590 + 6-9 nouveaux tests selon couverture mapper-vs-service-vs-migration = 596-599 attendus.

## Fichiers modifiés/créés

| Fichier | Action |
|---|---|
| `backend/.../employee/app/EmployeeService.java` | Ajouter `createSelfEmployee(User owner)` |
| `backend/.../employee/repo/EmployeeRepository.java` | Ajouter `Optional<Employee> findByUserId(Long userId)` |
| `backend/.../tenant/app/TenantProvisioningService.java` | Injecter `EmployeeService`, appeler après save tenant |
| `backend/.../care/app/CareService.java` (ou équivalent — à trouver au plan) | Fallback pro-self quand liste employés vide/absente |
| `backend/src/main/resources/db/migration/tenant/V4__seed_self_employee.sql` | Nouveau fichier migration |
| `backend/src/test/.../EmployeeServiceTests.java` | +3 tests createSelfEmployee |
| `backend/src/test/.../TenantProvisioningServiceTests.java` | +1-2 tests |
| `backend/src/test/.../CareServiceTests.java` | +3 tests fallback employé |
| `frontend/.../booking-stepper/booking-stepper.component.ts` | Header refactor (flèche + suppression Retour bas) |
| `frontend/.../booking-stepper/booking-stepper.component.spec.ts` | +3 tests UI flèche |

## Edge cases

- **Tenant DRAFT sans Care** : la V4 crée seulement l'employé, pas de soin à lier. OK.
- **Tenant déjà avec employés mais aucun lié au owner** (ex. pro a créé des employés sans s'inclure) : la V4 crée pro-self en plus. Le pro aura alors un employé en plus dans sa liste, à lui de le désactiver s'il ne veut pas se présenter comme praticien.
- **User renommé après création du tenant** : l'employé pro-self conserve son `name` de la création. Pas de sync automatique. (Au pro de l'éditer dans `/pro/employees`. Acceptable.)
- **Care créé avec liste explicite incluant le pro-self** : pas de duplication (Set vs List).
- **Suppression de l'employé pro-self par le pro** : possible aujourd'hui via `/pro/employees`. Hors scope de cette PR — comportement actuel préservé. À régler plus tard si on veut empêcher la suppression du dernier employé d'un tenant.

## Hors scope explicite

- Notion de plan/subscription (Solo / Team / …) côté backend ou frontend
- Masquage UI de `/pro/employees` selon le plan
- Édition du téléphone de l'employé pro-self (le pro le fera via la page employés existante)
- Synchro automatique `User.name → Employee.name` quand le pro change son nom
- Empêcher la suppression du dernier employé d'un tenant

## Effort estimé

- Backend : 2-3h (service + provisioning hook + fallback + migration + tests)
- Frontend : 30 min (refactor header) + 30 min tests
- Total : **~3-4h**
