# Protection de l'employé pro-self (delete + désactivation)

**Date :** 2026-05-22
**Auteur :** Claude (brainstorm validé Gustavo)
**Statut :** Spec — en attente du plan d'implémentation
**Scope :** Backend (EmployeeService + DTO + MyEmployeeController) + Frontend (liste employés + modale détail + traductions)
**Hors scope :** Schéma DB (pas de migration), modèle de plan/tier (déjà géré par autres specs), prévention du delete du dernier employé actif d'un tenant multi-employés, modèle de calendrier par employé (voir spec multi-employee booking 2026-05-21).

---

## 1. Contexte

Depuis la PR `feat/default-self-employee` (mergée 2026-05-11, commit `fc4ec7e`), tout tenant possède automatiquement un employé "pro-self" lié au compte propriétaire (`employee.userId == tenant.ownerId`). Cet employé garantit que :
- le pro peut immédiatement créer une réservation depuis `/pro/bookings` sans avoir à créer manuellement un employé,
- un soin créé sans employé assigné est attaché par défaut au pro-self,
- l'invariant **« tout tenant a ≥ 1 employé actif »** est maintenu.

**Le problème actuel :** ni `EmployeeService.delete(id)` ni `EmployeeService.update(id, …)` ne protègent l'employé pro-self. Un pro peut donc :
1. Cliquer "Supprimer" dans `/pro/employees` sur sa propre fiche → tenant cassé (plus aucun bookable, plus aucun fallback côté soin).
2. Basculer le toggle `active=false` sur sa propre fiche → même résultat.
3. Bypasser l'UI via `curl -X DELETE /api/pro/employees/{ownerId}` → idem.

Ce risque est explicitement noté comme « hors scope, à régler plus tard » dans la spec précédente (ligne 269 de `2026-05-11-default-self-employee-design.md`). On y est.

**Note sur les congés :** le système `LeaveRequest` existant gère parfaitement les absences du pro-self (vacances, maladie). Désactiver `active=false` n'est PAS une alternative légitime — c'est un mécanisme de retrait du salon, pas d'absence temporaire.

---

## 2. Décisions de design

| # | Décision | Pourquoi |
|---|---|---|
| D1 | **Marqueur dynamique** : un employé est "pro-self" si `employee.userId == tenant.ownerId`. Pas de colonne IS_OWNER, pas de migration de schéma. | Source de vérité unique (`Tenant.ownerId`). Évite la désync. Coût négligeable (1 lookup mutualisé par requête de listing). |
| D2 | **Modifier oui, supprimer/désactiver non.** Le pro peut éditer nom, téléphone, soins assignés du pro-self. Il ne peut ni le supprimer (DELETE) ni le désactiver (`active=false`). | Garde l'invariant "≥1 employé actif" en permanence. Laisse l'autonomie d'édition (nom commercial, téléphone affiché client, soins pratiqués). |
| D3 | **Pas de notion d'absence/congé dans cette spec.** Les congés/RTT/maladie passent par `LeaveRequest` existant, qui fonctionne déjà pour pro-self. | Le besoin "pro indisponible aujourd'hui" est orthogonal à "pro retiré du salon". Pas de mélange de concepts. |
| D4 | **Backend renvoie 409 CONFLICT** avec code message stable pour le frontend (`SELF_EMPLOYEE_PROTECTED`). | Standard REST. Permet au frontend de mapper vers un toast i18n localisé. |
| D5 | **Champ DTO `isSelf: boolean`** ajouté à `EmployeeResponse` (calculé, non persisté). Pas modifié sur `EmployeeSlimResponse` (exposé publiquement, info privée). | Permet au frontend de conditionner badge + actions sans deuxième appel API. Ne fuite pas l'info en API publique. |
| D6 | **Frontend** : badge discret "Vous" à côté du nom dans la liste, bouton supprimer caché dans la modale détail, toggle `active` désactivé avec texte d'aide. | UX claire (pas de surprise via 409). Conserve la régression backend en filet de sécurité (curl direct). |
| D7 | **Edge case : `tenant.ownerId` introuvable** (tenant supprimé/incohérent) → on bloque le delete par défaut (fail-safe). | Si on ne peut pas vérifier l'invariant, on refuse plutôt que de risquer la corruption. Le pro contactera le support. |

