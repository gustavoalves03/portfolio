# Refonte des modales PC — Design Doc

**Date :** 2026-05-09
**Statut :** Validé pour implémentation
**Scope :** Refonte UI des 18 modales existantes pour PC + tablette (≥768px). Mobile inchangé.

## 1. Contexte et objectif

Les pages PC du produit (`/pro/dashboard`, `/pro/salon`, `/pro/cares`, `/pro/planning`, `/pro/bookings`, `/pro/employees`, `/pro/settings`, `/pro/booking-history`, `/pro/clients/:id`, `/salon/:slug`) ont été refondues en mai 2026 avec une grammaire commune (panels plats, border 1px, radius 4px, accent rose Pretty Face, KPI strips, sticky savebars, max-width 1440-1760px).

Les **modales** sont restées sur la grammaire Material par défaut, ce qui crée une rupture visuelle forte quand le pro ouvre une modale depuis ces pages refondues. Cette spec définit la grammaire commune des modales PC et applique la transposition à chaque modale existante.

**Pas dans le scope :** mobile (bottom-sheet ≤767px déjà validé, intouchable), création de nouvelles modales (deux idées sorties — `approve/reject booking` — sont notées pour brainstorming dédié futur).

## 2. Décisions transverses

### 2.1 Cible

- **PC + tablette** uniquement, via `@media (min-width: 768px)`.
- **Mobile** (≤767px) : bottom-sheet et animation `sheet-slide-up` dans `frontend/src/styles.scss` restent **intacts**.

### 2.2 Tailles

3 presets, déclarés par chaque caller via `size`.

| Preset | Largeur | Marge bord | Usage |
|--------|---------|------------|-------|
| **S** | `min(480px, calc(100vw - 32px))` | 32px | Confirms, suppressions, formulaires 1–2 champs |
| **M** | `min(640px, calc(100vw - 48px))` | 48px | Formulaires standard (par défaut) |
| **L** | `min(860px, calc(100vw - 64px))` | 64px | Workflows multi-étapes (booking, fiche éditable) |

Hauteur max commune : `min(800px, calc(100vh - 80px))`. Scroll dans le body, header et footer ancrés.

### 2.3 Grammaire visuelle

| Élément | Spec |
|---------|------|
| Filet décoratif | 3px en haut, dégradé `#c66075 → #f5d0e0` (vire au rouge `#b3001b → #e08896` si `dangerous`) |
| Breadcrumb | Pastille rose 6px (rouge si `dangerous`) + Lvl1 uppercase gris (`#999`, letter-spacing 0.12em, 11px) + " / " (gris clair) + Lvl2 (`#666`, 12px) |
| Titre | Cormorant Garamond, 22px (S) / 26px (M/L), font-weight 500, letter-spacing -0.01em, italique pour les noms d'objet (`Modifier <em>Léa Martin</em>`) |
| Sous-titre | 13px, `#666`, peut contenir du HTML (gras sur mots-clés) |
| Field-rows | Grid 130px (label) / 1fr (input), padding 14px vertical, border-bottom `#f4f4f4`, dernière ligne sans border |
| Label required | Astérisque rose Pretty Face (`#c66075`) à droite du label |
| Bouton primaire | Rose Pretty Face `#c66075` (rouge `#b3001b` si `dangerous`), texte contextuel ("Créer la X" / "Enregistrer" / "Inviter" / "Publier") |
| Footer | Raccourci clavier ou bouton retour à gauche, cancel + primary à droite |
| Croix de fermeture | 18px gris `#999`, en haut-droite du header |

### 2.4 Tons et libellés

- Titres en **question** quand l'action n'est pas évidente : *"Supprimer X ?"*, *"Comment s'est passée ta visite ?"*
- Bouton primaire **contextuel** : pas de "Sauvegarder" générique. Préférer *"Créer la praticienne"*, *"Enregistrer"*, *"Inviter"*, *"Publier"*, *"Confirmer la réservation"*.
- Bouton cancel **adouci** quand pertinent : *"Plus tard"* au lieu de *"Annuler"* dans les flows où l'utilisateur peut revenir (auth-modal, rate-visit, publish-missing).
- Sous-titres **expliquent l'effet réel** : *"Léa sera notifiée et l'absence apparaîtra dans le planning"*, *"Visible sur la page publique du salon"*.

## 3. Wrapper partagé `<modal-form>`

