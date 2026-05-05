# Preview vitrine, indicateur d'étape global, refonte home & page pricing

**Date :** 2026-05-06
**Statut :** Design validé en brainstorming, en attente de plan d'implémentation
**Cycle :** 5 jalons d'implémentation séquentiels dans une spec unique

## Contexte

Trois besoins remontés par l'utilisateur, traités ensemble parce qu'ils partagent un même fil rouge — rendre Pretty Face lisible et désirable :

1. **Le pro doit pouvoir prévisualiser sa vitrine** avant publication. Aujourd'hui `/salon/:slug` renvoie 404 si le salon est en `DRAFT`.
2. **Le pro ne doit pas se perdre** quand il configure son salon. La checklist d'onboarding existe sur le dashboard, mais disparaît dès qu'il navigue ailleurs (`/pro/cares`, `/pro/planning`, etc.).
3. **Le design PC doit séduire et fonctionner**. L'app a été pensée mobile-first ; sur PC les pages paraissent étroites, la home n'a pas de présence émotionnelle, et le visiteur (cliente comme pro) ne comprend pas immédiatement ce qu'il peut faire.

## Objectifs

Trois angles coordonnés :

- **Lisibilité onboarding** : un indicateur d'étape persistant sur toutes les pages pro tant que la vitrine est en `DRAFT`.
- **Confiance avant publication** : preview accessible en 1 clic, identique à ce que verra le client.
- **Désir** : home publique avec hero immersif + carrousel de salons "imposant", page `/pricing` dédiée aux pros avec démo animée.

## Critères de succès

**Qualitatif :**
- Un nouveau pro arrivé sur la home comprend en 5 secondes qu'il peut publier sa propre vitrine.
- Une nouvelle cliente arrivée sur la home a envie de cliquer sur un salon dans les 10 secondes.
- Un pro qui configure se voit avancer depuis n'importe quelle page (jamais de "où en suis-je ?").
- Le pro peut visualiser sa vitrine **comme un client la verra**, à tout moment, en 1 clic.

**Mesurable :**
- Home : LCP < 2.5s sur 4G simulée.
- Carrousel flip : 60fps stable sur Safari/Chrome desktop et mobile (DevTools Performance).
- Indicateur d'étape : présent sur 100% des pages `/pro/*` quand `status === 'DRAFT'`.
- Preview vitrine : 1 clic max depuis le dashboard ET depuis l'indicateur (toutes pages pro).
- Lighthouse home (PC) : Performance ≥ 85, Accessibility ≥ 95.
- Lighthouse pricing (PC) : Performance ≥ 80, Accessibility ≥ 95.

## Hors scope

- **Refonte des modales pro PC** — sortie en spec dédiée, mémoire enregistrée (`project_pending_modals_redesign.md`).
- **Refonte des pages clientes** autres que `/` (`/discover`, `/salon/:slug` redessinée en profondeur, `/bookings`, etc.).
- **Tarifs Stripe réels sur `/pricing`** — placeholder en attendant le shipping de `project_pending_payments.md`. Une 2e itération de la page `/pricing` interviendra après.
- **Page `/about`** existante.
- **Asset vidéo propriétaire pour le hero** — placeholder Pexels/Coverr en J3, remplacement plus tard.

## Architecture & jalons

| # | Jalon | Périmètre |
|---|-------|-----------|
| 1 | Indicateur d'étape global | Pill mobile + stepper PC + bottom-sheet, `ProShellComponent` wrapper, élévation `DashboardStore` |
| 2 | Preview vitrine (owner-only) | Backend : autoriser DRAFT pour owner. Front : bannière, bouton Aperçu dashboard, raccourci dans indicateur |
| 3 | Refonte home publique | Hero vidéo/image + carrousel multi-cards flip + bloc CTA pro discret |
| 4 | Page `/pricing` | Hero parallax + démo 3 widgets animés au scroll + features + tarif placeholder + CTA |
| 5 | Densification pages pro PC + lien partage preview | Conteneurs 1440px, grilles multi-col, split-view `/pro/salon`, tokens preview signés |

