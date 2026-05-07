# Pro Onboarding Wizard — Design

**Date** : 2026-05-07
**Statut** : Draft, en attente de validation user

## Contexte et problème

Le tutoriel pro actuel (`OnboardingIndicatorComponent` + sheet mobile + stepper PC) présente trois défauts identifiés par le pro de référence :

1. **Étapes cochées sans action.** À l'inscription, `TenantProvisioningService` initialise `tenant.name = owner.getName()` (ligne 39). Du coup l'étape "Renseigne le nom de ton salon" est `done = true` dès le premier login, alors que le pro n'a rien fait.
2. **Pas de guidage à l'arrivée sur la page cible.** Quand le pro clique sur une étape, il atterrit sur `/pro/salon` (ou `/pro/cares`, `/pro/planning`) avec toute la page éditable, et seul un focus-pulse de 2,4 s sur le bon champ. Il ne sait pas vraiment quoi faire.
3. **Pas de modale au clic sur Publier en cas d'incomplétude.** Le back renvoie `422 + { message, missing[] }` mais le front ne fait rien d'autre que `setError`.

Au-delà du fix, le pro veut un parcours plus large que les 3 conditions techniques actuelles : nom + soin + horaires ne suffisent pas pour un salon présentable. Il faut aussi catégorie, coordonnées et logo.

## Décision

On remplace le bandeau d'onboarding comme parcours principal par un **wizard dédié** à `/pro/onboarding`, étape par étape. Le bandeau existant reste comme porte de retour pour les pros qui ont sorti du wizard.

Le wizard est **obligatoire au premier login** : tant que le tenant est `DRAFT` et `canPublish === false`, on redirige automatiquement le pro sur `/pro/onboarding` à toute entrée dans `/pro/*`. Une porte de sortie ("Je préfère explorer le tableau de bord") set un flag de session qui désactive la redirection.

Toutes les étapes du wizard sont **bloquantes pour la publication**. On élargit donc la condition `canPublish` côté back pour inclure `hasCategory`, `hasContact` et `hasLogo` en plus des 3 actuelles.

## Périmètre

**Inclus**
- Nouvelle route `/pro/onboarding` + composant wizard + 7 composants d'étape.
- Élargissement de `TenantReadinessResponse` (3 nouveaux booléens) et de `canPublish`.
- Fix de provisioning : `tenant.name = null` à la création + migration Flyway pour rendre la colonne nullable.
- Redirection automatique des pros DRAFT non publiables vers `/pro/onboarding`.
- Composant `PublishMissingDialogComponent` qui s'ouvre depuis le dashboard quand `PUT /publish` répond 422.
- Bouton "Reprendre le tutoriel" dans le bandeau onboarding existant.

**Exclu (V1)**
- Photos additionnelles au-delà du logo (la photo de couverture / `heroImagePath` reste optionnelle, modifiable depuis `/pro/salon`).
- Refonte des pages `/pro/salon`, `/pro/cares`, `/pro/planning`. Elles continuent de fonctionner pour les pros sortis du wizard ou en post-publication.
- Wizard mobile dédié : on fait le même composant responsive. Layout single-column sur mobile, ce n'est pas une refonte mobile spécifique.
- Reprise post-DELETE / SUSPENDED : non couvert, ces statuts ne déclenchent pas le wizard.

## Architecture

### Front

**Route et composant principal**
- Nouvelle route `/pro/onboarding` (auth pro déjà en place via le shell existant).
- `ProOnboardingWizardComponent` (page) : conteneur, gère :
  - L'étape courante via `signal<WizardStepKey>()`, synchronisée avec un fragment d'URL (`#name`, `#contact`, etc.) pour back/forward navigation.
  - Le calcul de l'étape de démarrage (première non `done` selon `readiness`).
  - La barre de progression et la transition entre étapes.

**Étapes (8 écrans : welcome + 7 actions)**

| Clé             | Composant                       | Champ(s) persistés sur le tenant       |
|-----------------|---------------------------------|----------------------------------------|
| `welcome`       | `WelcomeStepComponent`          | aucun                                  |
| `name`          | `NameStepComponent`             | `name`                                 |
| `contact`       | `ContactStepComponent`          | `addressStreet`, `addressPostalCode`, `addressCity`, `addressCountry`, `phone`, `contactEmail` |
| `logo`          | `LogoStepComponent`             | `logoPath` (via endpoint upload)       |
| `categories`    | `CategoriesStepComponent`       | `categorySlugs`                        |
| `cares`         | `CaresStepComponent`            | crée 1+ `Care` actif (template ou manuel) |
| `openingHours`  | `OpeningHoursStepComponent`     | crée 1+ `OpeningHour`                  |
| `publish`       | `PublishStepComponent`          | déclenche `PUT /publish`               |

