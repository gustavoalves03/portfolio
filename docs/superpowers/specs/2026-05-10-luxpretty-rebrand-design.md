# LuxPretty — Rebrand de Pretty Face

**Date :** 2026-05-10
**Statut :** Design validé, prêt pour plan d'implémentation
**Auteur :** Gustavo + Claude (brainstorming)

## Contexte & motivation

L'application "Pretty Face" est destinée au marché luxembourgeois. Renommer l'app en **LuxPretty** :
- Ancre l'identité géographique (Luxembourg)
- Crée un jeu de mots "Lux" (luxe + Luxembourg) qui renforce le positionnement haut de gamme
- Conserve la racine "Pretty" pour la continuité de marque

En parallèle, un défaut UX du header est corrigé : aujourd'hui sur les pages salon ou en mode pro, le nom du salon remplace complètement le logo, privant le client de toute navigation vers l'accueil de la marketplace.

## Objectifs

1. Rebrander complètement l'application : code, packages, configs, UI, i18n, documentation
2. Permettre au client de revenir à l'accueil LuxPretty depuis n'importe quelle page (y compris salon/pro)
3. Conserver la palette rose existante, ajouter un accent or discret pour évoquer le luxe

## Non-objectifs

- Refonte de la palette de couleurs (rose nacré conservé)
- Refonte du système de composants Material/Tailwind
- Migration de la base de données (les schémas/tables ne contiennent pas le nom)
- Réécriture de l'historique git
- Refactor des artefacts legacy (`_bmad/`, `mockup-*.html` à la racine)

---

## 1. Identité visuelle "LuxPretty"

### Logo

Composition deux-mots avec contraste typographique fort :

| Partie | Police | Style | Rôle |
|---|---|---|---|
| **LUX** | Cormorant Garamond | UPPERCASE, weight 500, tracking ~0.25em | Sobriété, luxe, ancrage |
| **Pretty** | Italiana (ou Cormorant Italic) | Italic fin, weight 400, capitalize | Délicat, manuscrit, beauté |

- Espacement inter-mots : ~12px
- Baseline alignée
- Pas de séparateur graphique entre les deux mots

### Variants du composant `<lp-logo>`

| Variant | Usage | Taille |
|---|---|---|
| `default` | Header desktop public, footer | ~24px hauteur |
| `small` | Header gauche en mode salon/pro | ~17px hauteur (~70%) |
| `with-tagline` | Home hero, footer | logo + "Beauté · Luxembourg" en or 9px |

### Couleurs

- **Palette rose conservée** : tous les tokens `--mat-sys-*` actuels restent inchangés
- **Nouvel accent or** : `--lp-accent-gold: #C9A961`
- **Usage de l'or** (parcimonieux) :
  - Soulignement décoratif sous "Pretty" sur logo home (filet 1px, opacity 40%)
  - Hover du logo : "LUX" passe en or doux pendant 200ms
  - Tagline `Beauté · Luxembourg` en or
- **Pas d'or** dans les CTA, badges, états actifs — la primary rose reste seule décideuse

### Tagline

`Beauté · Luxembourg` — affichée uniquement sur la home et le footer, jamais dans le header (déjà chargé).

### Favicon

Monogramme `LP` en serif majuscules sur fond nacré rose pâle.

---

## 2. Header refondu

### Comportement actuel (à corriger)

```
Public :  [≡] [discover] [about]    PrettyFace        [🔔] [👤]
Salon :   [≡] [discover] [about]    Le Salon Rose     [🔔] [👤]
                                    ↑ remplace le logo, plus de retour home
```

### Nouveau comportement

**Pages publiques (home, discover, about, etc.)** — inchangé visuellement, juste rebrandé :
```
[≡] [discover] [about]      LUX Pretty       [🔔] [👤]
                            ↗ /
```

**Pages salon (visiteur ou pro)** — desktop :
```
[≡]  LUX Pretty             Le Salon Rose    [🔔] [👤]
     ↗ / (small variant)    ↗ /salon/{slug}
```

**Pages salon — mobile (<640px)** :
```
[←]  Le Salon Rose          [🔔] [👤]
     (logo small masqué, sidenav burger garde "Accueil")
```

