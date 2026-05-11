# Security roadmap — LuxPretty

**Date :** 2026-05-11
**Statut :** Roadmap validée, PR1 entré en exécution

## Contexte

LuxPretty est une app SaaS publique avec des endpoints sensibles (register, login, booking, posts, uploads). Aujourd'hui rien ne protège contre :

- Création massive de faux comptes par bots
- Brute-force / credential-stuffing sur le login
- DDoS qui sature le backend ou la bande passante
- Spam de bookings, posts, cares
- Upload malveillant (XSS via SVG, malware)

État actuel : JWT auth + Spring Security + CSRF cookie-based + CORS + BCrypt + OAuth2 Google. Pas de rate limit, pas de captcha, pas de WAF, pas de security headers, pas de account lockout.

## Vision : défense en profondeur, 4 couches

```
┌──────────────────────────────────────────────────┐
│ Couche 4 — Cloudflare proxy                      │  DDoS, WAF, Turnstile, Bot Fight Mode
│ (gratuit, edge protection)                       │
└────────────────┬─────────────────────────────────┘
                 ▼
┌──────────────────────────────────────────────────┐
│ Couche 3 — Spring filter (Bucket4j rate limit)   │  Per-IP / per-user throttling
│ (application-level)                              │
└────────────────┬─────────────────────────────────┘
                 ▼
┌──────────────────────────────────────────────────┐
│ Couche 2 — Spring Security + métier              │  Captcha vérif, account lockout, email confirm
│ (per-endpoint validation)                        │
└────────────────┬─────────────────────────────────┘
                 ▼
┌──────────────────────────────────────────────────┐
│ Couche 1 — Domaine                               │  Audit log, business invariants, GDPR
│ (entities, services)                             │
└──────────────────────────────────────────────────┘
```

## Stack ciblée

**Cloudflare proxy devant un VPS Spring Boot.** Cloudflare offre gratuitement DDoS L3/L4/L7, WAF managed ruleset, Turnstile captcha (no-cookie, RGPD-friendly), Bot Fight Mode, HSTS auto. Le backend ajoute les protections fines (rate limit, lockout) pour défense en profondeur si Cloudflare est bypassé (origin IP leak, attaque interne).

## Les 6 chantiers, par priorité ROI

| # | Chantier | Menace couverte | Effort | Priorité |
|---|---|---|---|---|
| 1 | Cloudflare proxy + Turnstile captcha | DDoS, bots register, scraping | 2h infra + 3h code | 🔴 P0 |
| 2 | Bucket4j rate limiting backend | Brute-force login, abuse register, spam posts | ~1 jour | 🔴 P0 |
| 3 | Account hardening (lockout + email confirmation) | Brute-force ciblé, fake accounts | ~1 jour | 🟠 P1 |
| 4 | HTTP security headers (CSP, HSTS, X-Frame-Options) | XSS, clickjacking, mixed content | ~0.5 jour | 🟠 P1 |
| 5 | Upload hardening (magic bytes, taille stricte, antivirus) | Upload malveillant, XSS via SVG, abus stockage | ~1 jour | 🟡 P2 |
| 6 | Logging audit + alerting (Sentry/Grafana) | Détection d'anomalie | ~1 jour | 🟡 P2 |

## Pourquoi cet ordre

1. **Cloudflare en 1er** : infra, n'introduit aucun risque dans le code, couvre énormément de surface gratuitement.
2. **Rate limit backend en 2e** : protège même si Cloudflare est bypassé.
3. **Account hardening en 3e** : protège même quand le rate limit laisse passer (5 tentatives login/min × 24h = 7200 tries possibles → on doit lock l'account après ~10).
4. **Headers HTTP en 4e** : faciles à ajouter, durcissent contre XSS/clickjacking, certains sont déjà offerts par Cloudflare proxy.
5. **Upload hardening** : moins urgent tant que `/api/posts` reste authentifié (l'auth limite déjà l'abus).
6. **Audit/alerting** : prérequis à toute réponse à incident.

## Ce qu'on a déjà (à NE PAS refaire)

- ✅ JWT auth + Spring Security
- ✅ CSRF cookie-based (`SecurityConfig.java`)
- ✅ CORS configuré
- ✅ Password hashing BCrypt
- ✅ OAuth2 Google (offre une porte d'entrée "sans mot de passe" déjà sécurisée)

## Hors scope global

- WAF managed cher (Cloudflare Enterprise, AWS WAF) — overkill au stade actuel
- Cloudflare Workers / Tunnels — pas justifiés
- mTLS interne / Zero-trust — overkill pour une app web publique
- DAST/SAST automatisés en CI — utile mais nice-to-have, post-launch

## Liens vers les specs détaillées

- **PR1 — Cloudflare + Turnstile** : `2026-05-11-cloudflare-turnstile-design.md` (entré en exécution 2026-05-11)
- PR2 — Bucket4j rate limiting : à écrire
- PR3 — Account hardening : à écrire
- PR4 — Security headers : à écrire
- PR5 — Upload hardening : à écrire
- PR6 — Audit + alerting : à écrire

## Suivi backlog

Les items 1-6 remplacent (ou complètent) les entrées correspondantes dans `memory/project_prod_readiness.md` :

- Backlog #9 "No rate limiting" → couvert par PR2
- Backlog #16 "HTTP security headers" → couvert par PR4
- Backlog #11 "Upload validation partial" → couvert par PR5
- Nouveau PR1, PR3, PR6 (pas dans la backlog initiale)
