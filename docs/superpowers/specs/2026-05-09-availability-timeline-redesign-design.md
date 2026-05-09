# /pro/availability — refonte timeline horizontale

**Date :** 2026-05-09
**Statut :** Design validé, plan d'implémentation à écrire.
**Scope :** une seule page (`/pro/availability`). Pas de touche aux autres écrans pro.

## Contexte et objectif

La page actuelle affiche les horaires hebdomadaires sous forme d'une grille 7 colonnes (1 jour par colonne) avec deux inputs `<input type="time">` par créneau. Trois griefs identifiés par l'utilisateur :

1. **Trop serré.** Sept colonnes sur PC laissent ~140 px par jour — les inputs natifs et le bouton "supprimer" se marchent dessus.
2. **Inputs time peu pratiques.** Le picker natif est lent, sans snap 30 min, et le rendu varie entre navigateurs.
3. **Difficilement responsive.** La même grille 7 colonnes ne tient pas sur mobile et la version actuelle ne propose pas de fallback dédié.

Objectif : remplacer la grille par une **timeline visuelle horizontale** sur PC qui montre d'un coup l'amplitude de la semaine, et par une **liste verticale empilée** sur mobile pour rester lisible. Édition par **popover** (clic sur un bloc ou sur un rail vide), avec des selects snap 30 min — pas de drag-resize.

## Décisions de design (fixées par brainstorming)

| Sujet | Décision |
|---|---|
| Layout PC ≥ 768px | Timeline horizontale, 1 ligne par jour, rail unique |
| Plage horaire | 6h → 22h (16h, granularité 1 colonne / heure) |
| Granularité de saisie | 30 min (selects pré-remplis, pas de saisie libre) |
| Interaction de création / édition | Clic sur bloc ou rail vide → popover (pas de drag-resize) |
| Toggle ouvert/fermé | Switch par jour. Fermé = bande hachurée non cliquable |
| Quick presets | Toolbar en haut : "Pleine semaine 9-18", "Avec pause midi", "Tout fermer" |
| Copier vers | Boutons dans le popover : Mer / Jeu / Ven / Toute la semaine |
| Layout mobile < 768px | Bascule vers cards verticales (1 par jour), time pickers boutons +/− |
| Style | Palette rose actuelle. Blocs ouverts : `linear-gradient(135deg, #c00066, #e85a8e)`. Fermés : `repeating-linear-gradient` `#f9e3ea` |

Mockup de référence : `.superpowers/brainstorm/21957-1778353765/content/05-availability-final-design.html`.

## Architecture des composants