---

## 3. Architecture

### 3.1 Backend — helper centralisé

Nouvelle méthode privée dans `EmployeeService` :

```java
private boolean isSelfEmployee(Employee employee) {
    String tenantSlug = TenantContext.requireActive();
    return applicationSchemaExecutor.call(() ->
        tenantRepository.findBySlug(tenantSlug)
            .map(t -> t.getOwnerId().equals(employee.getUserId()))
            .orElse(true)   // fail-safe: si tenant introuvable, on traite comme pro-self → bloque
    );
}
```

> Note : `.orElse(true)` est volontaire. Si `tenant.ownerId` est introuvable on est dans un état incohérent ; on refuse la suppression plutôt que de risquer la corruption.

### 3.2 Backend — blocage delete

Modification de `EmployeeService.delete(Long id)` :

```java
@Transactional
public void delete(Long id) {
    Employee employee = employeeRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + id));

    if (isSelfEmployee(employee)) {
        throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Cannot delete the salon owner's employee profile (SELF_EMPLOYEE_PROTECTED)");
    }

    employeeRepository.delete(employee);
}
```

### 3.3 Backend — blocage désactivation

Modification de `EmployeeService.update(Long id, UpdateEmployeeRequest req)` :

```java
@Transactional
public EmployeeResponse update(Long id, UpdateEmployeeRequest req) {
    Employee employee = employeeRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + id));

    if (req.active() != null && !req.active() && isSelfEmployee(employee)) {
        throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Cannot deactivate the salon owner's employee profile (SELF_EMPLOYEE_PROTECTED)");
    }

    // ... reste inchangé (name / phone / active / careIds) ...
}
```

Note : le check ne se déclenche que sur tentative *réelle* de désactivation (`req.active() == false`). Un `req.active() == true` ou `null` sur pro-self est un no-op silencieux.

### 3.4 Backend — DTO `EmployeeResponse`

```java
public record EmployeeResponse(
    Long id,
    Long userId,
    String name,
    String email,
    String phone,
    boolean active,
    List<CareRef> assignedCares,
    LocalDateTime createdAt,
    boolean isSelf   // NEW
) {
    public record CareRef(Long id, String name) {}
}
```

⚠️ **Effet de bord à la compilation** : tous les `new EmployeeResponse(...)` deviennent obligatoires-à-mettre-à-jour. Inventaire :
- `EmployeeService.toResponse(e)` — calcul effectif
- `MyEmployeeController.getMyProfile()` — construit à la main, à refactor

### 3.5 Backend — mutualisation du lookup `ownerId`

Pour éviter N appels `tenantRepository.findBySlug` dans `listAll()` :

```java
@Transactional(readOnly = true)
public List<EmployeeResponse> listAll() {
    Long ownerId = currentTenantOwnerId();  // 1 seul lookup
    return employeeRepository.findAll().stream()
            .map(e -> toResponse(e, ownerId))
            .toList();
}

private Long currentTenantOwnerId() {
    String slug = TenantContext.requireActive();
    return applicationSchemaExecutor.call(() ->
        tenantRepository.findBySlug(slug)
            .map(t -> t.getOwnerId())
            .orElse(null)
    );
}

private EmployeeResponse toResponse(Employee e, Long ownerId) {
    // ... map champs ...
    boolean isSelf = ownerId != null && ownerId.equals(e.getUserId());
    return new EmployeeResponse(..., isSelf);
}
```

Le `toResponse(Employee)` à 1 argument reste pour `get(id)` et `create(req)` (re-lookup ponctuel, OK car cas unitaires).

### 3.6 Backend — `MyEmployeeController.getMyProfile`

L'endpoint `/api/employee/me` est appelé par tout employé (owner OU non-owner). Le check `isSelf` doit s'appliquer ici aussi :

