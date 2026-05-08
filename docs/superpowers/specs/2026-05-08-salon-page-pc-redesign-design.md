# Refonte page salon PC — vue client & vue pro (design)

**Date :** 2026-05-08
**Statut :** Design — en attente de plan d'implémentation
**Contexte :** Sortie de brainstorming le 2026-05-08. Variante "B raffinée — esprit eauceane.com" validée pour la page client. Éditeur pro pensé en miroir.

## 1. Pourquoi

Aujourd'hui la page salon (côté client `pages/salon` et côté pro `features/salon-profile`) est pensée mobile-first :

- **Vue client** (`/salon/:slug`) — onglets "Soins / Posts / Contact", layout étroit qui ne tire pas parti de la largeur PC.
- **Vue pro** (`/pro/.../salon`) — formulaire à onglets Material avec champs textuels. Aucune correspondance visuelle avec ce que voit le client. Le pro ne sait pas à quoi ressemblera son salon avant publication.

**Vision produit :** la page `/salon/:slug` doit donner au pro l'impression que **c'est son site**, pas une fiche dans une marketplace. Et la page de gestion doit être un **éditeur visuel en miroir**, pas un formulaire administratif déconnecté.

## 2. Objectifs

1. **Client PC** : vitrine éditoriale raffinée, scrollable, dans l'esprit luxe minimaliste apaisé d'eauceane.com, adaptée à la palette nacre Pretty Face.
2. **Pro PC** : éditeur en miroir de la vitrine. Le pro voit ce que voient ses clients et édite chaque section *en place*.
3. **Section "Stories"** réintroduite comme signature de l'app : posts scrollables horizontalement avec réservation directe sur la carte (réutilisation 100% de `SalonPostsViewerComponent` existant en mode focus modal).
4. **White-label léger** : marque Pretty Face s'efface au profit du salon, sauf footer discret "Propulsé par Pretty Face ✿".

## 3. Hors scope