### Logique d'implémentation

- Le `computed` `headerBrand()` continue de retourner `{name, slug, isPro} | null`
- Quand `headerBrand()` est non-null **et** desktop : afficher `<lp-logo variant="small">` dans la colonne gauche, lien vers `/`
- Les liens texte "Discover/About" sont retirés en mode salon/pro (place pour le logo small) — restent accessibles via la sidenav
- Aria-label sur le logo small : i18n key `nav.backToHome` ("Retour à l'accueil LuxPretty" / "Back to LuxPretty home")

### Mode pro complet (`pro-shell`)

À vérifier au moment de l'implémentation : si `pro-shell` réutilise le header global, traitement automatique. Sinon, ajouter le logo small dans le shell.

---

## 3. Périmètre du renommage (big bang, 1 PR)

### Backend Java

- **Package** : `com.prettyface.app` → `com.luxpretty.app` (289 fichiers)
- **Refactor IDE** (IntelliJ "Rename Package") gère imports + `pom.xml` `<groupId>` + tests
- **Dossiers physiques** :
  - `backend/src/main/java/com/prettyface/` → `backend/src/main/java/com/luxpretty/`
  - `backend/src/test/java/com/prettyface/` → `backend/src/test/java/com/luxpretty/`
- **Configs** :
  - `application.properties` : `spring.application.name=pretty-face` → `lux-pretty`
  - `pom.xml` : `<artifactId>` et `<name>` → `lux-pretty`
  - `Dockerfile` (si présent) : nom de l'image
- **Vérification post-refactor** : `grep -r "com.prettyface" backend/` doit retourner 0 résultat

### Base de données

- ✅ Aucun schéma/table nommé `prettyface` (vérifié dans Flyway)
- ✅ OAuth callbacks utilisent `{baseUrl}` dynamique → pas impactés
- ⚠️ Si l'utilisateur Oracle en prod est nommé `PRETTYFACE`, on **ne touche pas** (juste un user DB, sans impact applicatif)

### Frontend

- **Code & dossiers** :
  - `frontend/package.json` : `"name": "app"` → `"luxpretty-web"` (cosmétique)
  - Aucun dossier `prettyface` côté frontend (vérifié)
- **Nouveau composant** : `frontend/src/app/shared/uis/lp-logo/`
  - `lp-logo.component.ts` + `.html` + `.scss` + `.spec.ts`
  - Input `variant: 'default' | 'small' | 'with-tagline'`
  - Styles encapsulés (Cormorant + Italiana via Google Fonts)
- **Textes visibles à remplacer** :
  - `frontend/public/i18n/fr.json` et `en.json` — toutes occurrences
  - `header.html` : remplacer `<span class="brand-pretty">` + `<span class="brand-face">` par `<lp-logo>`
  - `salon-page-pc.component.html:195` : "Pretty Face ✿" → "LuxPretty ✿"
  - `about.html:3` : "Bienvenue chez Pretty Face" → "Bienvenue chez LuxPretty"
  - `register.component.spec.ts:14` : strings de test
  - `salon-posts-viewer.component.ts:943` : `navigator.share({ title: 'Pretty Face' })` → `'LuxPretty'`
  - `transloco-http.loader.ts:26,78` : fallback `app.title`
- **Styles** :
  - Classes CSS `brand-pretty` / `brand-face` supprimées (composant remplace)
  - Nouveau token `--lp-accent-gold: #C9A961` dans `styles.scss`
- **Métadonnées** :
  - `frontend/src/index.html` : `<title>` + meta description
  - Favicon `/favicon.ico` régénéré (monogramme LP)
  - Manifest PWA si présent

### Documentation

- `CLAUDE.md` : "**Pretty Face** - Beauty salon..." → "**LuxPretty**"
- `OAUTH2_SETUP.md` : références au nom de l'app
- `AGENTS.md` : références si présentes
- `README.md` (~12 octets, vide en pratique)

### Hors scope

- `_bmad/`, `_bmad-output/`, `mockup-*.html` à la racine : artefacts de design legacy
- Mémoire auto (`memory/`) : contexte historique utilisateur, non touché
- Historique git : non réécrit
- `uploads/` : photos clientes, RAS

