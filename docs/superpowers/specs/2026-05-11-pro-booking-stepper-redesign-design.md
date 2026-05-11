# Pro booking stepper — sélection employé + taille modale stable

**Date :** 2026-05-11
**Auteur :** Claude (brainstorm validé Gustavo)
**Scope :** `/pro/bookings` → bouton "Ajouter une réservation" → `BookingStepperComponent`.
**Hors scope :** mobile (<768px) inchangé, autres modales pro, refonte de step-datetime / step-client (intégration seulement).

## Contexte

Le `BookingStepperComponent` (`features/bookings/components/booking-stepper/`) est la modale ouverte depuis `/pro/bookings` pour qu'un pro crée une réservation au nom d'un client. Elle a 3 étapes :

1. **Step 1 Care** — choix du soin
2. **Step 2 Datetime** — date + créneau horaire
3. **Step 3 Client** — sélection ou création du client final

Deux problèmes UX identifiés :

1. **Pas de sélection d'employé.** `StepCareComponent.onNext()` émet `{ careId, employeeId: 0 }` en dur (cf. `step-care.component.ts:132`), et `BookingStepperComponent.confirmBooking()` n'inclut pas du tout `employeeId` dans le payload `CreateCareBookingRequest`. Conséquence : le pro ne peut pas désigner quel employé exécute le soin ; le backend reçoit `employeeId = 0` ou `undefined` selon le chemin, et applique vraisemblablement une affectation par défaut côté serveur.
2. **Hauteur de modale instable.** `:host { max-height: 80vh }` est la seule contrainte verticale. Chaque étape a une hauteur de contenu différente (step 1 ~300px, step 2 ~480px avec datepicker, step 3 ~600px avec form create-client), donc la modale grandit et rétrécit visuellement à chaque transition, ce qui est désagréable côté PC.

## Décisions de design

| # | Décision | Pourquoi |
|---|---|---|
| D1 | La sélection employé est **fusionnée dans Step 1**, après le choix du soin. On reste à 3 étapes. | Validé en brainstorm. Le couple `soin + employé` conditionne les créneaux du Step 2 ; rapprocher les deux décisions évite un aller-retour. |
| D2 | Si **un seul employé** peut effectuer le soin choisi, la section employé est entièrement masquée et l'employé est pré-sélectionné silencieusement. | UX minimaliste pour les petits salons (cas dominant). |
| D3 | Si **>1 employé** : mini-cards verticales (avatar + nom), avec une **carte spéciale "Premier dispo" en tête, pré-sélectionnée**. | Liste verticale plus lisible que des chips pour >5 employés. "Premier dispo" évite la friction quand le pro n'a pas de préférence. |
| D4 | La taille de la modale est figée **uniquement à partir de 768px** : largeur 480px, `min-height: 560px`, `max-height: 85vh`. Le scroll des étapes est interne à `.step-content`. | Mobile inchangé (cf. mémoire `feedback_modals_mobile_untouched.md`). Tablette + PC bénéficient de la stabilité visuelle. |
| D5 | `employeeId` devient `number \| null` : `null` = "premier dispo" (le backend choisit). Un nombre = employé désigné. | Sémantique claire ; évite le sentinel `0`. |

## Architecture cible

### Layout `BookingStepperComponent` (≥768px)

```
┌────────────────────────────────────┐
│ sheet-handle + header (sticky)     │  ~52px
│ [×] Confirmer la réservation  1/3  │
├────────────────────────────────────┤
│ progress bar (sticky)              │  3px
├────────────────────────────────────┤
│ .step-content                      │
│   flex: 1 1 auto                   │  ≥505px
│   overflow-y: auto                 │  (scroll interne si besoin)
│   min-height: 0                    │
│                                    │
│   <app-step-care | -datetime |     │
│    -client>                        │
│                                    │
├────────────────────────────────────┤
│ btn-back (étapes 2+, sticky)       │  ~40px
└────────────────────────────────────┘

Conteneur : width 480px, min-height 560px, max-height 85vh
```