Tout passe par ce wrapper. Le code existant à l'adresse `frontend/src/app/shared/uis/modal-form/modal-form.ts` est l'unique point de modification d'infra ; les modales métier deviennent toutes des callers.

### 3.1 Inputs

| Nom | Type | Défaut | Description |
|-----|------|--------|-------------|
| `title` ★ | `string` | — | Titre serif. Peut contenir du HTML (italique pour les noms). |
| `size` 🆕 | `'s' \| 'm' \| 'l'` | `'m'` | Préset de largeur. |
| `breadcrumbParent` 🆕 | `string` | — | Chemin parent ("Catalogue / Soins"). |
| `subtitle` 🆕 | `string` | — | Sous-titre 13px gris, HTML autorisé. Optionnel. |
| `dangerous` 🆕 | `boolean` | `false` | Si `true` : filet rouge, pastille rouge, bouton primaire rouge. Fail-safe. |
| `kbdHint` 🆕 | `string` | — | Raccourci clavier dans le footer ("⌘S pour enregistrer"). Caché en taille S. |
| `backLabel` 🆕 | `string` | — | Si fourni : bouton "‹ Retour" à gauche du footer (workflows L étape ≥ 2). |
| `saveLabel` | `string` | `'Enregistrer'` | Texte du bouton primaire. |
| `cancelLabel` | `string` | `'Annuler'` | Texte du bouton de gauche. |
| `saveDisabled` | `boolean` | `false` | Désactive le bouton primaire. |
| `showCloseButton` | `boolean` | `true` | Affiche la croix. |
| `hideSaveButton` | `boolean` | `false` | Cache le bouton primaire (mode view-only). |

### 3.2 Outputs

| Nom | Type | Description |
|-----|------|-------------|
| `save` | `EventEmitter<void>` | Clic sur le bouton primaire. |
| `cancel` | `EventEmitter<void>` | Clic sur cancel ou la croix. |
| `back` 🆕 | `EventEmitter<void>` | Clic sur "‹ Retour" (si `backLabel` fourni). |

### 3.3 Slots ng-content

| Slot | Description |
|------|-------------|
| (default) | Contenu principal du body. |
| `[slot="stepper"]` 🆕 | Stepper inséré entre header et body (workflows L). |
| `[slot="aside"]` 🆕 | Colonne gauche pour layout L 2 colonnes. Si fourni, body devient grid 280px / 1fr. |
| `[slot="footer-left"]` 🆕 | Override custom du footer-left (ex. *"Supprimer Léa"* dans employee-detail). Remplace `kbdHint` et `backLabel`. |

### 3.4 Inputs supprimés (legacy)

| Nom | Raison |
|-----|--------|
| `icon` | Plus d'icône Material géante à gauche du titre. |
| `iconColor` | Idem. |
| `saveIcon` | Bouton primaire purement textuel. |
| `cancelIcon` | Idem. |

## 4. Composants annexes à créer