---

## 4. Stratégie d'exécution

### Ordre d'exécution (1 PR)

**Étape 1 — Backend Java refactor** (le plus risqué, en premier)
1. IntelliJ : Rename Package `com.prettyface.app` → `com.luxpretty.app`
2. Update `pom.xml` (artifactId, name)
3. Update `application.properties` (`spring.application.name`)
4. `mvn clean test` — tous tests passent
5. `mvn spring-boot:run` + smoke test API (login, list cares, create booking)

**Étape 2 — Frontend logo & header**
1. Créer `<lp-logo>` (`shared/uis/lp-logo/`) avec 3 variants
2. Importer Cormorant Garamond + Italiana via Google Fonts dans `styles.scss`
3. Ajouter token `--lp-accent-gold`
4. Refondre `header.html` :
   - Centre : `<lp-logo variant="default">` à la place du bloc `brand-pretty/brand-face`
   - Gauche (nouveau) : `@if (headerBrand()) { <lp-logo variant="small" class="hide-on-mobile">` }`
5. Vérifier `pro-shell.component.html` et appliquer le même traitement si header séparé

**Étape 3 — i18n & textes hardcodés**
1. `fr.json` et `en.json` : remplacer "Pretty Face" → "LuxPretty"
2. Ajouter clé `nav.backToHome` (FR + EN)
3. Remplacer les hardcodes (`about.html`, `salon-page-pc.html`, `salon-posts-viewer.ts`, `transloco-http.loader.ts`)
4. `index.html` : `<title>` + meta description
5. `register.component.spec.ts` : strings de test

**Étape 4 — Doc & assets**
1. `CLAUDE.md`, `OAUTH2_SETUP.md`, `AGENTS.md` : remplacer occurrences
2. Favicon : générer monogramme `LP`

**Étape 5 — Validation**
1. `mvn test` (backend) + `npm test` (frontend)
2. `npm start` + parcours visuel manuel : home → salon → mode pro → retour home via logo small
3. `grep -r "Pretty Face\|prettyface\|PrettyFace" --include="*.{ts,html,scss,java,properties,json,md}"` ne renvoie que des faux positifs documentés
4. Test bilingue : switch FR/EN, vérifier toutes les pages

### Tests à ajouter / mettre à jour

- `lp-logo.component.spec.ts` (nouveau) : les 3 variants rendent correctement
- `header.spec.ts` :
  - nouveau test "displays small logo when on salon page"
  - nouveau test "small logo navigates to /"
  - tests existants mentionnant "Pretty Face" : adapter aux nouveaux strings

### Risques & mitigations

| Risque | Mitigation |
|---|---|
| Refactor IDE rate des références (strings, reflection) | `grep com.prettyface` après refactor → doit retourner 0 |
| OAuth Google consent screen affiche "Pretty Face" | **Action manuelle post-merge** : renommer dans Google Cloud Console (documenté dans le PR) |
| Cache navigateur garde l'ancien favicon | Hash dans le nom de fichier + force refresh |
| Mémoire auto utilisateur référence "Pretty Face" | Non touchée — préserve le contexte historique |
| Branche en cours mergée pendant le rebrand | Faire le rebrand quand `main` est stable, sans feature en cours |

### Estimation

| Étape | Durée |
|---|---|
| Backend refactor | 30min IDE + 30min validation |
| Frontend logo + header | 2h |
| i18n + hardcodes | 1h |
| Doc + favicon | 30min |
| Validation finale | 30min |
| **Total** | **~5h sur une journée** |

### Actions manuelles post-merge

1. Renommer "Pretty Face" → "LuxPretty" dans **Google Cloud Console** → OAuth consent screen
2. Mettre à jour les variables d'env Postmark (si nom expéditeur configuré, à venir avec PR mail outbox)
3. Vérifier les sender names dans les emails transactionnels

---

## Validation

- [x] Identité visuelle validée (palette rose + accent or)
- [x] Comportement header validé (logo small desktop, masqué mobile)
- [x] Périmètre validé (big bang complet, 1 PR)
- [x] Stratégie d'exécution validée
- [ ] Plan d'implémentation à rédiger via `superpowers:writing-plans`