```java
@GetMapping
public EmployeeResponse getMyProfile(@AuthenticationPrincipal UserPrincipal principal) {
    Employee emp = resolveEmployee(principal.getId());
    Long ownerId = resolveTenantOwnerId();  // helper local
    boolean isSelf = ownerId != null && ownerId.equals(emp.getUserId());
    var cares = emp.getAssignedCares().stream()
            .map(c -> new EmployeeResponse.CareRef(c.getId(), c.getName())).toList();
    return new EmployeeResponse(
            emp.getId(), emp.getUserId(), emp.getName(), emp.getEmail(),
            emp.getPhone(), emp.isActive(), cares, emp.getCreatedAt(), isSelf
    );
}
```

### 3.7 Frontend — interface TypeScript

`frontend/src/app/features/employees/employees.model.ts` :

```typescript
export interface Employee {
  id: number;
  userId: number;
  name: string;
  email: string;
  phone: string | null;
  active: boolean;
  assignedCares: CareRef[];
  createdAt: string;
  isSelf: boolean;  // NEW
}
```

### 3.8 Frontend — liste employés (badge "Vous")

`employees.component.html`, dans la cellule Member :

```html
<div class="person-info">
  <span class="person-name">
    {{ employee.name }}
    @if (employee.isSelf) {
      <span class="self-badge" data-testid="self-badge">
        {{ 'pro.employees.youBadge' | transloco }}
      </span>
    }
  </span>
  <span class="person-meta">
    {{ 'pro.employees.memberSince' | transloco }} {{ formatJoinDate(employee.createdAt) }}
  </span>
</div>
```

SCSS pour `.self-badge` : chip discret (background `--mat-sys-secondary-container`, font 11px, padding 2px 8px, border-radius 12px). Suit la palette Material existante du projet.

### 3.9 Frontend — modale détail (cache delete + lock toggle)

`employee-detail.component.html` :

```html
<!-- Active toggle -->
<div class="toggle-row">
  <span class="toggle-label">{{ 'pro.employees.toggleActive' | transloco }}</span>
  <mat-slide-toggle
    [checked]="isActive()"
    [disabled]="employee.isSelf"
    (change)="onToggleActive($event.checked)"
    color="primary"
  ></mat-slide-toggle>
</div>
@if (employee.isSelf) {
  <p class="self-hint">{{ 'pro.employees.cannotDeactivateSelfHint' | transloco }}</p>
}

<!-- Actions -->
<mat-dialog-actions align="end">
  @if (!employee.isSelf) {
    <button mat-button color="warn" (click)="onDelete()" class="delete-btn">
      <mat-icon>delete</mat-icon>
      {{ 'actions.delete' | transloco }}
    </button>
    <span class="action-spacer"></span>
  }
  <button mat-button (click)="onCancel()">
    {{ 'common.cancel' | transloco }}
  </button>
  <button mat-raised-button color="primary" (click)="onSave()">
    {{ 'common.save' | transloco }}
  </button>
</mat-dialog-actions>
```

### 3.10 Frontend — gestion d'erreur 409

Le store `employees.store.ts` doit afficher un toast i18n quand le backend renvoie 409 sur delete/update. Si l'infra de gestion d'erreur globale du projet (intercepteur HTTP + snackbar) gère déjà les 409 avec le body comme message, rien à faire. Sinon, ajouter un `catchError` dans les `rxMethod` `deleteEmployee` et `updateEmployee` qui mappe vers `errors.employee.cannotDeleteSelf` / `errors.employee.cannotDeactivateSelf`.

### 3.11 Traductions

`fr.json` :
```json
{
  "pro.employees.youBadge": "Vous",
  "pro.employees.cannotDeactivateSelfHint": "Vous ne pouvez pas désactiver votre propre fiche. Pour une absence ponctuelle, utilisez les congés.",
  "errors.employee.cannotDeleteSelf": "Vous ne pouvez pas supprimer votre propre fiche employé.",
  "errors.employee.cannotDeactivateSelf": "Vous ne pouvez pas désactiver votre propre fiche employé."
}
```