| Composant | Rôle | Utilisé par |
|-----------|------|-------------|
| `<modal-stepper>` | Stepper horizontal numéroté (1 → 2 → 3) avec labels. Inputs : `steps: string[]`, `current: number`. Click sur step déjà fait = navigation arrière. | booking-stepper |
| `<modal-tabs>` | Onglets sobres (souligné rose sur l'actif). Compteur en pastille optionnel. | create-care, create-post, auth-modal, booking-stepper step 3 |
| `<app-care-row>` | Ligne care réutilisable (nom + cat-pill + durée + prix). | booking-stepper step 1, reassign-category |
| `<app-employee-picker>` | Liste cards verticaux praticiennes (avatar + nom + count optionnel). Single-select. | booking-dialog vitrine |
| `<app-cares-multi-picker>` | Multi-select à recherche + select all + compteur. | create-employee, employee-detail |
| `<app-mini-calendar>` | Mini-calendrier 240px avec jours fermés / fériés / dispo. | booking-dialog vitrine |

## 5. Modales (18)

Toutes les modales ci-dessous sont **des callers** de `<modal-form>`. Le tableau résume taille + breadcrumb + particularités. Détails design dans les mockups `.superpowers/brainstorm/14863-1778288487/content/m1` à `m18`.

> **Note sur la numérotation** : les `#` correspondent à l'ordre de traitement du brainstorming (m1 à m18), pas à un classement par bloc. Les blocs ci-dessous regroupent par taille et typologie. Les numéros peuvent donc paraître non séquentiels au sein d'un bloc (ex. `create-care` est #7 dans le bloc formulaires standards, intercalé entre les blocs).

### Bloc confirms (taille S)

| # | Modale | Breadcrumb | dangerous | Particularités |
|---|--------|------------|-----------|----------------|
| 1 | `delete-care` | Catalogue / Soins | ✅ | Titre devient question. Bouton "Supprimer définitivement". Warn explicite "RDV planifiés non affectés". |
| 2 | `no-show-confirm` | Suivi / Rendez-vous | ✅ | Titre reformulé "Marquer ce rendez-vous comme absent ?". Encart récap 2 colonnes (Soin / Date). |
| 3 | `confirm-dialog` (générique) | passé via `data` | optionnel | Wrapper réutilisable. Caller passe `breadcrumbParent` et `dangerous` (défaut `false` = fail-safe). |

### Bloc auth (taille M)

| # | Modale | Breadcrumb | Particularités |
|---|--------|------------|----------------|
| 4 | `login-modal` | Compte | Inputs natifs. Bouton "Continuer avec Google". TODO commentaires pour "Mot de passe oublié" + "Se souvenir de moi" (back pas prêt — voir mémoire `project_pending_auth_features`). |
| 5 | `auth-modal` | Réservation / Connexion requise | Onglets sobres login/register. Sous-titre nomme le salon. Bandeau rosé "Ta sélection est conservée". Cancel = "Plus tard" (conserve la sélection booking — voir mémoire `project_pending_auth_modal_context`). |

### Bloc formulaires courts (taille M)

| # | Modale | Breadcrumb | Particularités |
|---|--------|------------|----------------|
| 6 | `create-category` | Catalogue / Catégories | 2 field-row natifs (nom + description). Mode édition : titre nomme la catégorie en italique serif, sous-titre affiche compte de soins ("7 soins associés…"). |
| 8 | `reassign-category` | Catalogue / Catégories | dangerous=true. Visualisation "from → to" en haut. Liste scrollable des soins concernés (cachée une fois destination choisie). DTO enrichi avec `careList: {name, duration, price}[]`. |
| 9 | `review-leave-dialog` | Équipe / Demandes de congé | Une seule modale, 2 modes : APPROVED (rose) / REJECTED (rouge). Récap demande (type/dates/motif). Label textarea contextuel. DTO enrichi avec `type/startDate/endDate/reason`. |
| 10 | `rate-visit-dialog` | Mon évolution / Visites | Care-card en haut (thumb + nom + salon + date). Étoiles 32px en rose Pretty Face (pas jaune). Libellé dynamique sous étoiles. Cancel = "Plus tard". DTO enrichi avec `salonName + visitDate`. |

### Bloc formulaires standards (taille M)

| # | Modale | Breadcrumb | Particularités |
|---|--------|------------|----------------|
| 7 | `create-care` | Catalogue / Soins | 2 onglets (Informations + Images avec compteur). Prix + Durée sur la même ligne (suffixes € / min). Onglet Images : grille 4 cols 1:1, image cover encerclée rose, drag-to-reorder, slot "+", **limite 5 images** (voir mémoire `project_care_images_limit`). État empty soigné. État limite atteinte (5/5) avec bandeau d'info. |
| 11 | `create-user` | Administration / Utilisateurs | 2 field-row (nom + email). Callout invitation par mail. Bouton "Inviter". Mode édition : sous-titre meta. Sans promesse "24h" (back pas confirmé). |
| 12 | `create-employee` | Équipe / Praticiennes | 4 field-row : nom + (email · téléphone pair-cols) + password + cares-multi-picker. Vocabulaire "Praticienne". Pas d'invitation par mail (pro tape password). |
| 13 | `create-post` | Vitrine / Posts | 3 chips de type (Avant/Après · Photo · Carrousel) avec micro-description. Drop-zones rosées. Caption avec compteur 0/500. Nouveau champ "Soin associé" (select optionnel). Hint "Si choisi, bouton Réserver apparaîtra". Limite carrousel : 10 (statu quo). |
| 14 | `publish-missing-dialog` | Tableau de bord / Vitrine | Titre encourageant ("Plus que quelques étapes !"). Barre de progression dégradé rose + compte "X sur Y étapes". Checklist complète (faits + manques) — items faits en barré opacité 0.65. CTA "Aller →" par item. Footer minimaliste "Plus tard". DTO enrichi avec `checklist: {key, status: 'done'\|'missing'}[]`. |

### Bloc workflows (taille L)

| # | Modale | Breadcrumb | Particularités |
|---|--------|------------|----------------|
| 15 | `booking-stepper` (refonte de `BookingStepperComponent`) | Réservations / Nouvelle | Workflow 3 étapes : Soin → Horaire → Cliente. Layout L 2 colonnes (récap fixe à gauche + step actif à droite). Stepper. Étape 2 : sélecteur "Toutes praticiennes — N dispos" + 7 jours à gauche + grille créneaux. Étape 3 : onglets "Cliente existante" / "Nouvelle cliente". Total estimé dans le récap. |
| 16 | `employee-detail` | Équipe / Praticiennes | **Taille M finalement** (pas L — densité ne justifie pas 2 colonnes). Titre "Modifier *Léa Martin*". Bandeau identité allégé (avatar + nom + méta sociale "Inscrite il y a 8 mois · 247 RDV effectués" + pastille statut). 3 sections labellées (IDENTITÉ, STATUT, SOINS EFFECTUÉS). **Identité éditable** (nom + email + téléphone). Toggle visibilité avec aide contextuelle. Multi-picker soins. "Supprimer Léa" en lien rouge à gauche du footer (clic = ouvre confirm S). DTO enrichi avec `createdAt + bookingsCount`. |
| 17 | `booking-dialog` (vitrine /salon) | Vitrine / Réservation | Layout L 2 colonnes. **Pas de stepper** (une seule étape : date+heure). Titre "Réserver chez *<salon>*". Sous-titre "paiement sur place après le soin". Aside : care-card riche + employee-picker ("Indifférent" en premier, sélectionné par défaut). Main : mini-calendar 240px + grille créneaux. État vide créneaux avec encart "Choisis une date ←". Footer : "Total X € — payé sur place" + bouton textuel "Réserver Mar. 12 mai · 11:30". État succès complet (récap + "Voir mes RDV" branché sur `/client/bookings`). Image réelle dans care-card si dispo, sinon dégradé rose. Pas de compteur dispos par praticienne. Phrase "selon les conditions du salon" au lieu d'inventer "24h". |
| 18 | `modal-form` (wrapper) | — | Voir section 3. C'est le wrapper, pas une modale métier. |

### Code orphelin à supprimer

`frontend/src/app/features/bookings/modals/create/create-booking.component.ts` n'a aucun caller (legacy d'avant la refonte vers `BookingStepperComponent`). À supprimer pendant l'implémentation. Voir mémoire `project_orphan_create_booking`.

## 6. i18n — clés à ajouter / modifier

Toutes les chaînes nouvelles ou modifiées doivent être dupliquées dans `frontend/public/i18n/fr.json` ET `frontend/public/i18n/en.json` (cf. CLAUDE.md). Liste indicative non exhaustive :

- `modalForm.kbdSave` ("⌘S pour enregistrer" / "⌘S to save")
- `modalForm.kbdValidate` ("⏎ pour valider" / "⏎ to validate")
- `modalForm.kbdValidateMulti` ("⌘⏎ pour valider" / "⌘⏎ to validate")
- `modalForm.back` ("‹ Retour" / "‹ Back")
- `posts.publishContextual` ("Publier" / "Publish")
- Reformulations contextuelles par modale (titres, sous-titres, hints, libellés boutons) — à factoriser au moment de l'implémentation.

## 7. Stratégie d'implémentation

Suggérée pour le plan d'implémentation à venir (next step : invocation de `writing-plans`).

### Phase 1 — Infra (1 PR)
1. Refactorer `<modal-form>` avec les nouvelles props et slots.
2. Créer les 6 composants annexes (`<modal-stepper>`, `<modal-tabs>`, `<app-care-row>`, `<app-employee-picker>`, `<app-cares-multi-picker>`, `<app-mini-calendar>`).
3. Ajouter les media queries `@media (min-width: 768px)` dans `styles.scss`. Vérifier zéro régression mobile.
4. Stories Storybook ou page `/dev/modals` pour tester les 3 tailles + dangerous + tous les slots.

### Phase 2 — Migration des modales (par bloc, ordre proposé)
Le bloc confirms et auth en premier (gain visuel rapide, peu de code). Puis les formulaires standards et courts. Le bloc workflows (booking-dialog, booking-stepper, employee-detail) en dernier car le plus dense.

Chaque modale = 1 PR :
- Adapte le caller pour utiliser les nouvelles props (`size`, `breadcrumbParent`, `subtitle`, `dangerous`, `kbdHint`).
- Remplace `<dynamic-form>` par des field-row natifs là où c'est utilisé (cohérence grammaire).
- Remplace `<mat-form-field>`, `<mat-tab-group>`, `<mat-checkbox>` par les composants annexes ou des inputs natifs stylés.
- Supprime les icônes Material du wrapper.
- Met à jour les libellés contextuels.
- Met à jour ou enrichit les DTOs côté front (et back si nécessaire) selon les notes par modale.
- Met à jour i18n fr + en.
- Vérifie le rendu mobile (doit être inchangé).

### Phase 3 — Cleanup
- Supprime `create-booking.component.ts` orphelin.
- Re-test e2e sur les flows critiques (booking vitrine, création soin, suppression catégorie avec réassignation).

## 8. Notes mémoire associées

Le brainstorming a généré ou mis à jour les mémoires suivantes (dans `~/.claude/projects/-Users-Gustavo-alves-Documents-personal-portfolio/memory/`) :

- `feedback_modals_mobile_untouched.md` — Mobile bottom-sheet intouchable, refonte PC+tablette via `@media (min-width: 768px)` uniquement.
- `project_pending_auth_features.md` — Forgot-password + remember-me prévus mais non implémentés (back ni front), ne pas ajouter de liens morts.
- `project_pending_auth_modal_context.md` — auth-modal doit recevoir `salonName/careName/date` via `data`, "Plus tard" doit conserver la sélection booking.
- `project_care_images_limit.md` — Max 5 images par soin, cover = order=0 (pas de flag isCover).
- `project_pending_review_booking.md` — Modales approve/reject booking : feature future, sortie du scope, à brainstormer plus tard.
- `project_orphan_create_booking.md` — `features/bookings/modals/create/create-booking.component.ts` orphelin, à supprimer pendant l'implémentation.

## 9. Mockups visuels

Tous les mockups HTML produits pendant le brainstorming sont conservés dans `.superpowers/brainstorm/14863-1778288487/content/` :

- `grammar-options.html` — 3 directions de grammaire (A panel plat / B édito / C compact). Validé : header C + style B.
- `grammar-fusion.html` — Grammaire commune validée.
- `sizes.html` — Démonstration des 3 tailles S/M/L.
- `m1-delete-care.html` à `m17-booking-dialog.html` — Une mockup par modale (avec variantes : empty state, limite atteinte, mode édition, modes différents pour modales polymorphes, état succès).
- `m7b-create-care-limit.html` — Variante état "limite 5 images atteinte" pour create-care.
- `m16b-employee-detail-editable.html` — Variante identité éditable pour employee-detail.
- `m18-modal-form-api.html` — Synthèse de l'API du wrapper (ce qui est dans la section 3 de ce doc).

## 10. Dépendances back

Pour que tous les designs fonctionnent, les DTOs suivants doivent être enrichis côté backend Spring Boot (à inclure dans le plan d'implémentation) :

| DTO | Champs à ajouter |
|-----|------------------|
| `ReassignCategoryDialogData` (front) | `careList: {name, duration, price}[]` |
| `ReviewLeaveDialogData` (front) | `type, startDate, endDate, reason` |
| `RateVisitDialogData` (front) | `salonName, visitDate` (en plus de `visitId, careName` actuels) |
| `AuthModalData` (front, nouveau) | `salonName?, careName?, appointmentDate?` (optionnels) |
| `Employee` (front + back) | `createdAt, bookingsCount` (pour méta sociale dans employee-detail) |
| `PublishMissingDialogData` (front + back) | `checklist: {key, status: 'done' \| 'missing'}[]` (au lieu de juste `missing[]`) |
| `CreatePostRequest` (front + back) | `careId?: number` (optionnel, pour "Soin associé") |

Le back doit aussi exposer ces champs dans les endpoints concernés. La pluralité des modifications côté Spring Boot fait que la phase 2 de l'implémentation aura un pas back en plus pour chaque modale concernée.