Chaque composant d'étape :
- Est standalone, reçoit `tenant` en input, émet `(completed)` quand l'API confirme la sauvegarde.
- Charge ses données initiales depuis le tenant (pré-remplissage à la reprise).
- Désactive son CTA "Suivant" tant que sa validation locale n'est pas remplie.

**Composant partagé**
- `WizardProgressBarComponent` : barre 7 segments, segment actif rose, segments faits opaques, à venir transparents. Cliquable sur les étapes déjà `done` pour permettre la modification.

**Redirection automatique**
- Effet placé dans `pro-shell.component.ts`.
- Conditions cumulatives :
  - `readiness.status === 'DRAFT'`
  - `readiness.canPublish === false`
  - `sessionStorage.getItem('pf_skipOnboarding') !== '1'`
  - URL ≠ `/pro/onboarding`
- Si toutes vraies → `router.navigate(['/pro/onboarding'])`.
- Le flag `pf_skipOnboarding` est posé par le bouton "Sortir du tuto" et consommé (supprimé) par le bouton "Reprendre le tutoriel" du bandeau dashboard.

**PublishMissingDialogComponent**
- Standalone, ouvert depuis `pro-dashboard` quand `PUT /publish` répond 422.
- Le store `DashboardStore` ajoute un état `publishMissing: string[]` peuplé depuis le body 422.
- Un `effect()` dans `pro-dashboard` détecte `publishMissing.length > 0` et ouvre le dialog.
- Le dialog liste chaque clé manquante (`name`, `hasCategory`, `hasContact`, `hasLogo`, `hasActiveCare`, `hasOpeningHours`) avec :
  - icône Material
  - titre court (`pro.dashboard.checklist.{key}`)
  - description (`pro.dashboard.checklist.{key}Desc`)
  - bouton "Y aller" qui ferme le dialog et navigue vers `/pro/onboarding#{stepKey}`.
- Au close : `patchState(store, { publishMissing: [] })`.

### Back

**Élargissement de `TenantReadinessResponse`**
```java
record TenantReadinessResponse(
  String slug,
  boolean name,
  boolean hasCategory,
  boolean hasContact,     // NEW
  boolean hasLogo,        // NEW
  boolean hasActiveCare,
  boolean hasOpeningHours,
  boolean canPublish,
  String status,
  boolean employeesEnabled,
  Integer annualLeaveDays
)
```