`en.json` :
```json
{
  "pro.employees.youBadge": "You",
  "pro.employees.cannotDeactivateSelfHint": "You cannot deactivate your own profile. For a temporary absence, use leaves.",
  "errors.employee.cannotDeleteSelf": "You cannot delete your own employee profile.",
  "errors.employee.cannotDeactivateSelf": "You cannot deactivate your own employee profile."
}
```

---

## 4. Plan de test

### 4.1 Backend — `EmployeeServiceTests` (JUnit + Mockito)

| # | Test | Vérifie |
|---|---|---|
| T1 | `delete_throwsConflict_whenEmployeeIsProSelf` | Setup employee avec `userId == tenant.ownerId` → `delete()` lève `ResponseStatusException(CONFLICT)`. `employeeRepository.delete()` jamais appelé. |
| T2 | `delete_succeeds_forNonSelfEmployee` | Setup employee avec `userId != tenant.ownerId` → delete passe normalement. |
| T3 | `delete_throwsConflict_whenTenantNotFound` | `tenantRepository.findBySlug` renvoie empty → fail-safe = bloqué. |
| T4 | `update_throwsConflict_whenDeactivatingProSelf` | `isSelf` + `req.active=false` → 409. |
| T5 | `update_succeeds_whenKeepingProSelfActive` | `isSelf` + `req.active=true` → OK. |
| T6 | `update_succeeds_changingNameOrPhoneOfProSelf` | `isSelf` + uniquement `name`/`phone`/`careIds` (active=null) → OK. |
| T7 | `update_allowsDeactivatingNonSelfEmployee` | Non-self + `active=false` → OK (régression). |
| T8 | `listAll_marksProSelfWithIsSelfTrue_othersFalse` | Liste de 2 employés → seul celui avec `userId==ownerId` a `isSelf=true`. |
| T9 | `get_marksProSelfWithIsSelfTrue` | Idem en single. |

### 4.2 Backend — `MyEmployeeController` (smoke via WebMvcTest)

| # | Test | Vérifie |
|---|---|---|
| T10 | `getMyProfile_returnsIsSelfTrue_forTenantOwner` | Auth comme owner → JSON `isSelf=true`. |
| T11 | `getMyProfile_returnsIsSelfFalse_forRegularEmployee` | Auth comme employé non-owner → JSON `isSelf=false`. |

### 4.3 Backend — `EmployeeController` MVC

| # | Test | Vérifie |
|---|---|---|
| T12 | `delete_returns409_whenServiceThrowsForProSelf` | Mock `service.delete()` throw `ResponseStatusException(CONFLICT)` → contrôleur renvoie 409 via `GlobalExceptionHandler`. |

### 4.4 Frontend — Karma/Jasmine

| # | Composant | Test | Vérifie |
|---|---|---|---|
| T13 | `employees.component.spec.ts` | `displays "You" badge for self-employee row` | Mount avec 2 employees dont 1 `isSelf=true` → `[data-testid="self-badge"]` présent uniquement sur cette ligne. |
| T14 | `employee-detail.component.spec.ts` | `hides delete button when employee.isSelf is true` | Data `isSelf=true` → `.delete-btn` absent du DOM. |
| T15 | `employee-detail.component.spec.ts` | `disables active toggle when employee.isSelf is true` | Data `isSelf=true` → `mat-slide-toggle` a l'attribut `disabled`, hint visible. |
| T16 | `employee-detail.component.spec.ts` | `shows delete and active toggle when isSelf is false` | Régression : non-self conserve l'UI actuelle. |

### 4.5 Manuel / Smoke

| # | Action | Attendu |
|---|---|---|
| S1 | Login pro → `/pro/employees` | Ligne pro-self avec badge "Vous" (ou "You" en anglais) |
| S2 | Cliquer sur la fiche pro-self → modale détail | Pas de bouton "Supprimer", toggle `active` désactivé, hint visible |
| S3 | Modifier nom + soins assignés du pro-self, sauvegarder | Save OK, données mises à jour côté DB |
| S4 | Créer un 2e employé, le supprimer | OK (régression non cassée) |
| S5 | `curl -X DELETE /api/pro/employees/{ownerId}` avec auth pro | Réponse HTTP 409 |
| S6 | `curl -X PUT /api/pro/employees/{ownerId}` body `{"active":false}` | Réponse HTTP 409 |
| S7 | Pro-self crée un `LeaveRequest` via UI ou API | OK (pas de blocage — voie légitime pour absence) |
| S8 | Login employé non-owner → `/employee/me` | JSON contient `isSelf: false` |