- Mobile et tablette de la page client (la page mobile actuelle reste, on n'y touche pas dans cette spec).
- Mobile et tablette de la page pro (la page pro actuelle reste pour mobile).
- Refonte des modales pro (déjà identifiée comme spec dédiée séparée).
- Édition CSS custom (couleurs d'accent dérivées du logo, polices personnalisées) — niveau white-label 1 retenu (badge discret), pas de personnalisation au-delà.
- Refonte du composant `SalonPostsViewerComponent` lui-même — on le réutilise tel quel.
- Génération automatique de contenu (description, hero) par IA — hors scope ici.

## 4. Vue client — `/salon/:slug` sur PC

### 4.1 Structure (de haut en bas)

| # | Section | Contenu | Notes |
|---|---------|---------|-------|
| 1 | **Header sticky** | Logo salon + nom · Nav (Accueil, Nos soins, Stories, À propos, Contact) · Sélecteur langue FR/EN · CTA "Prendre RDV" | Backdrop blur, bordure basse, le CTA est toujours visible |
| 2 | **Hero éditorial** | Eyebrow `— Esthétique & bien-être —` · Titre serif (Cormorant Garamond, 48px) · Lede · 2 CTA (primaire "Prendre RDV", secondaire "Découvrir") · flèche descendante | Centré, padding généreux, fond crème |
| 3 | **Bandeau photo** | Hero image du salon, ou dégradé palette si absente | Hauteur 280px, marge latérale 36px, radius 4px |
| 4 | **À propos** | 2-col : image (320px) + texte (lab + h2 serif + 2 paragraphes + lien "En savoir plus") | Section masquée si `description` vide |
| 5 | **Soins & rituels** | Cartes 2-col par catégorie : icône ronde, nom, description, durée/prix, lien "Réserver →" | Catégories vides masquées, soins inactifs masqués |
| 6 | **Stories** | Rangée horizontale scrollable (variante B) — voir 4.2 | Section entière masquée si 0 posts publiés |
| 7 | **CTA "Prenez soin"** | Fond accent-soft, titre serif, paragraphe, bouton "Prendre RDV" | Toujours présent |
| 8 | **Contact** | 2-col : info (adresse, téléphone, email, horaires) + carte Leaflet | Section masquée si aucune info de contact |
| 9 | **Footer** | 4 colonnes (brand + nav + légal + social) + `© 2026 [Nom salon]` + `Propulsé par Pretty Face ✿` | Fond sombre |

### 4.2 Section Stories — variante B

Réutilise `SalonPostsViewerComponent` existant (snap vertical 3:4, types PHOTO/BEFORE_AFTER/CAROUSEL, bouton Réserver intégré quand `careId` présent).

**Vue first-load (in-page) :**
- Titre éditorial gauche (`— Stories du salon —` + h2 + sous-titre court)
- Boutons flèches gauche/droite à droite + lien "Voir toutes →"
- Rangée horizontale de cartes 240×320, scroll-snap horizontal, drag/touch/molette
- Chaque carte = preview statique du post avec :
  - Pill type en haut-gauche (`📷 Photo` / `⇄ Av/Ap` / `⊞ Carrousel · N`)
  - Caption + tag soin (avec prix si `careId`) en bas
  - Action `↗` (partage) en bas-droite
  - Action `RDV` (rond accent rosé) si `careId`, déclenche `BookingDialogComponent` directement
- Pour les posts BEFORE_AFTER, le slider visible mais figé à 50% en preview

**Vue focus (clic sur carte) :**
- Modale plein écran dialog réutilisant `SalonPostsViewerComponent` en mode TikTok complet (snap vertical, défilement, slider av/ap interactif, RDV intégré)
- Position initiale = post cliqué
- ESC ou clic fond ferme

**Section masquée côté client si 0 posts publiés** (validation 7.2). Pas de placeholder vide.

### 4.3 Palette & typographie

| Token | Valeur | Usage |
|-------|--------|-------|
| `--ink` | `#2a2522` | Texte principal, footer |
| `--ink-soft` | `#6b5e57` | Texte secondaire, descriptions |
| `--line` | `#ece4dd` | Bordures, séparateurs |
| `--bg` | `#fdfaf8` | Fond crème principal |
| `--paper` | `#fff` | Fond sections "alt" |
| `--accent` | `#b56b5a` | Boutons, liens, accents |
| `--accent-soft` | `#f4e6df` | Fonds CTA bands, icon backgrounds |
| `--accent-dark` | `#9b5848` | Hover boutons primaires |

- **Titres** : Cormorant Garamond, weight 300, sizes 48/34/30/22
- **Corps** : système (-apple-system…), 13–14px, line-height 1.7–1.9
- **Labels/eyebrows** : 9–10px, uppercase, letter-spacing 3–4px

Ces tokens sont **scope-locaux à la page salon** (préfixe `--pf-salon-*` au moment de l'implémentation), pas globaux à l'app — pour ne pas perturber le reste.

### 4.4 Responsive

Spec ciblée PC (`min-width: 1024px`). En-dessous, on **conserve la page mobile actuelle** sans modification. Un breakpoint à 1024px bascule entre les deux templates (ou la nouvelle page se dégrade au mieux entre 768 et 1024 — à décider à l'implémentation, mais pas dans cette spec).

### 4.5 Navigation

Les liens de la nav sticky sont des **ancres** vers les sections (`#soins`, `#stories`, `#about`, `#contact`). Scroll smooth. L'ancre active se met à jour selon la section visible (IntersectionObserver).

## 5. Vue pro — `/pro/.../salon` sur PC

### 5.1 Layout général

```
┌─────────────────────────────────────────────────────────────┐
│ TOP BAR éditeur (Pretty Face / Console pro / Ma page salon) │
│ État publication · "Aperçu client" · "Publier"             │
├──────────┬──────────────────────────────────────────────────┤
│          │                                                  │
│ SIDEBAR  │              CANVAS (= page client)              │
│          │                                                  │
│ Sections │  En-tête      [hover : cadre éditable]           │
│ Checklist│  Hero         [click : panneau d'édition]        │
│ Aperçu   │  À propos                                        │
│ public   │  Soins                                           │
│          │  Stories                                         │
│          │  CTA                                             │
│          │  Contact                                         │
│          │  Footer                                          │
└──────────┴──────────────────────────────────────────────────┘
```

### 5.2 Top bar éditeur

- Gauche : breadcrumb `Pretty Face / Console pro › Ma page salon`
- Droite :
  - **Pill statut** :
    - DRAFT → `● Brouillon · prêt à publier` (rosé) si checklist OK, sinon `● Brouillon · N élément(s) manquant(s)`
    - ACTIVE → `● En ligne` (vert)
  - Bouton ghost **"Aperçu client →"** : ouvre `/salon/:slug?preview=1` dans un nouvel onglet (statut DRAFT, sans cache, banner preview en haut comme aujourd'hui)
  - Bouton primaire **"Publier"** :
    - Si DRAFT et checklist OK → "Publier la page" (déclenche `POST /api/pro/salon/publish` existant)
    - Si DRAFT et checklist incomplète → désactivé, tooltip listant les éléments manquants
    - Si ACTIVE → bouton remplacé par lien `↗ Voir la version publique` (les sauvegardes en ACTIVE sont déjà live, pas besoin de republier)

### 5.3 Sidebar gauche (240px)

Trois blocs verticaux séparés par bordures fines :

**(a) Sections de la page** — liste cliquable :
- `⌂ En-tête (logo, nav)`
- `✦ Hero d'accueil`
- `✿ À propos`
- `≡ Soins & rituels` — badge `2 vides` si catégories sans soin
- `▶ Stories` — badge `0` si pas encore de story
- `★ CTA "Prenez soin"`
- `✉ Contact`
- `▭ Pied de page`

Click → scroll vers la section + active l'état d'édition. Item actif = barre d'accent gauche + fond blanc.

**(b) Checklist "Avant publication"** :
- Réutilise la logique `canPublish` du backend (tenant + au moins 1 soin actif + champs requis)
- Ligne par requirement : `✓ Nom du salon` / `! Photo de hero` (vert/rosé)
- Visible seulement tant que statut = DRAFT. Disparaît une fois la page ACTIVE (toutes les modifs ultérieures sont live).

**(c) Lien aperçu** :
- `↗ Voir la version publique` : ouvre `/salon/:slug` dans un nouvel onglet
- Affiché uniquement si statut ACTIVE

### 5.4 Canvas — édition contextuelle

Le canvas affiche **exactement le rendu client**, avec une couche d'édition par-dessus.

**Wrapper `<app-editable-section>`** (composant nouveau, voir 6.2) entoure chaque section du canvas. Comportement :

1. **État repos** — la section ressemble à 100% au rendu client.
2. **État hover** — cadre bleu pointillé `outline: 2px dashed` + onglet en haut-gauche `✎ Section · Cliquez pour éditer`.
3. **État active** (clic) — cadre rosé plein + le **panneau d'édition** apparaît directement sous l'onglet, ancré à la section. Pas de modale plein écran. Pas de drawer latéral. L'édition reste contextuelle, in-place.
4. **Panneau d'édition** : contient les champs spécifiques à la section (texte, image, liste de soins, etc.) + 2 boutons : **Enregistrer** / **Annuler**. Pas d'auto-save.

Une seule section éditable à la fois. Cliquer sur une autre section = ferme le panneau actif (avec confirmation si modifications non sauvegardées).

### 5.5 Modèle de sauvegarde

- **Sauvegarde manuelle par section** (Enregistrer dans le panneau)
- **Persistance immédiate** en backend : chaque "Enregistrer" déclenche un `PATCH /api/pro/salon/<section>` (ou réutilise `PUT /api/pro/salon` existant pour le tenant)
- **Statut DRAFT/ACTIVE conservé** :
  - Tant que **DRAFT** → la page n'est pas publiquement visible. Le bouton "Publier" en top bar fait passer en ACTIVE (déclenche la validation backend `canPublish`).
  - Une fois **ACTIVE** → chaque sauvegarde est immédiatement live côté client. Pas de cycle "DRAFT temporaire" sur modifs.
- En DRAFT, la pill statut reflète la checklist `canPublish` (OK ou nb d'éléments manquants), pas un compteur de changements.
- En ACTIVE, la pill affiche simplement `● En ligne` (chaque save étant déjà live).

### 5.6 Sections éditables — détails

| Section | Champs | Source backend |
|---------|--------|----------------|
| **En-tête** | Logo (image), Nom du salon | `UpdateTenantRequest.logo`, `.name` |
| **Hero** | Eyebrow (nouveau ?), Titre principal, Sous-titre, Photo hero | `name` réutilisé / `description` ou nouveau champ ? — voir 8.3 |
| **À propos** | Image dédiée, Titre, Paragraphes | nouveau champ ? — voir 8.3 |
| **Soins** | Catégories : ajout/édition/suppression. Soins : drag-reorder, ✎ éditer, 👁 masquer, 🗑 supprimer, `+ Ajouter un soin` | API existante `cares` + `categories` |
| **Stories** | Outils par carte (✎ éditer, × supprimer). Carte `+ Nouvelle story` ouvre le `CreatePostModalComponent` existant | API `posts` existante |
| **CTA** | Titre, Texte | nouveau champ — voir 8.3 |
| **Contact** | Adresse, code postal, ville, pays, téléphone, email, horaires | `UpdateTenantRequest` (sauf horaires : à clarifier) |
| **Pied de page** | Réseaux sociaux (Instagram, Facebook URLs) | nouveau champ — voir 8.3 |

### 5.7 Soins — interactions spécifiques

Chaque carte de soin en mode pro affiche :
- **Drag handle** (`⋮⋮`) en haut-droite pour réordonner par drag-and-drop (existe déjà ailleurs dans l'app ? à vérifier — sinon Angular CDK Drag&Drop)
- Boutons d'action en hover : `✎ Éditer` (ouvre le modal édition soin existant), `👁 Masquer` (toggle visibilité côté client), `🗑 Supprimer` (confirmation)
- Lien `+ Ajouter un soin` à côté du titre de catégorie → ouvre le modal création soin existant

Les soins **masqués** apparaissent en demi-opacité dans le canvas pro avec un badge "Masqué", invisibles côté client.

Les **catégories vides** apparaissent dans le canvas pro avec un placeholder explicatif `Aucun soin dans cette catégorie. Les visiteurs ne la verront pas.` + lien `+ Ajouter un soin`. Côté client, elles sont masquées.

### 5.8 Stories — interactions spécifiques

- Rangée horizontale identique au client, mais chaque carte expose en hover :
  - `✎` (édite la story — réutilise le composant de création/édition existant si dispo, sinon nouvelle modale)
  - `×` (supprime — confirmation)
- Carte finale `+ Nouvelle story` (encadré pointillé accent) → ouvre `CreatePostModalComponent` existant
- Pas de "viewer modal" en mode pro (le pro a déjà ses outils sur la carte)

## 6. Architecture frontend

### 6.1 Routes

Aucun changement de routes :
- `salon/:slug` continue de pointer vers `pages/salon/salon-page.component`
- `pro/.../salon` continue de pointer vers `features/salon-profile/salon-profile.component`

Mais **chaque composant détecte le breakpoint PC** (≥ 1024px) et bascule entre l'ancien template (mobile) et le nouveau.

Option à valider à l'implémentation :
- (a) Un seul composant avec deux templates (`@if (isPc())`) — simple, testable.
- (b) Deux composants `SalonPagePcComponent` / `SalonPageMobileComponent`, le composant racine choisit lequel rendre — meilleure isolation, plus de fichiers.

**Recommandé : (b)** pour ne pas alourdir un composant déjà gros, et pour tester chaque vue en isolation.

### 6.2 Nouveaux composants (vue pro)

```
features/salon-profile/
├── pc/                              ← nouveau dossier PC
│   ├── salon-editor-pc.component.ts (root layout : top-bar + side + canvas)
│   ├── editor-top-bar/
│   ├── editor-sidebar/
│   ├── canvas-sections/
│   │   ├── header-section.component.ts        (édition logo + nom)
│   │   ├── hero-section.component.ts          (édition titre/sous-titre/photo)
│   │   ├── about-section.component.ts
│   │   ├── cares-section.component.ts         (catégories + soins inline)
│   │   ├── stories-section.component.ts
│   │   ├── cta-section.component.ts
│   │   ├── contact-section.component.ts
│   │   └── footer-section.component.ts
│   └── shared/
│       ├── editable-section.component.ts      (wrapper hover/active/panel)
│       └── inline-edit-panel.component.ts     (panneau édition avec save/cancel)
```

Le wrapper `<app-editable-section>` accepte :
- `[label]` (titre dans l'onglet)
- `[icon]`
- `[isActive]` (bound au signal de la section active dans le store)
- `(activate)` / `(deactivate)`
- `<ng-content>` pour le rendu client de la section
- `<ng-content select="[edit-panel]">` pour le panneau d'édition

### 6.3 Nouveaux composants (vue client)

```
pages/salon/
├── pc/                              ← nouveau dossier PC
│   ├── salon-page-pc.component.ts (root scrollable)
│   ├── client-header/                (header sticky + smooth scroll spy)
│   ├── client-hero/
│   ├── client-banner/
│   ├── client-about/
│   ├── client-cares/                 (catégories + cartes soins)
│   ├── client-stories/               (rangée horizontale + viewer modal)
│   ├── client-cta/
│   ├── client-contact/
│   └── client-footer/
```

Plusieurs composants seront **partagés** entre client et pro (le pro affiche le rendu client identique avec une couche d'édition au-dessus). Idéalement, mettre les "rendus purs" dans `pages/salon/pc/sections/` et le pro les wrappe.

### 6.4 État (store pro)

Le `SalonProfileStore` actuel reste source de vérité pour le tenant. Ajouter :
- `activeSectionId: signal<string | null>` (id de la section en cours d'édition)
- `dirtySection: signal<boolean>` (le panneau actif a des modifs non sauvegardées)
- Méthodes : `activateSection(id)` / `deactivateSection()` / `saveSection(id, payload)` / `cancelSection()`

Confirmation modale si `activateSection` est appelé alors que `dirtySection() === true`.

### 6.5 Section Stories : viewer modal

Pour la vue client, créer un nouveau composant léger `SalonStoriesRowComponent` qui affiche les previews et, au clic, ouvre une modale plein écran contenant `SalonPostsViewerComponent` (existant) avec un input `[startIndex]` à ajouter au composant existant pour positionner sur le post cliqué.

C'est la **seule modification** au composant `SalonPostsViewerComponent` existant : ajouter un `@Input() startIndex = 0` qui scrolle au bon item à l'init.

## 7. Architecture backend

### 7.1 Aucun nouveau endpoint requis pour la majorité

- Le client `/salon/:slug` consomme déjà `GET /api/public/salons/:slug` qui retourne `PublicSalonResponse`.
- Le pro édite via `PUT /api/pro/salon` (payload `UpdateTenantRequest`) — déjà existant.
- Soins : endpoints existants utilisés par les modales.
- Posts : endpoints existants.
- Publication : `POST /api/pro/salon/publish` — déjà existant.

### 7.2 Champ "stories visibles côté client"

Aucun changement backend nécessaire. Côté client, `PublicSalonResponse` n'expose pas la liste des posts (le viewer charge via `/api/public/salons/:slug/posts`). Le client demande la première page : si vide → masquer la section. C'est une décision **purement frontend**.

### 7.3 Nouveaux champs (à confirmer en plan d'implémentation)

Voir question ouverte 8.3.

## 8. Questions ouvertes / risques

### 8.1 Drag-and-drop des soins

À confirmer : l'app utilise-t-elle déjà Angular CDK pour du drag-and-drop ailleurs ? Si non, l'introduction d'`@angular/cdk/drag-drop` est un nouveau coût (taille bundle ~20kb). Alternative : flèches haut/bas par item, plus simple, moins fluide.

**Reco :** flèches haut/bas en v1, drag-and-drop en v2 si demande utilisateur.

### 8.2 Édition de l'eyebrow et des textes "magiques"

Le mockup utilise des textes éditoriaux fixes (`— Esthétique & bien-être —`, `Un institut où le temps ralentit`, `— Notre carte —`, `Soins & rituels`, etc.). Ces textes sont aujourd'hui **génériques** dans la maquette. Trois options :

1. Les rendre éditables par le pro (champs `eyebrow`, `aboutTitle`, `caresTitle`, etc. à ajouter au modèle Tenant)
2. Les laisser figés (textes hardcodés via translations FR/EN) — plus simple, moins personnalisé
3. Mixte : titres figés en translations, eyebrows et textes longs (about, CTA) éditables

**Reco : option 3** — cohérence visuelle préservée (les titres restent "officiels" Pretty Face), mais le pro raconte son histoire dans les paragraphes longs.

À trancher en plan d'implémentation, en fonction du temps d'ajout des champs backend.

### 8.3 Champs Tenant à ajouter (selon résolution de 8.2)

Si option 3 retenue :
- `aboutText: string | null` — texte long de la section À propos (peut réutiliser `description` actuel ?)
- `aboutImage: string | null` — image de la section À propos (différente du hero)
- `ctaText: string | null` — paragraphe du CTA "Prenez soin"
- `instagramUrl: string | null`
- `facebookUrl: string | null`
- `openingHours: string | null` — texte libre pour horaires (ou structure dédiée — déjà existante ?)

**À vérifier :** la table `Tenant` a-t-elle déjà des champs réseaux sociaux ou horaires ? Si oui, on les réutilise. Sinon, c'est un Flyway shared (rappel mémoire : Flyway shared baselining toujours en attente sur tenants legacy — à coordonner).

### 8.4 Stories : preview before-after fonctionnelle ?

Sur les cartes preview de la rangée Stories, le slider before-after est-il interactif (drag possible) ou figé à 50% ? Recommandation : **figé à 50%** sur la rangée (sinon, conflit entre drag horizontal du slider et scroll horizontal de la rangée). L'interaction complète arrive dans le viewer modal.

### 8.5 Viewer modal et navigation au clavier

Le viewer modal plein écran doit gérer ↑↓ pour défilement, ESC pour fermeture. Le composant existant gère la souris/touch — vérifier si le clavier marche déjà ou s'il faut l'ajouter.

### 8.6 Sécurité aperçu DRAFT

Le bouton "Aperçu client →" en top bar du pro ouvre `/salon/:slug?preview=1`. Vérifier :
- Aujourd'hui, accéder à `/salon/:slug` quand statut = DRAFT renvoie une preview au pro propriétaire (avec `PreviewBannerComponent`). Cela continue d'exister.
- Pas de fuite si quelqu'un d'autre tente l'URL avec `?preview=1` → comportement existant : la route publique vérifie si l'utilisateur est le propriétaire avant de retourner la DRAFT, sinon 404.

## 9. Critères de succès

1. Sur PC ≥ 1024px, `/salon/:slug` rend la nouvelle vitrine éditoriale, scrollable, avec les 9 sections définies en 4.1.
2. La section Stories ne s'affiche pas si 0 posts publiés.
3. La section Stories permet de **réserver directement** depuis une carte (RDV → `BookingDialogComponent`) ou en plein écran via le viewer modal.
4. Sur PC, `/pro/.../salon` affiche le canvas miroir avec édition contextuelle inline par section.
5. Une modification dans un panneau n'est persistée qu'au clic "Enregistrer".
6. La logique DRAFT/ACTIVE actuelle est conservée. Le bouton "Publier" en top bar fait basculer DRAFT → ACTIVE.
7. La pill statut top bar reflète le statut courant et l'état "à jour" / "changements non publiés" (en DRAFT).
8. La sidebar gauche permet de naviguer entre sections (scroll + activation).
9. Aucune régression sur la version mobile actuelle (les deux templates coexistent).
10. Les translations FR/EN sont à jour pour tous les textes nouveaux (CLAUDE.md mandatory).

## 10. Décomposition pressentie pour le plan

À détailler au plan, mais les jalons probables :

- **J1 — Vitrine client PC squelette** : route conditionnelle PC, sections statiques avec données du tenant. Pas encore Stories.
- **J2 — Stories rangée + viewer modal** : composant `SalonStoriesRowComponent`, modale, ajout du `startIndex` au viewer existant.
- **J3 — Pro PC layout** : top bar + sidebar + canvas vide + composant `<app-editable-section>` reproductible.
- **J4 — Pro PC sections d'édition** : implémentation des 8 sections, chacune avec son panneau Enregistrer/Annuler.
- **J5 — Soins inline pro** : drag (ou flèches), masquer, ajouter, éditer en réutilisant les modales existantes.
- **J6 — Stories pro inline** : outils par carte, "+ Nouvelle story", suppression.
- **J7 — Polish + i18n + tests** : translations, tests Karma sur les composants critiques, vérification responsive (≥ 1024px), checklist accessibilité.
- **J8 — Champs ajoutés backend (si 8.3)** : DTOs + migration Flyway si réseaux sociaux / aboutText / etc.

Total estimé : ~6–8 jours selon les arbitrages 8.2/8.3.