Tout reste dans `frontend/src/app/features/availability/`. Le composant existant `AvailabilityComponent` est conservé comme **conteneur de page** (il garde l'état et appelle le store). On y ajoute un **switch de layout** par viewport et deux nouveaux composants enfants spécialisés.

### Découpage

```
features/availability/
├── availability.component.ts        ← conteneur de page (existant, refactored)
├── availability.component.html      ← réécrit : header + KPIs + @if(isMobile) layouts
├── availability.component.scss      ← réécrit
├── availability.store.ts            ← inchangé (déjà OK)
├── availability.service.ts          ← inchangé
├── availability.model.ts            ← + types `TimeSlot`, `WeekPreset` si nécessaire
│
├── timeline/                        ← nouveau, vue PC
│   ├── availability-timeline.component.ts
│   ├── availability-timeline.component.html
│   ├── availability-timeline.component.scss
│   └── availability-timeline.spec.ts
│
├── day-list/                        ← nouveau, vue mobile
│   ├── availability-day-list.component.ts
│   ├── availability-day-list.component.html
│   ├── availability-day-list.component.scss
│   └── availability-day-list.spec.ts
│
└── slot-popover/                    ← nouveau, partagé PC + mobile
    ├── slot-popover.component.ts
    ├── slot-popover.component.html
    ├── slot-popover.component.scss
    └── slot-popover.spec.ts
```

### Responsabilités

**`AvailabilityComponent` (conteneur)**
- Détecte le viewport (signal `isMobile = computed(() => window.innerWidth < 768)`, mis à jour via un `resize` listener avec garde `isPlatformBrowser`).
- Garde tout l'état d'édition (les slots de la semaine, le jour en cours d'édition, le slot en cours d'édition).
- Rend `<app-availability-timeline>` ou `<app-availability-day-list>` selon `isMobile()`.
- Rend la sticky save-bar et appelle `store.save()`.
- Contient la KPI strip (jours ouverts, heures hebdo, créneaux totaux).
- Contient la quick-actions toolbar (presets), qui modifie l'état directement.

**`AvailabilityTimelineComponent` (PC)**
- Inputs : `slots` (Signal\<WeekSlots\>), `closedDays` (Signal\<DayOfWeek[]\>).
- Outputs : `dayToggle` (DayOfWeek), `slotClick` (slot reference + position popover), `addSlotClick` (DayOfWeek + position popover).
- Rend l'axe horaire 6h-22h en en-tête.
- Rend 7 lignes : label jour + switch + rail timeline + bouton "+".
- Calcule la position des blocs en pourcentage : `left = (startMin - 360) / 960 * 100`, `width = (endMin - startMin) / 960 * 100`. (960 minutes = 16h × 60.)
- N'embarque AUCUN état d'édition. Uniquement de la projection visuelle + émission d'événements.

**`AvailabilityDayListComponent` (mobile)**
- Mêmes inputs / outputs que la timeline.
- Rend une carte par jour : nom + summary + switch + slots inline + bouton "+ ajouter une pause".
- Time-pickers : boîtes `<button>` cliquables qui ouvrent le popover.
- Émet les mêmes events que la timeline pour que le conteneur traite les deux uniformément.

**`SlotPopoverComponent` (partagé)**
- Inputs : `mode` ('create' | 'edit'), `dayOfWeek`, `initialStart`, `initialEnd`, position d'ancrage.
- Outputs : `confirm` ({ start, end, copyToDays?: DayOfWeek[] }), `cancel`, `delete`.
- Deux `<select>` snap 30 min (16h × 2 = 32 options chacun, formatées "06:00" → "22:00").
- "Copier vers" : groupe de toggle-buttons (les autres jours de la semaine + "toute la semaine").
- Rendu via `MatDialog` ou positionné absolument selon le viewport :
  - **PC** : positionné absolument sous le bloc cliqué via CDK Overlay (`OverlayPositionBuilder`).
  - **Mobile** : ouvert en bottom-sheet via `MatDialog` + `bottomSheetConfig()` existant.

### Pourquoi 3 composants

Si on tente une vue unifiée, le HTML devient un sapin de `@if (isMobile)` blocks. Trois composants permettent :
- de tester chacun isolément (la timeline a une logique de calcul de positions absentes du mobile) ;
- de modifier le visuel d'une vue sans risquer l'autre ;
- de bien voir lequel des deux on regarde quand on lit le code.

Le popover est partagé parce que la mécanique d'édition est identique aux deux viewports.

## Modèle de données

Le modèle existant `availability.model.ts` est suffisant. On y ajoute juste un type pour les presets :

```ts
export interface WeekPreset {
  key: 'fullWeek-9-18' | 'midDayBreak' | 'closeAll';
  labelKey: string;        // i18n key
  apply: (current: WeekSlots) => WeekSlots;
}
```

Pas de changement backend ni de migration — on parle uniquement d'UI.

## Flux d'interaction

### Créer un créneau (PC)
1. User clique sur un rail vide d'un jour ouvert.
2. Conteneur reçoit `addSlotClick(day, anchorEl)`.
3. Conteneur ouvre `SlotPopoverComponent` via CDK Overlay positionné au-dessus de `anchorEl`, mode `create`, `initialStart` = première heure ronde libre, `initialEnd` = `initialStart + 1h`.
4. User ajuste les selects, clique "Valider".
5. Popover émet `confirm({ start, end })`.
6. Conteneur ajoute le slot à `slots[day]`, marque dirty.

### Éditer un créneau (PC)
1. User clique sur un bloc rose existant.
2. Conteneur reçoit `slotClick(day, slotIndex, anchorEl)`.
3. Popover ouvert mode `edit` avec `initialStart`/`initialEnd` du slot.
4. User modifie OU clique "Supprimer" OU clique un jour dans "Copier vers".
5. Si `confirm`, le slot est mis à jour. Si `delete`, le slot est retiré. Si `copyToDays`, le slot remplace les slots des jours sélectionnés (override complet du jour cible — pas de merge).

### Mobile
Mêmes events, mais la boîte temps mobile (le rectangle "09:00") ouvre directement le popover en bottom-sheet (pas de mini-popover positionné — le bottom-sheet est plus naturel sur mobile).

### Validation côté front
- Empêcher `start >= end` (le bouton "Valider" est désactivé).
- Empêcher chevauchement avec un autre slot du même jour (avant la confirmation, vérifier les slots existants minus le slot en cours d'édition).
- Si le user crée un slot qui chevauche un autre, afficher un message dans le popover et désactiver "Valider".

### Sticky save bar
- Inchangée (existe déjà). Bouton "Enregistrer" appelle `store.save()`. Désactivé tant qu'aucun changement (`!dirty`) ou si un slot invalide est en cours.

## Cas limites et erreurs

- **Slot dépassant le rail (avant 6h, après 22h).** Si l'utilisateur édite manuellement les heures et fournit `05:30 → 09:00`, on rend visuellement clamp à 6h-22h mais on garde la valeur réelle. Le popover affiche un warning.
- **Jour fermé.** Le rail rend la bande hachurée. Le bouton "+" est disabled. Toggle switch → on déplie un slot par défaut "9h-18h" (UX pré-remplie).
- **Dernier slot supprimé.** Le jour reste ouvert mais sans slot. KPI "créneaux totaux" décrémenté.
- **Quick preset "Tout fermer".** Demande confirmation (modal) avant d'écraser tous les slots.
- **Copier vers.** Override complet : si Mardi a 2 slots et qu'on copie Lundi (1 slot) sur Mardi, Mardi a maintenant 1 slot. Pas de merge.
- **Resize de la fenêtre PC ↔ mobile.** Le composant change live. L'état d'édition (popover ouvert) est fermé au resize pour éviter les bugs de positionnement.
- **Mode SSR.** Pas de `window` à l'init : par défaut on rend la version PC (la plus probable). Le `effect` qui détecte mobile s'active après l'hydratation côté browser.

## Tests

### Unitaires
- `AvailabilityTimelineComponent` : calcul de position (left/width) selon les slots fournis. Snapshot du DOM pour 3 cas (semaine simple, double slot, jour fermé).
- `AvailabilityDayListComponent` : rendu correct pour les 3 mêmes cas.
- `SlotPopoverComponent` : selects pré-remplis correctement, validation start < end, événement `confirm` avec les bons args, "Copier vers" émet les jours sélectionnés.

### Intégration
- `AvailabilityComponent` : viewport mock < 768 → DayList rendu. Viewport ≥ 768 → Timeline rendu. Save bar appelle `store.save()` avec les slots modifiés.

### Visuel
- Vérifier sur PC réel et mobile réel (pas seulement DevTools) que les blocs sont bien positionnés.

## i18n

Nouvelles clés à ajouter dans `fr.json` et `en.json` :

```
pro.availability.timeline.axis6h22h
pro.availability.popover.title.create     // "Nouveau créneau · {{day}}"
pro.availability.popover.title.edit       // "Modifier · {{day}}"
pro.availability.popover.startLabel
pro.availability.popover.endLabel
pro.availability.popover.copyTo
pro.availability.popover.copyAll
pro.availability.popover.delete
pro.availability.popover.cancel
pro.availability.popover.confirm
pro.availability.preset.fullWeek          // "Pleine semaine 9—18"
pro.availability.preset.midDayBreak       // "Avec pause midi"
pro.availability.preset.closeAll          // "Tout fermer"
pro.availability.preset.confirmCloseAll   // confirmation modal
pro.availability.invalidOverlap           // "Ce créneau chevauche un autre"
```

Les clés existantes (`pro.availability.title`, `pro.availability.kpi.*`, `pro.availability.days.*`, `pro.availability.save`) sont conservées.

## Hors scope (volontairement)

- **Drag-resize**. Décidé dans le brainstorming — clic + popover seulement.
- **Vue calendrier 24h**. Plage 6h-22h fixe.
- **Slots minute-par-minute**. Snap 30 min seulement.
- **Saisie libre `<input type="time">`**. Remplacé par selects.
- **Différencier ouverture/employé.** L'écran reste au niveau du salon (horaires d'ouverture du commerce). Les disponibilités par employé restent où elles sont actuellement.
- **Backend / API**. Aucun changement : on lit/écrit le même DTO `WeekSlots` qu'avant.
- **Modales (refonte PC).** Hors scope — restent en l'état tant qu'on n'attaque pas ce sujet à part.

## Risques connus

- **CDK Overlay positioning sur PC.** Si la timeline est scrollée, le popover doit suivre le bloc. CDK gère ça nativement (`flexible-connected-position`) mais à tester.
- **Précision visuelle.** À 60 px par heure, un slot 9h00-9h30 fait 30 px de large — limite lisible. Acceptable car le popover affiche le détail.
- **Migration de l'état existant.** Les utilisateurs qui ont créé des slots avec des minutes non-rondes (9h17) verront leurs valeurs arrondies au prochain enregistrement. Décision : on les laisse telles quelles à l'affichage (clamp visuel), mais le popover snap à 30 min dès qu'on édite.