### 4.6 Régression à ne pas casser

- Karma : tous tests actuels verts + 4 nouveaux ≈ +4
- Maven : tous tests actuels verts + 12 nouveaux ≈ +12

---

## 5. Fichiers modifiés / créés

| Fichier | Action |
|---|---|
| `backend/.../employee/app/EmployeeService.java` | Ajouter `isSelfEmployee()`, `currentTenantOwnerId()`, surcharger `toResponse(e, ownerId)`, blocage delete + update |
| `backend/.../employee/web/dto/EmployeeResponse.java` | Ajouter champ `boolean isSelf` |
| `backend/.../employee/web/MyEmployeeController.java` | Refactor `getMyProfile()` pour calculer `isSelf` |
| `backend/.../employee/web/EmployeeController.java` | Aucun changement (le 409 remonte via `GlobalExceptionHandler`) |
| `backend/src/test/.../EmployeeServiceTests.java` | +9 tests (T1-T9) |
| `backend/src/test/.../employee/web/MyEmployeeControllerTests.java` | +2 tests (T10-T11) — fichier nouveau si inexistant |
| `backend/src/test/.../employee/web/EmployeeControllerTests.java` | +1 test (T12) |
| `frontend/.../features/employees/employees.model.ts` | Ajouter `isSelf: boolean` à `Employee` |
| `frontend/.../features/employees/employees.component.html` | Badge "Vous" conditionnel |
| `frontend/.../features/employees/employees.component.scss` | Styles `.self-badge` |
| `frontend/.../employees/modals/employee-detail/employee-detail.component.html` | Cache delete + disable toggle + hint |
| `frontend/.../employees/modals/employee-detail/employee-detail.component.scss` | Styles `.self-hint` |
| `frontend/.../employees/modals/employee-detail/employee-detail.component.spec.ts` | +3 tests (T14-T16) |
| `frontend/.../features/employees/employees.component.spec.ts` | +1 test (T13) |
| `frontend/src/assets/i18n/fr.json` | +4 clés |
| `frontend/src/assets/i18n/en.json` | +4 clés |

---

## 6. Edge cases

- **Tenant `ownerId` introuvable** : fail-safe = delete/désactivation bloqués (D7).
- **Pro renommé après création tenant** : pas d'impact (l'identité pro-self repose sur `userId`, pas sur `name`).
- **Curl direct sur DELETE** : backend bloque (S5). UI n'est qu'une couche de confort.
- **Deuxième tenant pour le même user** (cas multi-tenant si jamais on l'autorise) : l'employee est résolu via `TenantContext` actif, donc le check `ownerId` est scopé tenant courant. OK.
- **`active=true` envoyé sur pro-self** : no-op (déjà true). Aucun blocage. OK.
- **`isSelf` exposé en API publique** : non — `EmployeeSlimResponse` reste inchangé. Aucune fuite d'info privée vers les clients.

---

## 7. Hors scope explicite

- Empêcher le delete du *dernier employé actif* d'un tenant multi-employés (cas plus large, à traiter après l'arrivée de tenants réellement multi-employés).
- Synchronisation automatique `User.name → Employee.name` quand le pro change son nom.
- Modèle de calendrier par employé (voir spec `2026-05-21-tier-gating-and-multi-employee-booking-design.md`).
- Migration de schéma DB (volontairement évitée — voir D1).
- Édition du téléphone du pro-self via le profil utilisateur plutôt que via `/pro/employees` (chemin actuel conservé).

---

## 8. Effort estimé

- **Backend** : ~1h (helper + 2 checks + DTO + refactor MyEmployeeController + 12 tests)
- **Frontend** : ~45 min (interface + badge + conditions modale + traductions + 4 tests)
- **Total : ~1h45**
