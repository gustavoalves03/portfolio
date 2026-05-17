# Spec — Documents légaux (CGU, CGV pros, Confidentialité, Mentions légales, Cookies) + Bandeau cookies

**Date :** 2026-05-17
**Status :** Design validé, prêt pour writing-plans
**Auteur :** Gustavo + Claude (brainstorming)

---

## 1. Contexte et objectif

LuxPretty est en production sur `https://luxpretty.lu`. Le footer mentionne "Mentions légales · Confidentialité · Cookies" mais **aucune page légale n'existe**. Les checkbox de consentement dans `register` et `auth-modal` pointent vers des routes `/cgu` et `/confidentialite` mortes. Aucun bandeau cookies n'est affiché alors que le RGPD UE et la loi luxembourgeoise s'appliquent.

**Objectif :** livrer un socle juridique complet et fonctionnel pour LuxPretty :
- 5 pages légales accessibles (CGU, CGV pros, Politique de confidentialité, Mentions légales, Politique cookies)
- Un bandeau cookies discret non bloquant
- Footer mis à jour avec liens fonctionnels
- Textes opposables FR + EN, prêts à être relus par un juriste LU avant facturation réelle

**Hors scope :**
- Tracking analytics (aucun ajout)
- Modal de consentement complet accept/refuser/configurer (inutile vu qu'on n'a aucun cookie non nécessaire)
- Versionnage en base de données des CGU acceptées par utilisateur (V2 future)
- Refonte du flux consentement existant (les checkbox actuelles restent telles quelles)

---

## 2. Décisions structurantes