CSS-clé sur `:host` du stepper :

```scss
:host {
  display: flex;
  flex-direction: column;
  background: var(--pf-paper);
  /* Mobile : comportement actuel inchangé. */
  max-height: 80vh;
  overflow-y: auto;
}

@media (min-width: 768px) {
  :host {
    width: 480px;
    min-height: 560px;
    max-height: 85vh;
    overflow: hidden; /* le scroll passe à .step-content */
  }
  .step-content {
    flex: 1 1 auto;
    min-height: 0;
    overflow-y: auto;
  }
}
```

### Step 1 (refonte) — `StepCareComponent`

Layout en deux blocs verticaux dans la même étape :

**Bloc 1 — Soin** (liste actuelle, légèrement compacted)
- Mêmes mini-cards qu'aujourd'hui
- `max-height: 240px` (au lieu de `50vh`) pour libérer la place du bloc 2

**Bloc 2 — Employé** (apparaît avec `@if (selectedCareId())`)
- Petit titre h4 : `booking.stepper.step1Employee` ("Avec qui ?")
- Cas `availableEmployees().length === 0` : message i18n `booking.employees.empty` (cas edge ; en théorie ne devrait pas arriver si le soin a été créé par un salon avec au moins un employé qualifié)
- Cas `availableEmployees().length === 1` : section **non rendue**, `selectedEmployeeId` set à l'unique employé en interne
- Cas `>1 employés` : liste verticale de mini-cards
  - 1re carte : "Premier dispo" (icône `groups`, sans avatar), `value: null`, **pré-sélectionnée**
  - Cartes suivantes : un par employé (avatar fallback initiales si pas d'image)
  - `max-height: 200px`, scroll interne au bloc

**Bouton Suivant**
- Désactivé tant que `selectedCareId()` est null
- Toujours actif dès qu'un soin est cliqué (un employé est toujours pré-sélectionné, fût-ce "premier dispo")

**Émission**
```ts
careSelected.emit({
  careId: this.selectedCareId()!,
  employeeId: this.selectedEmployeeId(), // null | number
});
```

### Step 2 / Step 3 — intégration

Pas de changement fonctionnel. Leur seul ajustement est qu'ils héritent maintenant de `.step-content` qui scrolle ; donc tout contenu dépassant la zone visible est scrollable de manière transparente. Le datepicker + grille de créneaux du Step 2, et le form de création du Step 3, deviennent stables visuellement.

### `BookingStepperComponent` — wiring

- Le signal `selectedEmployeeId: signal<number | null>` (déjà présent ligne 165, jamais utilisé) reçoit la valeur émise par step-care.
- `confirmBooking()` ajoute `employeeId: this.selectedEmployeeId() ?? undefined` à la requête.

### Modèle / Service

- `CreateCareBookingRequest` doit déjà accepter `employeeId?: number` (à vérifier dans `bookings.model.ts`).
- Endpoint employés pour le pro : **point à confirmer dans le plan**. Hypothèses :
  - Si `GET /api/pro/employees?careId=…` existe → utiliser
  - Sinon, ajouter une méthode dans `BookingsService` qui appelle l'endpoint approprié. L'endpoint public `GET /api/salon/{slug}/employees?careId=…` existe (cf. `PublicSalonController.listEmployeesForCare`) mais nécessite le slug du salon. Pour le pro, on peut récupérer le slug du tenant courant (déjà disponible côté frontend via le profil) **ou** ajouter un endpoint pro dédié.
  - **Décision à reporter au plan**, pas au spec.

### i18n (fr + en)

Nouvelles clés :
- `booking.stepper.step1Employee` — "Avec qui ?" / "With whom?"
- `booking.employees.anyAvailable` — "Premier employé disponible" / "First available employee"
- `booking.employees.empty` — "Aucun employé disponible pour ce soin" / "No employee available for this care"

## Composants modifiés

| Fichier | Changement |
|---|---|
| `features/bookings/components/booking-stepper/booking-stepper.component.ts` | CSS `:host` media query ≥768px ; wiring `selectedEmployeeId` ; passer `employeeId` à `create()` |
| `features/bookings/components/step-care/step-care.component.ts` | Refonte template (bloc Soin + bloc Employé) ; ajout signal `availableEmployees`, `selectedEmployeeId` ; appel HTTP pour récupérer employés ; logique mono-employé silencieuse |
| `features/bookings/services/bookings.service.ts` | Nouvelle méthode `getEmployeesForCare(careId)` (à choisir endpoint au plan) |
| `features/bookings/models/bookings.model.ts` | Vérifier `CreateCareBookingRequest.employeeId?: number` |
| `assets/i18n/fr.json`, `assets/i18n/en.json` | 3 clés ajoutées |
| `features/bookings/components/step-care/step-care.component.spec.ts` (nouveau ou augmenté) | Tests des 4 comportements (cf. plan de test) |
| `features/bookings/components/booking-stepper/booking-stepper.component.spec.ts` (nouveau si absent) | Test `employeeId` transmis au service |

## Plan de test

### Unit (Karma)

**`StepCareComponent`** :
1. Affiche le bloc Employé après sélection d'un soin
2. Pré-sélectionne "Premier dispo" (`selectedEmployeeId() === null`) quand >1 employés disponibles
3. Auto-sélectionne et masque le bloc si exactement 1 employé disponible
4. Émet `{ careId, employeeId: null }` quand on clique Suivant avec "Premier dispo" actif
5. Émet `{ careId, employeeId: 42 }` quand on clique sur l'employé 42 puis Suivant
6. Affiche le message vide si aucun employé n'est dispo (edge case)

**`BookingStepperComponent`** :
- Vérifie que `bookingsService.create` reçoit bien `employeeId` cohérent avec ce que step-care a émis

**Test de non-régression existant** :
- Le test "passes viewContainerRef so the dialog inherits DashboardStore from ProShellComponent" reste vert.

### Visuel (manuel)

- `/pro/bookings` sur Chrome ≥1024px → modale 480×min560/max85vh, ne change pas de taille entre les 3 étapes
- `/pro/bookings` sur mobile <768px (simulateur DevTools) → bottom-sheet plein écran inchangé
- Cas mono-employé : le bloc employé est invisible
- Cas multi-employés : "Premier dispo" est pré-sélectionné (encadré rose)

## Edge cases

- **Aucun employé** pour le soin : message empty, bouton Suivant désactivé. (Cas qui ne devrait pas arriver en pratique vu que la création de soin exige au moins un employé qualifié — à confirmer dans le plan.)
- **Changement de soin** après sélection d'un employé : le `selectedEmployeeId` est reset à `null` (= "Premier dispo" pré-sélectionné si applicable). On évite de garder un employé qui n'est pas qualifié pour le nouveau soin.
- **Backend indisponible** lors du chargement de la liste d'employés : afficher un spinner pendant la requête, fallback message d'erreur si échec ; le pro peut quand même choisir un soin mais Suivant est désactivé tant que la liste n'a pas chargé.

## Suivi post-implémentation

- Mémoire : ne pas créer d'entrée, c'est du code "live" ; la mémoire `feedback_modals_mobile_untouched.md` couvre déjà la règle "mobile pas touché".
- Backlog : retirer la mention "modale add-booking ne montre que le header" de `2026-05-11-priorities.md` (déjà fait par le fix précédent).
- À tester en E2E plus tard quand on aura un setup Cypress/Playwright fonctionnel (hors scope ici).

## Hors scope explicite

- Refonte des modales pro autres que le stepper (cf. mémoire `project_pending_modals_redesign.md` qui couvre la refonte globale ; ici on touche uniquement le stepper, pas les autres modales).
- Modification de step-datetime et step-client (pas de changement fonctionnel, seule l'intégration dans le nouveau cadre).
- Sélection multi-employés ou plage horaire flexible — fonctionnalités futures.
- Création d'employé depuis la modale — flux séparé.