Les jalons 1-2 sont fortement couplés (le preview consomme l'indicateur). Les jalons 3-4 sont indépendants entre eux. Le jalon 5 dépend de 1-2 (les pages pro densifiées doivent intégrer l'indicateur).

---

## Jalon 1 — Indicateur d'étape global

### Comportement utilisateur

Tant que la vitrine du pro est en `DRAFT`, un indicateur de progression est visible sur toutes les pages `/pro/*`. Trois états :

1. **En cours** (1 ou 2 / 3) — visible, ton neutre/rose pâle.
2. **Prêt à publier** (3/3, DRAFT, `canPublish`) — passe en mode "succès", label *"Vitrine prête — publier"*, le CTA principal devient "Publier".
3. **Publié** (`status === 'ACTIVE'`) — retiré du DOM. Revient automatiquement si le pro dépublie.

### Responsive

**Mobile (< 768px)** : pill flottante haut-gauche, sous le header.
- Cercle de progression SVG (`2/3` au centre).
- Label court : *"Configuration · Suivant : horaires"*.
- Tap → ouvre un bottom-sheet avec liste détaillée + boutons Aperçu/Publier/Dashboard.

**Desktop (≥ 768px)** : stepper complet sticky sous le header.
- 3 étapes nommées avec connecteurs (`✓ Salon — ✓ Soins — ③ Horaires`).
- Étapes cliquables → navigation directe.
- À droite : bouton secondaire `Aperçu` + bouton `Publier` (si `canPublish`).

### Architecture

**Nouveau wrapper `ProShellComponent`** :

```
pages/pro/
├── pro-shell.component.ts           # nouveau
├── pro-shell.component.html         # nouveau
├── pro-shell.component.scss         # nouveau
```

```ts
@Component({
  selector: 'app-pro-shell',
  standalone: true,
  imports: [RouterOutlet, OnboardingIndicatorComponent],
  providers: [DashboardStore],
  templateUrl: './pro-shell.component.html',
})
export class ProShellComponent {}
```

```html
<app-onboarding-indicator />
<router-outlet />
```

**Modification du routing** dans `app.routes.ts` :

```ts
{
  path: 'pro',
  canActivate: [authGuard, roleGuard(Role.PRO)],
  loadComponent: () => import('./pages/pro/pro-shell.component').then(m => m.ProShellComponent),
  children: [ ...routes pro existantes ]
}
```