| Sujet | Décision |
|---|---|
| Juridiction | Luxembourg + RGPD UE — tribunaux Luxembourg-Ville |
| Entité éditrice | Projet personnel (pas d'entité formelle) — placeholders `[À COMPLÉTER]`, bannière "statut en cours d'immatriculation" sur mentions légales |
| Rôle LuxPretty | Pure plateforme de mise en relation — non partie au contrat de soin |
| Rétractation pros | Aucune (B2B strict). Essai 7 jours sert de test. Période payée non remboursable |
| Tracking analytics | Aucun — bandeau d'information non bloquant suffisant |
| Langues | FR + EN |
| Versioning docs | Version unique avec date de mise à jour affichée |
| Acceptation CGU | Checkbox existant, pas de stockage en base pour l'instant |

---

## 3. Architecture frontend

### 3.1 Arborescence

```
frontend/src/app/pages/legal/
├── legal-layout/
│   ├── legal-layout.component.ts        # standalone, slot via ng-content
│   ├── legal-layout.component.html      # h1 titre, "Dernière mise à jour : ..."
│   └── legal-layout.component.scss
├── cgu/
│   ├── cgu-page.component.ts
│   └── cgu-page.component.html
├── cgv/
│   ├── cgv-page.component.ts
│   └── cgv-page.component.html
├── privacy/
│   ├── privacy-page.component.ts
│   └── privacy-page.component.html
├── legal-notice/
│   ├── legal-notice-page.component.ts
│   └── legal-notice-page.component.html
└── cookies/
    ├── cookies-page.component.ts
    └── cookies-page.component.html

frontend/src/app/shared/components/cookie-banner/
├── cookie-banner.component.ts
├── cookie-banner.component.html
├── cookie-banner.component.scss
└── cookie-banner.service.ts
```

### 3.2 Routes (ajouts dans `app.routes.ts`)

```ts
{
  path: 'cgu',
  loadComponent: () => import('./pages/legal/cgu/cgu-page.component').then(m => m.CguPageComponent),
},
{
  path: 'cgv',
  loadComponent: () => import('./pages/legal/cgv/cgv-page.component').then(m => m.CgvPageComponent),
},
{
  path: 'confidentialite',
  loadComponent: () => import('./pages/legal/privacy/privacy-page.component').then(m => m.PrivacyPageComponent),
},
{
  path: 'mentions-legales',
  loadComponent: () => import('./pages/legal/legal-notice/legal-notice-page.component').then(m => m.LegalNoticePageComponent),
},
{
  path: 'cookies',
  loadComponent: () => import('./pages/legal/cookies/cookies-page.component').then(m => m.CookiesPageComponent),
},
```

Toutes publiques, pas de guard. Lazy loading pour ne pas alourdir le bundle initial.

### 3.3 `LegalLayoutComponent`

Composant simple, standalone, projection de contenu via `<ng-content>`. Inputs :
- `titleKey: string` (clé i18n)
- `updatedAt: string` (date ISO, formatée selon locale en template)

Template approximatif :

```html
<main class="legal-page">
  <article class="legal-content">
    <h1>{{ titleKey | transloco }}</h1>
    <p class="legal-updated-at">
      {{ 'legal.common.lastUpdated' | transloco }} {{ updatedAt | date:'longDate' }}
    </p>
    <ng-content></ng-content>
  </article>
</main>
```

Style : lecture confortable (max-width ~720px, line-height généreux), respect du thème Material `--mat-sys-*`.

### 3.4 Chaque page légale

Patron identique :

```ts
@Component({
  standalone: true,
  selector: 'app-cgu-page',
  imports: [TranslocoPipe, LegalLayoutComponent],
  templateUrl: './cgu-page.component.html',
})
export class CguPageComponent {
  readonly updatedAt = '2026-05-17';
}
```

Template :

```html
<app-legal-layout titleKey="legal.cgu.title" [updatedAt]="updatedAt">
  @for (section of sections; track section.key) {
    <section class="legal-section">
      <h2>{{ 'legal.cgu.sections.' + section.key + '.title' | transloco }}</h2>
      <div [innerHTML]="'legal.cgu.sections.' + section.key + '.body' | transloco"></div>
    </section>
  }
</app-legal-layout>
```

`sections` est un `readonly sections` figé dans le composant (ordre de l'index). L'`innerHTML` est sanitizé par Angular automatiquement → on autorise `<p>`, `<ul>`, `<li>`, `<strong>`, `<em>`, `<a>` dans les traductions.

---

## 4. Bandeau cookies

### 4.1 Variante choisie : A — strip bas pleine largeur sombre

- Fond sombre `rgba(17,24,39,.95)` avec texte blanc
- Plein largeur, position `fixed; bottom:0; left:0; right:0`
- z-index élevé (au-dessus du contenu mais sous les modales `cdk-overlay` 1000+) → utiliser `z-index: 900`
- Hauteur ~52px desktop, wrap mobile
- Texte + lien "En savoir plus" (`routerLink="/cookies"`) + bouton "J'ai compris"
- Animation d'entrée slide-up 200ms, slide-down 150ms à la fermeture

### 4.2 Service `CookieBannerService`

```ts
@Injectable({ providedIn: 'root' })
export class CookieBannerService {
  private readonly STORAGE_KEY = 'lp_cookie_banner_v1';
  private readonly platformId = inject(PLATFORM_ID);
  readonly dismissed = signal<boolean>(this.loadInitial());

  private loadInitial(): boolean {
    if (!isPlatformBrowser(this.platformId)) return true;  // SSR: ne pas afficher
    return localStorage.getItem(this.STORAGE_KEY) === 'dismissed';
  }

  dismiss(): void {
    if (!isPlatformBrowser(this.platformId)) return;
    localStorage.setItem(this.STORAGE_KEY, 'dismissed');
    this.dismissed.set(true);
  }
}
```

**Versioning :** suffixe `v1` dans la clé. Si la politique change matériellement, bumper à `v2` → bandeau réapparaît automatiquement pour tous.

**SSR :** `dismissed` initialisé à `true` côté serveur pour éviter un flash du bandeau pendant l'hydratation. Au mount client, un `effect()` ou un `afterNextRender` recalcule l'état.

### 4.3 Composant `CookieBannerComponent`

Standalone, déclaratif. Template :

```html
@if (!service.dismissed()) {
  <div class="cookie-banner" role="region" [attr.aria-label]="'cookieBanner.ariaLabel' | transloco">
    <p class="cookie-banner__message">
      🍪 {{ 'cookieBanner.message' | transloco }}
      <a routerLink="/cookies" class="cookie-banner__link">
        {{ 'cookieBanner.learnMore' | transloco }}
      </a>
    </p>
    <button
      type="button"
      class="cookie-banner__btn"
      (click)="service.dismiss()"
      [attr.aria-label]="'cookieBanner.dismiss' | transloco"
    >
      {{ 'cookieBanner.dismiss' | transloco }}
    </button>
  </div>
}
```

### 4.4 Mount dans l'app

Dans `app.component.html`, juste avant `<app-footer>` (ou équivalent) :

```html
<app-cookie-banner></app-cookie-banner>
```

Ajouter `CookieBannerComponent` aux `imports` du composant racine.

---

## 5. Footer

Localiser le composant footer dans `frontend/src/app/shared/layout/` (à confirmer pendant l'implémentation).

**Avant :**
```html
<span>{{ 'footer.legal' | transloco }}</span>  <!-- "Mentions légales · Confidentialité · Cookies" -->
```

**Après :**
```html
<nav class="footer-legal-links" [attr.aria-label]="'footer.legalNav' | transloco">
  <a routerLink="/cgu">{{ 'footer.links.cgu' | transloco }}</a>
  <a routerLink="/cgv">{{ 'footer.links.cgv' | transloco }}</a>
  <a routerLink="/confidentialite">{{ 'footer.links.privacy' | transloco }}</a>
  <a routerLink="/mentions-legales">{{ 'footer.links.legalNotice' | transloco }}</a>
  <a routerLink="/cookies">{{ 'footer.links.cookies' | transloco }}</a>
</nav>
```

Séparateurs gérés en CSS (`::after` avec `·`).

---

## 6. Modal pro-signup — clé de consentement

Fichier : `frontend/src/app/shared/modals/pro-signup-modal/pro-signup-modal.component.html` ligne 27.

Actuellement : `proSignup.modal.fields.consent` = *"J'accepte les conditions générales d'utilisation"*.

À remplacer en i18n par : *"J'accepte les **[Conditions Générales d'Utilisation](/cgu)** et les **[Conditions Générales de Vente](/cgv)** de LuxPretty"* (HTML léger via `[innerHTML]`).

Template :

```html
<mat-checkbox required [ngModel]="consent()" (ngModelChange)="consent.set($event)" name="consent">
  <span [innerHTML]="'proSignup.modal.fields.consent' | transloco"></span>
</mat-checkbox>
```

(Aligne avec le pattern déjà utilisé dans `auth.register.consent`.)

---

## 7. Contenu des documents (plans détaillés)

Les textes intégraux seront rédigés directement dans les clés i18n. Voici les **plans à respecter à la lettre**.

### 7.1 CGU — `legal.cgu.*`

1. **Objet et acceptation** — La plateforme, qui l'édite, acceptation au moment de la création de compte
2. **Définitions** — Plateforme, Utilisateur, Client, Pro/Salon, Compte, Contenu, Service
3. **Rôle de LuxPretty (mise en relation pure)** — Clause forte : LuxPretty n'est pas partie au contrat de soin entre client et salon. Aucune garantie sur la qualité, disponibilité, exécution du soin
4. **Inscription et compte** — Conditions d'âge ≥ 16 ans, véracité, sécurité mot de passe, unicité de l'e-mail, vérification e-mail
5. **Engagements de l'utilisateur** — Usage loyal, interdiction contenus illicites/diffamatoires/sexuels/violents, respect d'autrui
6. **Engagements du salon** — Autorisation d'établissement LU requise, exactitude de l'offre (prix, durée, photos), respect des rendez-vous confirmés, gestion no-show côté salon
7. **Contenus utilisateurs (UGC)** — Avis, photos uploadées : l'utilisateur reste titulaire mais accorde à LuxPretty une licence non exclusive d'affichage. Modération possible sans préavis pour contenu signalé
8. **Propriété intellectuelle LuxPretty** — Marque, logo, code, design — tous droits réservés
9. **Limitation de responsabilité** — LuxPretty s'engage à best effort, ne garantit ni la disponibilité 24/7, ni l'absence de bug, ni la qualité du soin (cf. §3). Force majeure
10. **Suspension / résiliation du compte** — Motifs (violation CGU, fraude, abus), procédure (notification, 7 jours pour régulariser sauf urgence), conséquences (perte d'accès, conservation des données selon politique de confidentialité)
11. **Données personnelles** — Renvoi vers `/confidentialite`
12. **Modification des CGU** — Notification 30j avant entrée en vigueur (e-mail + bandeau), acceptation tacite par usage continu après la date d'effet
13. **Droit applicable et juridiction** — Droit luxembourgeois, tribunaux compétents de Luxembourg-Ville. Médiation possible avant action contentieuse
14. **Contact** — `contact@luxpretty.lu`

### 7.2 CGV pros — `legal.cgv.*`

1. **Objet** — Souscription à un abonnement à la plateforme LuxPretty Pro
2. **Qualification B2B explicite** — Le souscripteur agit en qualité de professionnel. **Renoncement légal au droit de rétractation de 14 jours** (non applicable B2B en droit LU)
3. **Description du service** — Accès plateforme, planning, gestion clients, paiements clients, fonctionnalités selon plan souscrit
4. **Prix et facturation** — Prix en EUR HTVA, TVA luxembourgeoise applicable (17% par défaut), paiement carte via Stripe, facture émise à chaque cycle, accessible depuis le compte
5. **Essai gratuit 7 jours** — Sans engagement, conversion automatique en abonnement payant à la fin de la période, e-mail de rappel J-2
6. **Durée et renouvellement** — Mensuel ou annuel selon plan, tacite reconduction à chaque échéance
7. **Conditions d'annulation** — Annulable à tout moment depuis l'espace de gestion (`/pro/settings` → abonnement). L'annulation prend effet à la fin de la période en cours. **La période payée n'est pas remboursable** (B2B)
8. **Défaut de paiement** — En cas d'échec de prélèvement Stripe : 4 tentatives sur 14 jours puis suspension automatique. Restauration en cas de régularisation
9. **Évolution du service et des prix** — LuxPretty peut faire évoluer les fonctionnalités. Préavis 30j par e-mail pour toute hausse de prix ; le pro peut alors annuler sans frais avant entrée en vigueur
10. **Niveau de service** — Best effort, pas de SLA contractuel chiffré. Maintenance planifiée annoncée 48h à l'avance dans la mesure du possible
11. **Responsabilité** — Plafonnée au montant payé par le pro sur les 12 derniers mois. Exclusion des dommages indirects (perte de clientèle, manque à gagner)
12. **Données du pro et réversibilité** — Le pro peut exporter ses données (clients, bookings, soins) à tout moment depuis son espace. Conservation 90j après résiliation puis suppression
13. **Force majeure** — Définition standard
14. **Droit applicable et juridiction** — Droit luxembourgeois, tribunaux Luxembourg-Ville exclusivement compétents

### 7.3 Politique de confidentialité — `legal.privacy.*`

1. **Responsable du traitement** — LuxPretty, contact `privacy@luxpretty.lu`, identité complète marquée `[À COMPLÉTER]` jusqu'à immatriculation
2. **Données collectées** — Énumération précise par catégorie :
   - Compte : e-mail, mot de passe (hashé), nom, prénom, rôle, langue, date de création
   - Profil pro : nom du salon, adresse, téléphone, photos, soins, horaires
   - Bookings : date, soin, client, salon, notes, statut
   - Paiements : géré entièrement par Stripe — LuxPretty ne stocke aucune donnée carte
   - Contenus : avis, photos uploadées, posts
   - Logs techniques : adresse IP, user-agent, horodatage des actions (sécurité, anti-fraude)
3. **Base légale par traitement** —
   - Exécution du contrat : compte, bookings, accès au service
   - Consentement : communications marketing (opt-in séparé, V2)
   - Intérêt légitime : logs sécurité, anti-fraude
   - Obligation légale : facturation, comptabilité (10 ans)
4. **Finalités** — Fournir le service, gérer les paiements, sécurité, lutte contre la fraude, communication transactionnelle (confirmation booking, etc.), respect des obligations légales
5. **Destinataires et sous-traitants** — Liste explicite avec garanties :
   - **Stripe** (Irlande / US) — paiement, SCC + adéquation
   - **Postmark** (US) — e-mails transactionnels, SCC
   - **OVH** (France) — hébergement serveur et base de données
   - **Cloudflare** (US) — CDN et protection DDoS, SCC
   - **Cloudflare R2** (UE option) — stockage des images uploadées
   - **Google** (US) — OAuth login optionnel, SCC
   - **Facebook / Meta** (US) — OAuth login optionnel (prévu), SCC
6. **Transferts hors UE** — Identification claire des transferts (Stripe US, Postmark US, Cloudflare US, Google/Meta US). Garanties : clauses contractuelles types (SCC) approuvées par la Commission européenne
7. **Durées de conservation** —
   - Compte actif : jusqu'à suppression demandée
   - Compte inactif (≥ 3 ans sans connexion) : suppression automatique
   - Données de facturation : 10 ans (obligation comptable LU)
   - Logs techniques : 6 mois
   - Données pro après résiliation abonnement : 90 jours puis suppression
8. **Droits RGPD** — Accès, rectification, effacement, portabilité, opposition, limitation, retrait du consentement, directives post-mortem
9. **Modalités d'exercice** — E-mail à `privacy@luxpretty.lu` avec justificatif d'identité, délai de réponse 1 mois (extensible à 3 mois si demande complexe)
10. **Réclamation** — Auprès de la Commission Nationale pour la Protection des Données (CNPD) — 15 boulevard du Jazz, L-4370 Belvaux, Luxembourg
11. **Sécurité** — Chiffrement HTTPS en transit, mots de passe hashés (BCrypt), accès aux données limité au strict nécessaire, journalisation des accès admin
12. **Mineurs** — Inscription réservée ≥ 16 ans, accord parental requis sinon, suppression de compte sur signalement
13. **Modifications** — Notification 30j avant entrée en vigueur des changements matériels (e-mail + bandeau)

### 7.4 Mentions légales — `legal.notice.*`

**Bannière en haut de page** (composant inline) :
> ⚠️ LuxPretty est actuellement un projet en pré-lancement opéré à titre personnel. La société éditrice est en cours d'immatriculation.

1. **Éditeur du site** — Nom, statut juridique `[À COMPLÉTER]`, adresse `[À COMPLÉTER]`, e-mail `contact@luxpretty.lu`, n° RCS `[À COMPLÉTER]`, n° TVA `[À COMPLÉTER]`
2. **Directeur de la publication** — Gustavo Alves `[à confirmer]`
3. **Hébergeur** — OVH SAS, 2 rue Kellermann, 59100 Roubaix, France, téléphone +33 9 72 10 10 07, https://www.ovh.com
4. **CDN / sécurité** — Cloudflare, Inc., 101 Townsend St, San Francisco, CA 94107, USA
5. **Conception et développement** — LuxPretty
6. **Crédits photos** — `[À COMPLÉTER selon sources]` (Unsplash, photos pros, etc.)
7. **Signalement de contenu illicite** — `contact@luxpretty.lu`

### 7.5 Politique cookies — `legal.cookies.*`

1. **Qu'est-ce qu'un cookie** — Brève définition (fichier déposé par le site, mémorisation d'informations entre les visites)
2. **Cookies et stockages utilisés sur LuxPretty** — Tableau précis :

   | Nom | Type | Finalité | Durée | Base légale |
   |---|---|---|---|---|
   | `XSRF-TOKEN` | Cookie session | Protection CSRF | Session | Nécessaire |
   | Session Spring (`JSESSIONID` ou équivalent) | Cookie session | Authentification | Session | Nécessaire |
   | `lp_auth_token` | localStorage | Jeton d'authentification | Persistant jusqu'à logout | Nécessaire |
   | `lp_lang` | localStorage | Préférence langue | Persistant | Nécessaire |
   | `lp_cookie_banner_v1` | localStorage | Mémorisation fermeture bandeau | Persistant | Nécessaire |
   | Cookies Stripe (`__stripe_mid`, `__stripe_sid`) | Cookie tiers (stripe.com) | Sécurisation du paiement, prévention fraude | 1 an / session | Nécessaire à l'exécution du paiement |

3. **Aucun cookie analytics ou marketing** — Énoncé explicite : LuxPretty n'utilise aucun outil de mesure d'audience tiers (Google Analytics, Matomo, etc.), aucun pixel publicitaire, aucun cookie de profilage
4. **Comment gérer les cookies** — Réglages navigateur (liens vers aide Chrome, Firefox, Safari, Edge). Avertir que désactiver les cookies nécessaires empêche le fonctionnement du site
5. **Modifications** — Notification 30j en cas d'ajout d'un cookie non strictement nécessaire (qui déclencherait alors un vrai bandeau de consentement)

---

## 8. Traductions

### 8.1 Clés à ajouter dans `fr.json` et `en.json`

```jsonc
{
  "legal": {
    "common": {
      "lastUpdated": "Dernière mise à jour :"
    },
    "cgu": {
      "title": "Conditions Générales d'Utilisation",
      "sections": {
        "objet": { "title": "1. Objet et acceptation", "body": "<p>...</p>" },
        "definitions": { "title": "2. Définitions", "body": "<ul>...</ul>" },
        // ... 14 sections
      }
    },
    "cgv": { ... 14 sections ... },
    "privacy": { ... 13 sections ... },
    "notice": {
      "title": "Mentions légales",
      "preLaunchBanner": "⚠️ LuxPretty est actuellement un projet...",
      "sections": { ... 7 sections ... }
    },
    "cookies": { ... 5 sections + tableau ... }
  },
  "cookieBanner": {
    "message": "Ce site utilise uniquement des cookies nécessaires à son fonctionnement.",
    "learnMore": "En savoir plus",
    "dismiss": "J'ai compris",
    "ariaLabel": "Information sur l'utilisation des cookies"
  },
  "footer": {
    "links": {
      "cgu": "CGU",
      "cgv": "CGV",
      "privacy": "Confidentialité",
      "legalNotice": "Mentions légales",
      "cookies": "Cookies"
    },
    "legalNav": "Liens légaux"
  }
}
```

### 8.2 Strategy de rédaction EN

Pas de traduction mot-à-mot ; rédaction d'une version anglaise équivalente, sobre, juridiquement cohérente. Mention identique de la juridiction LU et de la CNPD.

### 8.3 Suppression / refactor

- Supprimer la clé `footer.legal` actuelle ("Mentions légales · Confidentialité · Cookies")
- Conserver `auth.register.consent` (déjà liée à `/cgu` et `/confidentialite` — fonctionnera dès que les routes existent)
- Mettre à jour `proSignup.modal.fields.consent` pour inclure liens vers CGU **et** CGV

---

## 9. Placeholders à compléter avant production réelle

À traquer dans un commentaire en tête de chaque clé i18n ou dans une liste centralisée :

- [ ] Identité juridique éditeur (raison sociale, statut, adresse)
- [ ] N° RCS Luxembourg
- [ ] N° TVA intracommunautaire
- [ ] Adresse e-mail `privacy@luxpretty.lu` à créer
- [ ] Adresse e-mail `contact@luxpretty.lu` à créer
- [ ] Directeur de la publication confirmé
- [ ] Crédits photos (sources)
- [ ] **Relecture par un juriste LU avant facturation effective** (recommandation forte)

---

## 10. Accessibilité

- Structure sémantique : `<main>`, `<article>`, `<section>`, `<nav>`
- Hiérarchie de titres respectée (h1 unique par page, h2 pour sections, h3 si sous-sections)
- Bandeau cookies : `role="region"`, `aria-label`, bouton focusable, fermable au clavier
- Liens externes : `target="_blank" rel="noopener noreferrer"`
- Contraste WCAG AA respecté sur le bandeau (texte blanc sur fond `#111827`)

---

## 11. SSR / SEO

- Pages légales statiques → idéales pour le SSR
- Ajouter `<title>` et `<meta name="description">` via le `Title`/`Meta` service Angular
- `<link rel="canonical">` pointant vers `https://luxpretty.lu/{cgu|cgv|confidentialite|mentions-legales|cookies}`
- Bandeau cookies : `dismissed = true` initial en SSR → pas de FOUC

---

## 12. Tests

### 12.1 Tests unitaires

- `CookieBannerService` :
  - signal initialement `false` en navigateur sans clé localStorage
  - `dismiss()` set le signal à `true` et écrit dans localStorage
  - garde SSR : initialise à `true` en environnement non-browser
- Chaque page légale :
  - rendu sans crash
  - clés i18n résolues
  - `LegalLayoutComponent` reçoit le bon `titleKey` et `updatedAt`

### 12.2 Tests E2E (Playwright)

- Navigation depuis le footer vers chacune des 5 pages
- Bandeau cookies visible à la première visite, disparu après clic sur "J'ai compris"
- Bandeau ne réapparaît pas au refresh tant que `lp_cookie_banner_v1=dismissed`
- Vérifier que la checkbox de consentement dans `register` et `auth-modal` mène bien à des pages réelles (pas de 404)

### 12.3 Tests visuels

- Bandeau cookies : desktop + mobile (wrap correct, bouton OK accessible)
- Pages légales : lecture confortable, max-width ~720px, marges OK sur mobile

---

## 13. Risques et points d'attention

| Risque | Mitigation |
|---|---|
| Textes non opposables sans relecture juriste | Mention claire dans le spec : relecture LU obligatoire avant facturation. Placeholders explicites |
| Identité éditeur incomplète | Bannière "en cours d'immatriculation" sur mentions légales |
| Cookies Stripe non documentés | Tableau cookies explicite + lien vers politique Stripe |
| Évolution future avec analytics | Versioning bandeau (`v1` → `v2`) + clé localStorage avec suffixe permet de redéclencher l'affichage |
| Liens morts dans `auth.register.consent` aujourd'hui | Implémentation des routes lève le problème |
| HTML dans i18n → risque injection | Angular sanitize `[innerHTML]` par défaut, on n'utilise que des tags whitelist (`<p>`, `<ul>`, `<li>`, `<a>`, `<strong>`, `<em>`) |
| SSR : bandeau visible avant hydratation | `dismissed = true` initial en SSR, recalcul au mount client |

---

## 14. Définition de "terminé"

- [ ] 5 routes accessibles, lazy-loaded, rendu correct FR + EN
- [ ] `LegalLayoutComponent` créé et utilisé par toutes les pages
- [ ] Toutes les sections rédigées (FR + EN) selon les plans §7
- [ ] Placeholders `[À COMPLÉTER]` listés en commentaire dans le spec ET dans une issue/note de suivi
- [ ] Bandeau cookies fonctionnel, dismiss persistant, SSR-safe
- [ ] Footer mis à jour avec 5 liens fonctionnels
- [ ] `proSignup.modal.fields.consent` mis à jour avec liens CGU + CGV
- [ ] Tests unitaires verts (`CookieBannerService` + smoke sur pages)
- [ ] Tests E2E verts (navigation footer + bandeau)
- [ ] `npm run lint` + `npm run build` OK
- [ ] Documentation mise à jour si besoin (CLAUDE.md : ajouter mention rapide des pages légales)

---

## 15. Suite — implementation plan

Après validation de ce spec, invoquer `writing-plans` pour générer le plan d'implémentation détaillé (découpage par PR, ordre des étapes, checkpoints).

Découpage probable (à confirmer en writing-plans) :

- **PR1 — Socle technique** : `LegalLayoutComponent`, `CookieBannerComponent` + service, routes, footer mis à jour, clés i18n vides — valider l'architecture sans le contenu
- **PR2 — Rédaction FR** : tous les textes en français pour les 5 documents
- **PR3 — Traduction EN** : version anglaise des 5 documents
- **PR4 — Polish + E2E + mise à jour `proSignup`** : tests E2E, ajustements UI, mise à jour clé `proSignup.modal.fields.consent`, vérif a11y
