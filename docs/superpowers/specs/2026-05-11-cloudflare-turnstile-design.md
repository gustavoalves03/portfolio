# Cloudflare proxy + Turnstile captcha — PR1 de la security roadmap

**Date :** 2026-05-11
**Statut :** Spec validée, prêt pour plan d'implémentation
**Roadmap parent :** `2026-05-11-security-roadmap.md` (PR1)

## Contexte

Premier chantier de la roadmap sécurité LuxPretty (voir le doc roadmap pour vue d'ensemble). Combine deux protections complémentaires :

- **Cloudflare proxy** (infra) : DDoS, WAF, Bot Fight Mode, HSTS auto — gratuit, niveau réseau/edge.
- **Turnstile captcha** (code) : vérif humaine sur les formulaires sensibles (register, forgot-password), niveau application.

## Objectifs

1. Mettre LuxPretty derrière Cloudflare en production pour neutraliser le gros des attaques DDoS et bot trafic sans payer.
2. Bloquer les bots qui passent à travers Cloudflare en validant un Turnstile token côté backend sur les endpoints critiques.
3. Le tout en gardant un dev local zéro friction (toggle off par défaut).

## Non-objectifs

- Rate limiting fin (PR2)
- Account lockout après N tentatives login (PR3)
- Headers HTTP custom au-delà de ceux fournis par Cloudflare (PR4)
- Migration vers Cloudflare Workers / Tunnels
- Captcha sur `/api/auth/login` — friction trop forte, PR2+PR3 le couvrent suffisamment

---

## 1. Architecture

### Vue d'ensemble

```
Client                    Backend Spring Boot       Cloudflare
  │ load /register          │                          │
  │◄─── widget JS ──────────┼─────────────────────────►│
  │ user submits form       │                          │
  │ POST /api/auth/register │                          │
  │ { ..., captchaToken }   │                          │
  │────────────────────────►│                          │
  │                         │ POST siteverify          │
  │                         │ { token, secret, ip }    │
  │                         │─────────────────────────►│
  │                         │◄─── { success: true } ───│
  │◄─── 201 / 400 ──────────│                          │
```

### Deux composants distincts

**A. Cloudflare proxy (infra)** : Le domaine `luxpretty.lu` (ou ce qui sera choisi) pointe en DNS vers Cloudflare, qui proxifie HTTP(S) vers le VPS. Aucune ligne de code à écrire — toute la config se fait dans le dashboard Cloudflare. Voir `docs/OPS_CLOUDFLARE.md` créé dans cette PR.

**B. Turnstile captcha (frontend + backend)** : Widget JS Cloudflare embarqué dans les formulaires register et forgot-password. Le widget génère un token unique côté client ; le frontend l'envoie au backend dans le body de la requête ; le backend valide via l'API Cloudflare `siteverify`.

### Pourquoi Turnstile et pas reCAPTCHA / hCaptcha

- Gratuit, illimité
- Pas de cookies (RGPD-friendly natif)
- Pas de "clic sur les feux" — invisible la plupart du temps
- Tu es déjà sur Cloudflare (proxy) → même fournisseur = config plus simple

### Récupération vraie IP client derrière Cloudflare

Quand Cloudflare proxifie, le backend voit l'IP Cloudflare comme remote address. **La vraie IP client est dans le header `CF-Connecting-IP`**, mais ce header peut être spoofé si la requête ne vient pas de Cloudflare. Solution : un composant `ClientIpResolver` qui :

1. Lit `CF-Connecting-IP` SI la requête vient d'une IP des ranges Cloudflare officiels (https://www.cloudflare.com/ips/)
2. Sinon fallback `X-Forwarded-For` first hop
3. Sinon fallback `request.getRemoteAddr()`

Ce composant est créé en PR1 (utilisé par `TurnstileVerifier` pour envoyer la vraie IP à Cloudflare lors du siteverify) et réutilisé en PR2 pour le rate limiter.

---

## 2. Cloudflare proxy — étapes manuelles

Pas de code ici, juste de la config dashboard à faire **après merge** par l'opérateur (toi).

1. Créer compte Cloudflare (gratuit), ajouter le domaine.
2. Cloudflare scanne les DNS records → valider → récupérer les 2 nameservers Cloudflare.
3. Chez le registrar (OVH, Gandi…) : remplacer les nameservers actuels. Propagation 1-24h.
4. Dans Cloudflare → DNS : enable proxy (orange cloud) sur les records pointant vers le VPS (`@`, `www`, éventuellement `api`).
5. SSL/TLS → Overview : mode **"Full (strict)"** (requiert un cert Let's Encrypt sur le VPS).
6. SSL/TLS → Edge Certificates : activer "Always Use HTTPS" et "Automatic HTTPS Rewrites".
7. Security → Bots : activer **"Bot Fight Mode"** (gratuit).
8. Security → WAF → Managed Rules : activer le "Cloudflare Free Managed Ruleset".
9. Security → DDoS : laisser sur "Default" (auto).
10. Caching → Configuration : "Standard". Page rule pour **bypass cache sur `/api/*`** (les API ne doivent jamais être cachées).
11. Speed → Brotli compression : activer.

Détails et commandes de vérification dans `docs/OPS_CLOUDFLARE.md`.

---

## 3. Backend — Turnstile verification

### Nouveaux fichiers

| Fichier | Responsabilité |
|---|---|
| `backend/src/main/java/com/luxpretty/app/security/turnstile/TurnstileProperties.java` | `@ConfigurationProperties("app.security.turnstile")` : `secretKey`, `enabled` (Boolean toggle). Fail-fast au boot si `enabled=true` et `secretKey` vide. |
| `backend/src/main/java/com/luxpretty/app/security/turnstile/TurnstileVerifier.java` | Service qui appelle `https://challenges.cloudflare.com/turnstile/v0/siteverify`. Méthode `boolean isValid(String token, String clientIp)`. Timeout 5s. Logge succès/échec (mais JAMAIS le `secretKey`). |
| `backend/src/main/java/com/luxpretty/app/security/turnstile/TurnstileRequired.java` | Annotation Java `@TurnstileRequired` à apposer sur les controller methods. |
| `backend/src/main/java/com/luxpretty/app/security/turnstile/TurnstileAspect.java` | AOP `@Around("@annotation(TurnstileRequired)")` qui extrait le token du body DTO (via réflexion : champ `captchaToken`) ou d'un header HTTP `cf-turnstile-response`, valide via `TurnstileVerifier`, refuse en 400 si invalide. Bypass si `enabled=false`. |
| `backend/src/main/java/com/luxpretty/app/security/ip/ClientIpResolver.java` | Composant utilitaire : extrait la vraie IP client en respectant les headers Cloudflare et les ranges officiels. |
| `backend/src/main/java/com/luxpretty/app/security/ip/CloudflareIpRanges.java` | Constante liste des CIDR Cloudflare publics (v4 et v6). Documentés avec lien source. |
| `backend/src/test/java/com/luxpretty/app/security/turnstile/TurnstileVerifierTests.java` | 5 cas : token valide, token invalide, token expiré, network timeout, disabled flag bypass. Mock du `RestClient`. |
| `backend/src/test/java/com/luxpretty/app/security/turnstile/TurnstileAspectTests.java` | 3 cas : annotation présente → vérif, sans token → 400, disabled → proceed. |
| `backend/src/test/java/com/luxpretty/app/security/ip/ClientIpResolverTests.java` | 4 cas : CF-Connecting-IP depuis range Cloudflare ✓, depuis IP non-Cloudflare ignoré, X-Forwarded-For fallback, RemoteAddr fallback. |

### Modifications

| Fichier | Changement |
|---|---|
| `backend/src/main/java/com/luxpretty/app/auth/dto/RegisterRequest.java` | Add `String captchaToken` field (nullable, ignoré si `turnstile.enabled=false`). |
| `backend/src/main/java/com/luxpretty/app/auth/dto/ProRegisterRequest.java` | Idem. |
| `backend/src/main/java/com/luxpretty/app/auth/dto/ForgotPasswordRequest.java` | Idem. |
| `backend/src/main/java/com/luxpretty/app/auth/AuthController.java` | Annoter `@TurnstileRequired` sur `/register`, `/register/pro`, `/register/client`, `/forgot-password`. **Pas sur `/login`** : trop de friction, PR2 (rate limit) + PR3 (lockout) protègent. |
| `backend/src/main/resources/application.properties` | `app.security.turnstile.enabled=${TURNSTILE_ENABLED:false}` + `app.security.turnstile.secret-key=${TURNSTILE_SECRET_KEY:}` |
| `backend/src/main/resources/application-test.properties` | `app.security.turnstile.enabled=false` (les tests bypass). |
| `.env.example` | Documenter `TURNSTILE_ENABLED=true` (prod) + `TURNSTILE_SECRET_KEY=<from Cloudflare dashboard>` |
| `backend/pom.xml` | Add `spring-boot-starter-aop` si pas déjà là (besoin de l'aspect). |

### Pattern du `TurnstileAspect`

```java
@Around("@annotation(TurnstileRequired)")
public Object verify(ProceedingJoinPoint pjp) throws Throwable {
    if (!properties.isEnabled()) return pjp.proceed();         // dev/test bypass
    String token = extractToken(pjp.getArgs());                // looks for `captchaToken` field in DTOs
    String ip = clientIpResolver.resolve(currentRequest());
    if (!verifier.isValid(token, ip)) {
        throw new ResponseStatusException(BAD_REQUEST, "captcha invalide");
    }
    return pjp.proceed();
}
```

### Sécurité du secret

- `TURNSTILE_SECRET_KEY` jamais commité (utilise env var), jamais loggé (test dédié vérifie l'absence dans `logCaptor`).
- Fail-fast au boot si `enabled=true` et `secretKey` vide.
- En dev local : `enabled=false` par défaut → pas besoin de secret.

---

## 4. Frontend — widget Turnstile

### Nouveaux fichiers

| Fichier | Responsabilité |
|---|---|
| `frontend/src/app/shared/uis/turnstile/turnstile.component.ts` | Standalone component `<app-turnstile>`. Charge dynamiquement `https://challenges.cloudflare.com/turnstile/v0/api.js` une seule fois par session. Rend le widget. Émet le token via `Output<string> verified` quand validé/expiré. Input `siteKey`. Bypass si `siteKey === ''` (dev) : émet `'dev-bypass'` immédiatement. |
| `frontend/src/app/shared/uis/turnstile/turnstile.component.html` | `<div #widget></div>` que le script remplit. |
| `frontend/src/app/shared/uis/turnstile/turnstile.component.spec.ts` | 3 specs : component exists, dev-bypass émet `'dev-bypass'`, charge le script avec siteKey set. Mock `window.turnstile`. |
| `frontend/src/app/core/config/captcha.config.ts` | Export du `CAPTCHA_SITE_KEY` `InjectionToken` lu depuis `environment.captchaSiteKey`. |

### Modifications

| Fichier | Changement |
|---|---|
| `frontend/src/app/pages/auth/register/register.component.{ts,html}` | Ajout `<app-turnstile (verified)="captchaToken.set($event)">` + désactivation submit tant que `captchaToken()` null. Inclusion du token dans payload `RegisterRequest`. |
| `frontend/src/app/pages/auth/register-pro/register-pro.component.{ts,html}` | Idem. |
| `frontend/src/app/pages/auth/forgot-password/forgot-password.component.{ts,html}` | Idem. |
| `frontend/src/environments/environment.ts` | Add `captchaSiteKey: ''` (dev = bypass). |
| `frontend/src/environments/environment.prod.ts` | Add `captchaSiteKey: '0xPLACEHOLDER'` (à remplacer manuellement après création du site Turnstile). |
| `frontend/src/app/app.config.ts` | Provider `{ provide: CAPTCHA_SITE_KEY, useValue: environment.captchaSiteKey }`. |
| `frontend/public/i18n/fr.json` | Add `errors.captcha.required: "Veuillez compléter la vérification"`. |
| `frontend/public/i18n/en.json` | Add `errors.captcha.required: "Please complete the verification"`. |

### Cas spéciaux

- **OAuth2 Google callback** (`/oauth2/redirect`) : pas protégé par Turnstile. Le token vient de Google, et Google a sa propre détection de bot. Pas applicable.
- **SSR** : le composant `<app-turnstile>` charge le script seulement si `isPlatformBrowser(platformId)`. SSR rend un placeholder vide, pas d'erreur.

---

## 5. Documentation

Nouveau fichier : `docs/OPS_CLOUDFLARE.md` qui couvre :
- Les 11 étapes config dashboard Cloudflare (voir section 2)
- Comment obtenir la `TURNSTILE_SITE_KEY` (publique, à mettre dans `environment.prod.ts`) et la `TURNSTILE_SECRET_KEY` (privée, à mettre dans les env vars du serveur)
- Comment vérifier que le proxy fonctionne (`curl -v https://luxpretty.lu/ping` doit montrer le header `CF-Ray` Cloudflare)
- Comment désactiver Turnstile en urgence : `TURNSTILE_ENABLED=false` env var + redéploy backend (30s downtime max)
- Comment vérifier les logs Cloudflare en cas de faux positif Bot Fight Mode

Mise à jour `CLAUDE.md` :
- Une ligne dans "Important Notes" qui mentionne "En prod, le trafic passe par Cloudflare proxy. Le backend lit `CF-Connecting-IP` via `ClientIpResolver`."

---

## 6. Plan d'exécution

Découpage en 3 commits, mergés en une seule PR :

### Commit 1 : backend Turnstile infra (isolé, pas branché)

- Files créés : `TurnstileProperties`, `TurnstileVerifier`, `TurnstileAspect`, `TurnstileRequired`, `ClientIpResolver`, `CloudflareIpRanges` + leurs tests.
- `application.properties`, `application-test.properties`, `pom.xml`, `.env.example`.
- Pas encore branché sur les controllers → zéro régression possible.
- ~2h.

### Commit 2 : wiring backend + frontend component

- Annotations `@TurnstileRequired` sur les controllers.
- `captchaToken` ajouté aux 3 DTOs.
- Composant `<app-turnstile>` + spec.
- Wire dans les 3 formulaires.
- i18n FR/EN.
- `environment.captchaSiteKey` placeholder.
- ~3h.

### Commit 3 : documentation opérationnelle

- `docs/OPS_CLOUDFLARE.md`.
- Update `.env.example`, `CLAUDE.md`.
- ~30 min.

### Validation finale

- `mvn test` : 553 + ~12 nouveaux = ~565 tests, 0 failures, 31 errors baseline préexistantes.
- `npm test` : 600 + 3 nouveaux = 603 tests, 0 failures.
- Build frontend OK.
- Smoke manuel dev (`TURNSTILE_ENABLED=false`) : register fonctionne comme avant, pas de widget visible.

### Actions manuelles post-merge

1. Dashboard Cloudflare → Turnstile → "Add site" → récupère **Site Key** (publique) + **Secret Key** (privée).
2. Mettre à jour les env vars serveur : `TURNSTILE_ENABLED=true`, `TURNSTILE_SECRET_KEY=<secret>`.
3. Mettre à jour `frontend/src/environments/environment.prod.ts` avec la `Site Key`.
4. Effectuer les 11 étapes config Cloudflare (`docs/OPS_CLOUDFLARE.md`).
5. Redéployer backend + frontend.

### Risques & mitigations

| Risque | Mitigation |
|---|---|
| Le widget Turnstile bloque tous les users si clé mal configurée | Toggle `app.security.turnstile.enabled=false` en 1 env var + redéploy 30s. Documenté en haut de `OPS_CLOUDFLARE.md`. |
| Cloudflare Bot Fight Mode génère des faux positifs | Désactivable en 1 clic dans le dashboard. Logs Cloudflare disent ce qui a été bloqué. |
| Le secret Turnstile fuit dans les logs | `TurnstileVerifier` ne logge JAMAIS `properties.secretKey`. Test dédié `LogCaptor` vérifie l'absence. |
| Le captcha casse les tests `AuthFlowIntegrationTests` (21 specs register) | `application-test.properties` force `enabled=false` → aspect bypassé → comportement actuel inchangé. |
| Le widget casse en SSR | `<app-turnstile>` check `isPlatformBrowser` avant de charger le script. SSR rend placeholder vide. |
| Cloudflare proxy ralentit le site | Cloudflare ajoute typiquement +5ms latence + accélère via cache statique. Net positif perf. |

### Estimation totale

~5h30 code + 30 min config Cloudflare manuel = **1 journée de travail**.

---

## Validation

- [x] Architecture (Cloudflare proxy + Turnstile, séparation infra/code) — validée
- [x] Étapes manuelles Cloudflare — validées
- [x] Code backend (verifier + aspect + DTOs + tests) — validé
- [x] Code frontend (widget + 3 formulaires + i18n) — validé
- [x] Plan d'exécution 3 commits — validé
- [ ] Plan d'implémentation à rédiger via `superpowers:writing-plans`