**Calcul dans `TenantReadinessService`**
- `name` : `tenant.getName() != null && !tenant.getName().isBlank()`
- `hasCategory` : `tenant.getCategorySlugs() != null && !tenant.getCategorySlugs().isBlank()` (le champ existe déjà sur l'entité)
- `hasContact` : `(phone non blank OR contactEmail non blank) AND addressStreet non blank AND addressPostalCode non blank AND addressCity non blank AND addressCountry non blank`
- `hasLogo` : `tenant.getLogoPath() != null && !tenant.getLogoPath().isBlank()`
- `hasActiveCare` et `hasOpeningHours` : inchangés (déjà tenant-scopés via `TenantContext` + multi-schema)
- `canPublish` : `name && hasCategory && hasContact && hasLogo && hasActiveCare && hasOpeningHours`

`getMissingConditions(tenant)` retourne ces 6 clés dans l'ordre du wizard (`name`, `hasContact`, `hasLogo`, `hasCategory`, `hasActiveCare`, `hasOpeningHours`). L'ordre permet au front de mapper directement clé → étape.

**Provisioning fix**
- `TenantProvisioningService.provision()` : `.name(null)` au lieu de `.name(owner.getName())`.
- Migration Flyway shared `V<next>__tenant_name_nullable.sql` (numéro à déterminer à l'écriture du plan ; la table TENANTS vit dans le schéma applicatif partagé) :
  ```sql
  ALTER TABLE TENANTS MODIFY (name NULL);
  ```
- Vérifier le H2 path dans `TenantSchemaManager.createTenantTablesH2` : si la table TENANTS y est dupliquée, ajuster aussi.

### Data flow par étape

1. Utilisateur saisit / sélectionne dans le composant d'étape.
2. Sur "Suivant" : appel HTTP au endpoint existant (PUT tenant, POST cares, etc.).
3. Au succès : `DashboardStore.loadReadiness()` → recharge `readiness` depuis `/api/pro/tenant/readiness`.
4. Le wizard observe `readiness` et passe à l'étape suivante non `done`.
5. Si l'étape n'est toujours pas `done` après recharge (ex : back a accepté mais readiness reste fausse pour une raison), un toast d'erreur apparaît et le pro reste sur l'étape.

## États d'erreur et edge cases

- **5xx réseau** : snackbar "Impossible d'enregistrer pour l'instant", CTA "Suivant" reste actif pour retry.
- **422 validation back** : message du back affiché sous le champ pertinent ; sinon snackbar.
- **`readiness === null`** : skeleton loading, déclenche `loadReadiness()`, pas d'écran décisionnel.
- **Toutes étapes déjà `done`** (pro a tout rempli ailleurs) : saut direct à `publish`.
- **Tenant déjà `ACTIVE`** : redirection vers `/pro/dashboard` + snackbar "Votre salon est déjà en ligne."
- **Reprise après refresh** : étape de démarrage = première non `done` ; les étapes saisies en cours mais non soumises sont perdues (acceptable).
- **Sortie via "Sortir du tuto"** : dialog de confirmation, set `pf_skipOnboarding=1` en sessionStorage (durée session, pas localStorage), redirige dashboard. Le bandeau existant reste affiché et propose "Reprendre le tutoriel" qui supprime le flag et renvoie sur `/pro/onboarding`.
- **SSR** : la route est désactivée pour SSR via `data: { ssr: false }` ou `isPlatformBrowser` dans les composants qui font des uploads.

## Tests

**Front (Karma/Jasmine)**
- Spec par composant d'étape : rendu, validation locale, émission `(completed)`.
- Spec `ProOnboardingWizardComponent` :
  - étape de démarrage = première non `done`
  - navigation au succès d'une étape
  - redirection si `status === 'ACTIVE'`
  - sortie via "Sortir du tuto" set le flag et redirige
- Spec `PublishMissingDialogComponent` : rendu de la liste, click "Y aller" navigue vers `/pro/onboarding#{key}`.
- Spec `pro-shell` : redirection automatique selon les 4 conditions.
- Mise à jour de `pro-dashboard.component.spec.ts` : intercept 422 → ouverture du dialog.

**Back (JUnit + Spring Boot Test)**
- `TenantReadinessServiceTests` : nouveaux cas pour `hasContact`, `hasLogo`, `hasCategory` (tenant-scopé).
- `TenantProvisioningServiceTests` : nouvelle assertion `tenant.getName() == null` à la création.
- `TenantControllerTests` (`@WebMvcTest`) : `PUT /publish` retourne 422 + `missing` dans l'ordre `[name, hasContact, hasLogo, hasCategory, hasActiveCare, hasOpeningHours]`.
- Migration Flyway testée via le pipeline existant.

## Internationalisation

Toutes les nouvelles clés doivent être ajoutées en FR et EN :
- `pro.onboarding.wizard.welcome.*`, `pro.onboarding.wizard.name.*`, `pro.onboarding.wizard.contact.*`, etc. (un bloc par étape)
- `pro.onboarding.wizard.exit.confirmTitle`, `confirmBody`
- `pro.onboarding.wizard.publish.success`
- `pro.dashboard.checklist.hasContact`, `hasContactDesc`, `hasLogo`, `hasLogoDesc` (les clés `hasCategory` / `hasActiveCare` / `hasOpeningHours` existent déjà ou sont à compléter)
- `pro.dashboard.publishMissingDialog.title`, `body`, `goTo`

Les clés sont nommées par feature et hiérarchiques, conformes à `CLAUDE.md`.

## Décomposition en PRs (indicative)

1. **PR Back** : élargissement `TenantReadinessResponse`, fix provisioning + migration Flyway, tests.
2. **PR Front Wizard** : route, composants d'étape, progress bar, redirection shell, sortie + flag.
3. **PR Front Publish dialog** : `PublishMissingDialogComponent`, intégration dans `pro-dashboard`, store update.

L'ordre PR1 → PR2 → PR3 permet de tester le back isolément, puis le wizard sans la modale, puis la modale qui dépend du back élargi.