→ `DashboardStore` est élevé : partagé entre toutes les pages pro filles. `pro-dashboard.component.ts` perd son `providers: [DashboardStore]` (l'inject vient du parent).

**Nouveau composant** `OnboardingIndicatorComponent` :

```
shared/features/onboarding-indicator/
├── onboarding-indicator.component.ts
├── onboarding-indicator.component.html
├── onboarding-indicator.component.scss
├── onboarding-indicator-sheet.component.ts   # bottom-sheet (mobile)
└── onboarding-indicator.component.spec.ts
```

Un seul composant qui rend pill OU stepper selon un signal `isDesktop` (basé sur `BreakpointObserver` ou matchMedia signal). Auto-cache si `!isDraft()`.

**Source de vérité** : réutilise `DashboardStore.readiness()`, `isDraft`, `canPublish`. Pas de nouveau store.

**Service de calcul des étapes** (factorisation de la logique existante) :

```ts
@Injectable({ providedIn: 'root' })
export class OnboardingChecklistService {
  buildSteps(readiness: TenantReadiness | null): OnboardingStep[]
  computeProgress(steps: OnboardingStep[]): { done: number; total: number; nextKey: string | null }
}
```

→ Consommé par `pro-dashboard.component.ts` (remplace les computed actuels lignes 83-111) ET par `OnboardingIndicatorComponent`. Élimine la duplication.

### Bottom-sheet mobile

Réutilise `bottomSheetConfig` et `shared/uis/sheet-handle/`.

Contenu :
- Titre *"Votre configuration"*.
- Barre de progression.
- 3 étapes avec icônes et états (cochée/suivante/à faire).
- Bouton primaire `Aperçu de la vitrine`.
- Bouton secondaire `Retour au dashboard` (si on n'y est pas).
- Si `canPublish` : bouton Publier en haut, mode succès.
- Tap sur une étape → close sheet + navigation.

### Cohérence avec la checklist du dashboard

L'`onboarding-wrap` actuel dans `pro-dashboard.component.html` (lignes 22-128) reste — c'est la **forme riche** (quickstart personas, descriptions). L'indicateur global est la **forme compacte/persistante**. Mêmes labels, même calcul, même `nextStepKey` via le service partagé.

### i18n

Nouvelles clés (FR + EN) :
- `pro.onboarding.indicator.label` — *"Configuration"*
- `pro.onboarding.indicator.next` — *"Suivant : {step}"*
- `pro.onboarding.indicator.ready` — *"Vitrine prête — publier"*
- `pro.onboarding.indicator.preview` — *"Aperçu"*
- `pro.onboarding.sheet.title` — *"Votre configuration"*

Réutilise les clés existantes pour les noms d'étapes (`pro.dashboard.checklist.*`) et la progression (`pro.dashboard.checklist.progress`).

### Accessibilité

- Pill mobile : `<button>` avec `aria-label="Voir la progression de la configuration, étape {done} sur {total}"`.
- Stepper PC : chaque étape est un `<a>` routerLink avec `aria-current="step"` sur l'étape en cours.
- Bottom-sheet : focus trap géré par le pattern existant, `aria-modal="true"`.
- Animations : `prefers-reduced-motion` désactive les transitions.

---

## Jalon 2 — Preview vitrine

### Comportement

Pro propriétaire connecté → accède à `/salon/:slug` même en DRAFT, voit la vitrine **identique** au rendu client, avec une **bannière sticky en haut** :

> 🌸 *Mode aperçu — votre vitrine n'est pas encore publique* · `[Retour au dashboard]` `[Publier]` (si `canPublish`)

Tout autre visiteur sur un salon DRAFT → 404 (comportement actuel conservé).

### Backend

Modifier le contrôleur public salon (`SalonPublicController` ou équivalent — à identifier) sur `GET /api/public/salons/{slug}` :

```
- Si status = ACTIVE → comportement actuel (200 + DTO).
- Si status = DRAFT :
    - Utilisateur connecté ET propriétaire du tenant pour ce slug → 200 + DTO normal.
    - (J5) Token preview valide en query → 200 + DTO normal.
    - Sinon → 404 (comportement actuel).
- Si status = SUSPENDED ou DELETED → 404 pour tous (pas de preview).
```

**DTO** : ajouter le champ `status: 'DRAFT' | 'ACTIVE'` dans la réponse publique. Non-sensible (en DRAFT seul l'owner ou un token valide y accède).

### Frontend

**Détection du mode preview** dans `salon-page.component.ts` :

```ts
readonly isPreviewMode = computed(() => {
  const salon = this.salon();
  if (!salon) return false;
  return salon.status !== 'ACTIVE';
});
```

**Bannière** : nouveau composant `app-preview-banner` dans `shared/uis/`.
- Sticky top, hauteur ~56px, fond rose pâle (`#fdf3f7`), border-bottom rose discret.
- Icône 🌸, label, deux boutons à droite.
- Bouton Publier réutilise `DashboardStore.publish()` — store injecté depuis le `ProShellComponent` parent (mais la page `/salon/:slug` n'est PAS sous `/pro` dans le routing actuel : il faut soit déplacer la route, soit fournir le store localement quand on est en mode preview).
- **Décision** : on garde la route `/salon/:slug` à la racine (publique) et on instancie un `DashboardStore` local dans la bannière de preview quand `isPreviewMode()`. Le store est léger et le doublon est acceptable (l'utilisateur revient au dashboard juste après dans 99% des cas).

**Bouton "Aperçu" sur le dashboard** :
- Section `publish-section` du `pro-dashboard.component.html` actuel.
- Toujours visible quand `status === 'DRAFT'`, même si `!canPublish`.
- À côté du bouton Publier.
- Action : `router.navigate(['/salon', readiness.slug])`.

**Raccourci dans l'indicateur d'étape** : voir Jalon 1.

### Edge cases

- Pro publie depuis la bannière → `salon.status` devient ACTIVE → `isPreviewMode()` redevient false → bannière disparaît. Pas de redirect forcé.
- Pro non-propriétaire arrive sur slug DRAFT → 404.
- (J5) Token expiré ou révoqué → 404.
- **Synchronisation publish via bannière ↔ ProShellComponent** : la bannière utilise un `DashboardStore` local (la route `/salon/:slug` est hors `ProShellComponent`). Quand le pro publie depuis la bannière puis retourne au dashboard via le bouton, le store du `ProShellComponent` doit re-fetch `readiness`. Solution : un `effect` dans `ProShellComponent` qui re-déclenche `loadReadiness()` lors d'une navigation vers `/pro/*`, ou utilisation d'un service `ReadinessSyncService` qui notifie tous les stores actifs. **Décision en plan d'implémentation.**
- **Token preview + owner connecté simultanément** : si l'owner accède à son propre slug avec un `?preview=<token>` dans l'URL (peu probable mais possible), on autorise via owner check (le token devient redondant, ignoré). Aucune ambiguïté de sécurité.

### i18n

- `salon.preview.banner.title`
- `salon.preview.banner.backToDashboard`
- `salon.preview.banner.publish`
- `pro.dashboard.preview` — *"Aperçu"*

---

## Jalon 3 — Refonte de la home publique

### Architecture finale

```
HEADER (existant)
↓
HERO IMMERSIF (vidéo PC / image mobile, 80vh PC / 60vh mobile)
↓
CARROUSEL "PRÈS DE CHEZ VOUS" (multi-cards, card centrale flip)
↓
RECENT POSTS (existant, re-stylé)
↓
BLOC CTA PRO (discret, pointe vers /pricing)
```

### Hero immersif

**Asset** :
- Format : MP4 H.264 + WebM VP9 fallback.
- Durée : 8-15s, boucle parfaite.
- Résolution : 1920×1080.
- Poids : < 4 MB MP4, < 3 MB WebM.
- Muet, sans contrôles.
- Poster JPG/WebP ~150 KB.
- **J3** : placeholder Pexels/Coverr libre de droits. Asset propriétaire à produire plus tard.

**Implémentation** :

```html
@if (isDesktopOrTablet()) {
  <video class="hero-video"
    [poster]="heroPosterUrl"
    autoplay muted loop playsinline preload="metadata">
    <source [src]="heroVideoWebm" type="video/webm" />
    <source [src]="heroVideoMp4" type="video/mp4" />
  </video>
} @else {
  <img class="hero-poster" [src]="heroPosterUrl" alt="" />
}
```

- `preload="metadata"` : pas de chargement complet au load.
- `playsinline` : empêche fullscreen iOS.
- Détection desktop/tablet via signal basé sur `matchMedia('(min-width: 768px) and (hover: hover)')`.
- SSR-safe : serveur rend toujours `<img>`, client hydrate avec vidéo si conditions remplies.
- `prefers-reduced-motion` : `<img>` même sur PC.

**Overlay** :
- Gradient `linear-gradient(180deg, transparent 0%, rgba(0,0,0,0.35) 100%)`.
- Branding "PRETTY · Face" : style typo conservé, agrandi sur PC (`5rem` au lieu de `3.5rem`).
- Search bar : style conservé, élargie (`max-width: 560px` PC).

### Carrousel multi-cards avec flip 3D

**Choix d'implémentation** : custom Angular pur (~150 lignes TS + scss). Pas de Swiper.js (60 KB, overkill). Pas de `@angular/cdk/scrolling` (pas de coverflow natif).

**Composant** : `shared/uis/salon-carousel/salon-carousel.component.ts`.

```ts
@Component({
  selector: 'app-salon-carousel',
  standalone: true,
  ...
})
export class SalonCarouselComponent {
  readonly salons = input.required<SalonCard[]>();
  readonly centerIndex = signal(0);
  readonly flippedSlugs = signal<Set<string>>(new Set());

  next(): void
  prev(): void
  goTo(index: number): void
  toggleFlip(slug: string): void
  onCardClick(salon: SalonCard, isCenter: boolean): void
}
```

**Layout** :
- Stage : `display: flex`, `align-items: center`, `gap: 18px`, `perspective: 1600px`.
- Card centrale : 360×420 PC, full-width-1.5x mobile.
- Voisines (offset ±1) : 220×300, opacity 0.55, scale 0.92.
- Lointaines (offset ±2) : 180×240, opacity 0.3, scale 0.85.
- Cards offset > 2 : `display: none` après animation.

**Animation flip** : pure CSS.
- `transform-style: preserve-3d` sur l'inner.
- `rotateY(180deg)` quand `flipped`.
- Durée 700ms, easing `cubic-bezier(0.4, 0, 0.2, 1)`.
- `backface-visibility: hidden`.

**Map au verso** :
- Mini-Leaflet **lazy-loadé** la première fois qu'un flip est demandé (signal `mapsLoaded`).
- Container fixe (300×280 sur card 360×420), pin centré, zoom 14, sans contrôles.
- Bandeau info en bas : nom + adresse + lien `tel:` ou itinéraire Google Maps.
- Geocoding : factoriser le cache existant de `home.ts` dans un service `core/services/geocoding.service.ts`.

**Performance** :
- Transforms uniquement (GPU), pas de width/height anim.
- Cards offset > 2 cachées avec `display: none`.
- 60fps cible, mesuré DevTools Performance.

**Touch & swipe** :
- `pointerdown`/`pointermove`/`pointerup` sur le stage.
- Threshold 50px de swipe.
- Sur card centrale flippée, le swipe la dé-flip avant de glisser.

**Comportement** :
- Clic flèche / dot / swipe → change `centerIndex`.
- Clic card voisine → devient centrale.
- Clic card centrale (hors bouton flip) → navigation `/salon/:slug`.
- Clic bouton flip → toggle `flippedSlugs`.

**Accessibilité** :
- Stage : `role="region"`, `aria-label`, `aria-roledescription="carousel"`.
- Flèches : `aria-label="Salon précédent / suivant"`.
- Card centrale : `aria-current="true"`, focusable.
- Bouton flip : `aria-pressed`, `aria-label="Voir sur la carte / Voir la photo"`.
- Clavier : flèches gauche/droite navigation, Enter ouvre salon, Espace flip.

### Bloc CTA pro

Remplace les lignes 83-88 de `home.html`.

```html
<section class="pro-cta">
  <div class="pro-cta-inner">
    <div class="pro-cta-text">
      <span class="pro-cta-eyebrow">Pretty Face Pro</span>
      <h3 class="pro-cta-title">Vous êtes esthéticien·ne ?</h3>
      <p class="pro-cta-body">Une vitrine élégante, un planning intelligent, des outils pensés pour vous.</p>
    </div>
    <a routerLink="/pricing" class="pro-cta-button">
      Découvrir Pretty Face Pro →
    </a>
  </div>
</section>
```

- Fond gradient nacré rose/sable.
- Largeur pleine PC (`max-width: 1440px`), padding vertical 64px.
- Présent et visible mais non dominant.

### Suppression / migration

- **Mini-map actuelle** (lignes 28-32 de `home.html`) supprimée — la fonction "voir la carte" est portée par le flip.
- **Mini-cards salons** (lignes 36-60) supprimées — remplacées par le carrousel.
- **Recent posts** : conservé, juste re-stylé.
- **`SALON_GRADIENTS`** déplacé dans le carrousel.
- **`geocodeAddress` + cache** extraits dans `core/services/geocoding.service.ts`.

### i18n

- `home.salons.flipToMap` — *"Voir sur la carte"*
- `home.salons.flipToPhoto` — *"Voir la photo"*
- `home.salons.viewItinerary` — *"Voir l'itinéraire"*
- `home.proCta.eyebrow` — *"Pretty Face Pro"*
- `home.proCta.title` — *"Vous êtes esthéticien·ne ?"*
- `home.proCta.body`
- `home.proCta.button` — *"Découvrir Pretty Face Pro"*

### Performance cibles

- LCP < 2.5s sur 4G (poster image WebP/AVIF < 60 KB, vidéo `preload="metadata"`).
- CLS proche de 0 (hauteurs hero en vh fixes, cards `aspect-ratio` fixe).
- Flip à 60fps stable.
- Lighthouse PC : Perf ≥ 85, A11y ≥ 95.

---

## Jalon 4 — Page `/pricing` (démo animée pro)

### Architecture finale

```
HEADER (existant)
↓
HERO PARALLAX (~85vh, image salon en parallax léger)
↓
SECTION "VOS CHIFFRES PRENNENT VIE"
  - Mock browser frame
  - 3 widgets animés au scroll : CA / Calendrier / Avis
↓
SECTION "TOUT CE DONT VOUS AVEZ BESOIN"
  - Grille 4 cards (vitrine / planning / clients / paiements)
↓
SECTION TARIFS (placeholder en attendant Stripe)
↓
CTA FINAL ("Lancer mon salon")
```

### Routing

```ts
{
  path: 'pricing',
  loadComponent: () => import('./pages/pricing/pricing.component').then(m => m.PricingComponent),
}
```

### Hero parallax

- Image full-bleed 1920×1080.
- **Parallax** : `transform: translateY(scrollY * -0.5)` mis à jour via `@HostListener('window:scroll')` throttlé `requestAnimationFrame`.
- SSR-safe : pas de calcul scroll côté serveur.
- `prefers-reduced-motion` : parallax désactivé, image statique.
- Overlay gradient pour lisibilité.
- Titre + sous-titre + 2 CTAs ("Lancer mon salon" / "Voir la démo ↓" smooth-scroll).

### Section démo : 3 widgets animés

**Frame mock browser** : composant `<app-mock-browser>` réutilisable (barre de titre + zone contenu, purement décoratif).

**Widget 1 — CA en hausse** :
- KPI card style identique au vrai dashboard pro (cohérence visuelle).
- Au scroll-in, chiffre count-up de 0 → `12 450 €` (1.2s, easeOutCubic).
- Sparkline SVG qui se trace (`stroke-dashoffset` animé).
- Flèche `▲ +18%` qui apparaît à la fin.

**Widget 2 — Calendrier qui se remplit** :
- Mini-grille 7 col × 6 lignes (style heatmap).
- Slots passent gris → rose en cascade (stagger 30ms).
- ~25 slots sur 42 deviennent bookés (effet "rempli mais pas full").

**Widget 3 — Avis clients** :
- Card "★★★★★ 4.9" + 3 quotes courtes (fictives codées en dur).
- 5 étoiles apparaissent une à une (scale 0 → 1, 200ms, stagger 100ms).
- `4.9` count-up de 0.0 → 4.9.
- Quotes fade-in avec stagger.

**Déclencheur unique** : un `IntersectionObserver` sur le conteneur démo (threshold 0.3). Quand visible, signal `demoStarted = true` → cascade des 3 widgets (W1 immédiat, W2 +400ms, W3 +800ms). **Re-trigger désactivé** : si l'utilisateur scrolle au-dessus puis revient, l'animation ne rejoue pas.

**Données mockées** : codées en dur dans le composant. Aucune dépendance backend.

### Section "Tout ce dont vous avez besoin"

Grille 4 cards (2×2 PC, 1 col mobile) :

| Card | Icône | Titre | Bullets |
|------|-------|-------|---------|
| 1 | `storefront` | Vitrine en ligne | Page personnalisée · Photos · Réservation directe |
| 2 | `event_available` | Planning intelligent | Disponibilités · Buffer · Rappels auto |
| 3 | `groups` | Clients suivis | Historique · Notes · Photos avant/après |
| 4 | `payments` | Paiements simples | Stripe intégré · Factures auto |

- Padding 32px, border-radius 16px.
- Hover : border rose + élévation subtile.
- Pas d'animation au scroll.

### Section Tarifs (placeholder)

Une seule card centrée :

```
┌────────────────────────────────────┐
│  ESSAI GRATUIT 30 JOURS            │
│  Sans engagement · Sans CB         │
│                                    │
│  [Démarrer maintenant →]           │
└────────────────────────────────────┘
```

À remplacer par les vrais plans Stripe quand `project_pending_payments.md` sera shippé. Une 2e itération de la page `/pricing` interviendra alors.

### CTA final

Bandeau pleine largeur fond rose pâle, titre fort + bouton "Lancer mon salon →" pointant vers `/auth/register-pro`.

### Composants réutilisables créés

- `shared/uis/mock-browser/mock-browser.component.ts` — frame fenêtre stylée.
- `shared/uis/parallax-hero/parallax-hero.component.ts` — hero avec image parallax.
- `shared/utils/use-intersection.ts` — helper signal-based pour observer la visibilité.
- `shared/utils/use-count-up.ts` — animation count-up basée sur RAF.

### Pas de lib externe

CSS + RAF + signals suffisent. Pas de framer-motion, pas de gsap. Cohérent avec la philosophie zoneless.

### SSR

Tous les widgets ont une version statique (état final) en SSR. L'animation ne joue qu'au client après hydration. Pas de saut visuel grâce au "state final = state animé final".

### Accessibilité

- `prefers-reduced-motion` → animations désactivées, état final statique.
- Contraste WCAG AA garanti par overlay sur le hero.
- CTAs : `<a>` ou `<button>` avec labels explicites.
- Widgets démo : `aria-hidden="true"` sur les éléments d'animation, mais le **texte des KPI/avis reste lisible** par les lecteurs d'écran.

### i18n bloc `pricing.*`

- `pricing.hero.title` — *"Votre salon, augmenté"*
- `pricing.hero.subtitle`
- `pricing.hero.ctaPrimary` — *"Lancer mon salon"*
- `pricing.hero.ctaSecondary` — *"Voir la démo"*
- `pricing.demo.title` — *"Vos chiffres prennent vie"*
- `pricing.demo.subtitle`
- `pricing.features.title` — *"Tout ce dont vous avez besoin"*
- `pricing.features.{vitrine,planning,clients,payments}.{title,bullets}`
- `pricing.plan.title` — *"30 jours gratuits"*
- `pricing.plan.subtitle` — *"Sans engagement, sans carte bancaire"*
- `pricing.plan.cta` — *"Démarrer maintenant"*
- `pricing.finalCta.title` — *"Prêt à lancer votre salon ?"*
- `pricing.finalCta.button` — *"Créer mon compte pro"*

### Performance

- Lighthouse PC : Perf ≥ 80, A11y ≥ 95 (un peu plus tolérant que home à cause du parallax + animations).
- Animations cumulées 60fps stables.
- Image hero parallax : WebP/AVIF, ~150 KB max.

---

## Jalon 5 — Densification pages pro PC + lien partage preview

### Densification PC

Principes appliqués à toutes les pages `/pro/*` :

- Conteneur principal : `max-width: 1440px`, padding latéral responsive (`16px → 32px → 48px`).
- Sur PC ≥ 1024px : exploiter la largeur (split-views, grilles 2-3 col).
- Densité Material : `density: -2` sur PC pour les form fields qui le supportent (`mat-form-field`, `mat-select`). Réduit la hauteur ~20%. À appliquer via class CSS conditionnelle, pas un thème global (pour éviter d'impacter mobile).
- Cards : padding interne uniformisé (16px mobile / 24px PC), border-radius 12px partout.
- Hover states : élévation subtile + bordure rose 1px.
- Headers de page sticky (titre + actions) sous le stepper de l'indicateur.

### Pages à toucher

| Page | Action |
|------|--------|
| `/pro/dashboard` (DRAFT) | Onboarding-wrap : grille personas 3→4 col PC, agrandir cards |
| `/pro/dashboard` (ACTIVE) | Vérifier paddings PC (déjà bien fait sur analytics) |
| `/pro/cares` | Container 1440px, table sur PC / cards mobile |
| `/pro/categories` | Intégrée dans cares |
| `/pro/planning` | Calendrier exploitant largeur PC, sidebar horaires à droite |
| `/pro/bookings` | Liste 1 col → 2 col PC (liste + détail à côté quand sélection) |
| `/pro/employees` | Grille 1 col → 2-3 col PC |
| `/pro/settings` | Container 1440px, formulaires 2 col PC |
| `/pro/salon` | **Split-view live preview** : form à gauche, aperçu vitrine à droite (mode embedded) |

### Split-view `/pro/salon` — live preview

Factoriser `SalonPageComponent` pour exposer un mode `[embeddedPreview]="true"` qui :
- Désactive la navigation interne (les liens `tel:`, mailto:, "Réserver" deviennent visuels seulement).
- Désactive le SSR (le composant est rendu côté client uniquement quand utilisé en embedded).
- Réduit certains paddings pour s'adapter à la zone moitié-écran.

Le composant `salon-profile.component.ts` (page `/pro/salon`) consomme ses propres signaux de formulaire et passe les valeurs en preview au composant embedded au fur et à mesure (debounced ~300ms pour éviter le thrashing).

### Lien partage preview signé

**Backend** :
- Nouvelle entité `SalonPreviewToken` (id, tenantId, token, createdAt, expiresAt nullable, revokedAt nullable).
- Endpoints pro authentifiés :
  - `POST /api/pro/salon/preview-tokens` → crée et renvoie le token + lien complet
  - `GET /api/pro/salon/preview-tokens` → liste tokens actifs
  - `DELETE /api/pro/salon/preview-tokens/{id}` → révoque
- Le contrôleur public (`GET /api/public/salons/{slug}`) accepte un query `?preview=<token>` : valide existence + non-révoqué + non-expiré + appartient au tenant du slug.

**Frontend** :
- Section "Partager un aperçu" dans `/pro/salon` ou `/pro/settings` (à décider en plan d'implémentation).
- Liste des tokens actifs avec bouton "Copier le lien" et "Révoquer".
- Bouton "Générer un lien" → POST + affichage du lien généré.
- La bannière de preview lit le query `?preview=<token>` si présent.

### Pas dans ce jalon

- Pas de refonte des modales pro PC (sortie en spec dédiée).
- Pas de refonte des composants `crud-table` ou `dynamic-form` partagés (impact trop large).
- Pas de refonte des pages clientes.

---

## Décisions techniques transversales

### `DashboardStore` élevé au niveau `ProShellComponent`

Permet à toutes les pages pro d'accéder à `readiness`. Une seule requête `loadReadiness` par session pro (au lieu d'une par page).

### Pas de lib d'animation externe

CSS + signals + `requestAnimationFrame` + `IntersectionObserver` suffisent pour tout (carrousel flip, parallax, démo widgets, count-up). Cohérent avec la philosophie zoneless du projet, et garde le bundle léger.

### SSR-safe partout

Toutes les animations / interactions clientes ont un état initial statique (final) rendu en SSR. L'animation ne se joue qu'après hydration. Pas de saut visuel.

### `prefers-reduced-motion` respecté

Sur tous les sujets : carrousel flip, parallax hero, démo widgets, count-ups, transitions du stepper.

### Service `GeocodingService` factorisé

`geocodeAddress` + cache de `home.ts` extraits dans `core/services/geocoding.service.ts` pour réutilisation par carrousel + future page `/discover`.

### Service `OnboardingChecklistService` factorisé

Calcul des étapes et de la progression centralisé. Consommé par `pro-dashboard.component.ts` (remplace les computed actuels) ET par `OnboardingIndicatorComponent`.

---

## Dépendances externes

- **Asset vidéo hero** (J3) : placeholder Pexels/Coverr en attendant un asset propriétaire.
- **Stripe / tarifs réels** (J4) : page `/pricing` shippe avec un placeholder. 2e itération après `project_pending_payments.md`.
- **Image parallax `/pricing`** (J4) : à fournir ou stock libre de droits.

## Risques identifiés

- **Vidéo hero pèse sur LCP mobile** : mitigé par le rendu `<img>` seul sur mobile.
- **Carrousel flip 60fps sur Safari mobile** : à vérifier en J3 sur device réel. Plan B si besoin : désactiver le flip sur mobile (la map serait alors un popup classique).
- **Élévation `DashboardStore`** : modifie le contrat de toutes les pages pro. Vérifier qu'aucune page ne re-provide localement par accident.
- **Densification PC J5** touche beaucoup de pages : risque de scope creep. Limiter strictement à la liste donnée, pas de bonus.

## Ce qui suit

Plan d'implémentation détaillé pour chacun des 5 jalons (skill `writing-plans`). Chaque jalon = un PR ou groupe de PRs livrable indépendamment.
