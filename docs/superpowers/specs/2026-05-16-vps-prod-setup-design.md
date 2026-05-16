# VPS Prod Setup — LuxPretty

**Date:** 2026-05-16
**Status:** Spec — pending user approval
**Owner:** Gustavo

## Goal

Déployer LuxPretty en production sur un VPS Ubuntu LTS avec :
- Stack Docker Compose (Oracle + backend Spring Boot + frontend Angular)
- Reverse proxy Caddy (TLS Let's Encrypt automatique) pour `luxpretty.lu`
- CI/CD via GitHub Actions (push `main` → build images GHCR → deploy SSH)
- Stockage uploads sur Cloudflare R2 (déjà configuré)

## Context

- Repo monorepo : `frontend/` (Angular 20 SSR-ready) + `backend/` (Spring Boot 3.5 / Java 21)
- Dockerfiles déjà présents dans `backend/` et `frontend/`
- `docker-compose.yml` actuel : Oracle + frontend-dev (profil `dev`) + Mailpit — **pas de profil `prod`**
- VPS cible : Ubuntu 22.04/24.04, identifiants dans `.env` (clés `VPS_HOST`, `VPS_USER`, `VPS_SSH_KEY_PATH`, `VPS_PASSWORD`)
- Domaine : `luxpretty.lu` (réservé, DNS à pointer vers VPS)

### ⚠️ Point d'attention — Frontend prod

Le `frontend/Dockerfile` actuel produit un build **statique servi par Nginx** (pas SSR). Conséquences :
- Pas besoin de runtime Node sur le VPS pour le front
- Perte du SSR en prod (SEO/perf initial dégradé vs. dev)
- Architecture simplifiée : Caddy → Nginx (frontend) + Caddy → Spring Boot (backend)

**Décision retenue dans ce spec :** garder l'image statique Nginx existante. Migration vers SSR prod = scope futur séparé.

## Non-Goals

- Migration DB (Oracle Free reste, comme en dev)
- Setup mail SMTP prod (Postmark) — chantier séparé, déjà spec'd dans roadmap mail outbox
- Monitoring/observabilité (APM, logs centralisés) — phase ultérieure
- Backup automatisé Oracle — phase ultérieure (mentionné en risques)
- Migration SSR pour le frontend prod
- Multi-environnements (staging) — uniquement prod ici

## Architecture

```
                       Internet
                          │
                  luxpretty.lu (DNS A → VPS_IP)
                  www.luxpretty.lu (CNAME → luxpretty.lu)
                          │
                  ┌───────▼────────┐
                  │  Caddy (host)  │  ports 80/443
                  │  TLS auto LE   │
                  └───┬──────────┬─┘
              /api/*  │          │  /*
                      │          │
              ┌───────▼──┐    ┌──▼──────────┐
              │ backend  │    │  frontend   │
              │ :8080    │    │  (nginx)    │
              │ Spring   │    │  :80 → :8081│
              └────┬─────┘    └─────────────┘
                   │
              ┌────▼─────┐
              │  oracle  │  port interne uniquement
              │  :1521   │  volume persistant oradata
              └──────────┘

Uploads → Cloudflare R2 (externe)
```

**Réseau Docker :** un network `luxpretty-prod` (bridge), aucun port DB exposé sur l'host. Backend joint Oracle via DNS interne `oracle:1521`.

**Ports exposés sur l'host :**
- `22` (SSH, restreint via UFW + fail2ban)
- `80` (Caddy, redirect HTTPS)
- `443` (Caddy, TLS)

Tous les autres ports (8080 backend, 8081 frontend, 1521 oracle) restent **internes au network Docker** — non exposés publiquement.

## Components

### 1. Caddy (host binaire, pas Docker)

Installé via apt repo officiel Caddy. Service systemd. Configuration `/etc/caddy/Caddyfile` :

```caddyfile
luxpretty.lu, www.luxpretty.lu {
    encode gzip zstd

    handle /api/* {
        reverse_proxy localhost:8080
    }

    handle {
        reverse_proxy localhost:8081
    }

    log {
        output file /var/log/caddy/luxpretty.log
        format json
    }
}
```

Caddy gère TLS Let's Encrypt automatiquement au reload (challenge HTTP-01 sur :80).

### 2. docker-compose.prod.yml

Nouveau fichier à la racine du repo. Services :

```yaml
services:
  oracle:
    image: gvenzl/oracle-free:latest
    container_name: oracle-db
    environment:
      ORACLE_PASSWORD: ${ORACLE_PASSWORD}
      APP_USER: ${APP_USER}
      APP_USER_PASSWORD: ${APP_USER_PASSWORD}
    volumes:
      - oradata:/opt/oracle/oradata
    networks: [luxpretty-prod]
    healthcheck:
      test: ["CMD", "/opt/oracle/healthcheck.sh"]
      interval: 30s
      timeout: 5s
      start_period: 10m
      retries: 80
    restart: unless-stopped

  backend:
    image: ghcr.io/gustavo/luxpretty-backend:latest
    container_name: luxpretty-backend
    ports:
      - "127.0.0.1:8080:8080"  # bind localhost only, accessible only à Caddy
    environment:
      SPRING_PROFILES_ACTIVE: prod
      DB_URL: jdbc:oracle:thin:@oracle:1521/FREEPDB1
      DB_USERNAME: ${APP_USER}
      DB_PASSWORD: ${APP_USER_PASSWORD}
      JWT_SECRET: ${JWT_SECRET}
      OAUTH2_REDIRECT_URI: ${OAUTH2_REDIRECT_URI}
      CORS_ALLOWED_ORIGINS: ${CORS_ALLOWED_ORIGINS}
      FRONTEND_BASE_URL: ${FRONTEND_BASE_URL}
      STORAGE_BACKEND: r2
      R2_ACCOUNT_ID: ${R2_ACCOUNT_ID}
      R2_BUCKET: ${R2_BUCKET}
      R2_ACCESS_KEY_ID: ${R2_ACCESS_KEY_ID}
      R2_SECRET_ACCESS_KEY: ${R2_SECRET_ACCESS_KEY}
      GOOGLE_CLIENT_ID: ${GOOGLE_CLIENT_ID}
      GOOGLE_CLIENT_SECRET: ${GOOGLE_CLIENT_SECRET}
    depends_on:
      oracle:
        condition: service_healthy
    networks: [luxpretty-prod]
    restart: unless-stopped

  frontend:
    image: ghcr.io/gustavo/luxpretty-frontend:latest
    container_name: luxpretty-frontend
    ports:
      - "127.0.0.1:8081:80"
    networks: [luxpretty-prod]
    restart: unless-stopped

networks:
  luxpretty-prod:
    driver: bridge

volumes:
  oradata:
```

### 3. .env.prod (sur le VPS uniquement)

Fichier `/home/deploy/luxpretty/.env` (chmod 600, owner `deploy:deploy`). Contient les vraies valeurs prod, jamais commité. Une copie sur le poste local pour bootstrap initial (rsync), ensuite édité directement sur le VPS.

### 4. GitHub Actions workflow

`.github/workflows/deploy.yml` — déclenché sur `push` vers `main` :

1. **Build & push backend image** → `ghcr.io/<owner>/luxpretty-backend:latest` + tag SHA
2. **Build & push frontend image** → `ghcr.io/<owner>/luxpretty-frontend:latest` + tag SHA
3. **SSH au VPS** (action `appleboy/ssh-action`) → `cd /home/deploy/luxpretty && docker compose -f docker-compose.prod.yml pull && docker compose -f docker-compose.prod.yml up -d`
4. **Healthcheck post-deploy** : `curl -fsS https://luxpretty.lu/api/health` (à exposer côté backend si pas déjà fait)

Secrets GitHub requis :
- `VPS_HOST`, `VPS_USER`, `VPS_SSH_KEY` (clé privée OpenSSH)
- `GHCR_TOKEN` (PAT classic avec scope `write:packages`, ou GITHUB_TOKEN avec permissions packages)

## Phases (exécution incrémentale)

### Phase 1 — Durcissement VPS

**Objectif :** VPS sécurisé avant d'y installer quoi que ce soit.

1. Premier SSH en root via password (`.env`)
2. `apt update && apt upgrade -y`
3. Créer user `deploy` (sudo, NOPASSWD ou avec password)
4. Copier clé SSH publique locale vers `~deploy/.ssh/authorized_keys` (chmod 700/600)
5. Test SSH `deploy@VPS` depuis local, OK
6. `/etc/ssh/sshd_config` : `PermitRootLogin no`, `PasswordAuthentication no`, `PubkeyAuthentication yes`
7. `systemctl restart ssh`
8. UFW : `ufw default deny incoming`, `allow OpenSSH`, `allow 80/tcp`, `allow 443/tcp`, `ufw enable`
9. `apt install -y fail2ban unattended-upgrades`
10. `dpkg-reconfigure -plow unattended-upgrades`

**Vérification :** SSH root refusé, SSH password refusé, `deploy` connecte par clé, `ufw status` montre 22/80/443 ouverts uniquement.

### Phase 2 — Docker

1. Install Docker Engine (repo officiel `https://download.docker.com/linux/ubuntu`) + plugin compose
2. `usermod -aG docker deploy`
3. Re-login `deploy`, test `docker run hello-world`

**Vérification :** `docker --version`, `docker compose version`, `docker run hello-world` OK sans sudo.

### Phase 3 — Stack app (premier déploiement manuel)

**Objectif :** valider que la stack tourne, AVANT de brancher Caddy/DNS.

1. Créer `/home/deploy/luxpretty/`
2. `git clone` repo dans `/home/deploy/luxpretty/repo` (ou rsync seulement `docker-compose.prod.yml`)
3. Créer `.env` sur le VPS (chmod 600) avec valeurs prod
4. Authentification GHCR : `docker login ghcr.io -u <user> -p <PAT>`
5. **Première fois :** build images localement sur le VPS depuis `repo/backend` et `repo/frontend` (bypass GHCR pour bootstrap) :
   ```
   docker compose -f docker-compose.prod.yml build
   docker compose -f docker-compose.prod.yml up -d oracle
   # attendre healthy (~10 min, init Oracle)
   docker compose -f docker-compose.prod.yml up -d backend frontend
   ```
6. Tester localement sur VPS : `curl http://localhost:8080/api/health`, `curl http://localhost:8081/`

**Vérification :** 3 conteneurs UP, backend répond, frontend sert HTML, Oracle healthy.

### Phase 4 — Caddy + DNS + TLS

1. DNS chez registrar : record A `luxpretty.lu` → IP VPS, CNAME `www.luxpretty.lu` → `luxpretty.lu`
2. Attendre propagation (vérif `dig luxpretty.lu`)
3. Install Caddy via apt repo officiel
4. Écrire `/etc/caddy/Caddyfile` (cf. section Components)
5. `systemctl reload caddy` (déclenche TLS Let's Encrypt)
6. `systemctl status caddy`, logs `journalctl -u caddy -f`

**Vérification :** `curl -I https://luxpretty.lu` retourne 200, certificat LE valide, redirect 80→443, frontend chargé via domaine.

### Phase 5 — GitHub Actions CI/CD

1. Générer clé SSH dédiée deploy sur local : `ssh-keygen -t ed25519 -f ~/.ssh/luxpretty_deploy -N ""`
2. Ajouter `~/.ssh/luxpretty_deploy.pub` à `~deploy/.ssh/authorized_keys` sur VPS
3. Tester `ssh -i ~/.ssh/luxpretty_deploy deploy@VPS_HOST` OK
4. Ajouter secrets dans GitHub repo :
   - `VPS_HOST`, `VPS_USER` (= `deploy`)
   - `VPS_SSH_KEY` = contenu de `~/.ssh/luxpretty_deploy` (privée)
5. Écrire `.github/workflows/deploy.yml`
6. Push sur `main` → vérifier le run GH Actions, les images publiées sur GHCR, le deploy SSH

**Vérification :** push commit factice (touche README) sur `main` → workflow vert → images mises à jour sur VPS → `curl https://luxpretty.lu` montre la nouvelle version.

## Data Flow

### Requête client
1. Client → DNS → IP VPS
2. VPS:443 (Caddy) → termine TLS
3. Caddy regarde le path :
   - `/api/*` → `localhost:8080` (backend Spring)
   - autre → `localhost:8081` (Nginx frontend)
4. Backend ↔ Oracle via network Docker interne
5. Backend ↔ R2 via HTTPS sortant (Cloudflare)

### Deploy (push main)
1. Dev push `main`
2. GH Actions : build backend Docker image (multi-stage Maven), tag `:latest` + `:<sha>`, push GHCR
3. GH Actions : idem frontend (multi-stage Node → Nginx)
4. GH Actions SSH → VPS : `docker compose pull`, `docker compose up -d`
5. Compose détecte nouvelle image, recreate containers concernés (oracle pas touché car image inchangée)
6. Healthcheck final HTTPS

## Error Handling & Rollback

- **Échec build CI** → workflow rouge, rien déployé, prod intacte
- **Échec SSH deploy** → workflow rouge, prod intacte
- **Échec démarrage container** (ex: nouvelle image plante) :
  - `docker compose logs <service>` côté VPS
  - Rollback manuel : `docker pull ghcr.io/.../backend:<prev_sha>`, taguer en `:latest`, `docker compose up -d`
  - Pas de rollback auto dans cette phase (scope futur)
- **TLS Let's Encrypt rate limit** : Caddy gère retry, mais si erreur persistante → `journalctl -u caddy`, vérifier DNS + port 80 ouvert
- **Oracle indisponible** : backend ne démarre pas (depends_on healthy), Caddy renvoie 502 pour `/api/*`

## Testing / Verification

Pas de tests automatisés ajoutés au repo dans ce chantier. Vérifications manuelles à chaque phase :

- **Phase 1** : `ssh root@VPS` refusé, `ssh deploy@VPS` OK avec clé
- **Phase 2** : `docker run hello-world` sans sudo
- **Phase 3** : `docker compose ps` → tous healthy/running
- **Phase 4** : `curl -I https://luxpretty.lu` 200, SSL Labs A+
- **Phase 5** : commit factice → deploy auto OK end-to-end

## Risks & Open Questions

### Risques

1. **Oracle Free sur VPS petit (RAM)** — Oracle Free réclame ~2GB RAM minimum, plus le backend Spring (~512MB) + frontend Nginx (~50MB). VPS < 4GB risque de swap. **Mitigation :** vérifier specs VPS avant Phase 3, swap file si limite.
2. **Pas de backup Oracle** — perte de données possible si VPS crash. **À traiter en chantier suivant** (cron `expdp` vers R2 ou snapshot VPS provider).
3. **GHCR public/private** — par défaut les packages GHCR créés sont privés, donc le `docker pull` côté VPS doit être authentifié. Sinon basculer les packages en public via UI GHCR (acceptable si pas de secrets dans les images).
4. **CORS_ALLOWED_ORIGINS** — doit contenir `https://luxpretty.lu` et `https://www.luxpretty.lu` en prod, vérifier la valeur actuelle dans `.env`.
5. **Domaine `.lu` propagation DNS** — peut prendre jusqu'à 24h selon registrar, prévoir marge.

### Questions ouvertes

- **Identifier le owner GHCR** : `ghcr.io/gustavo/...` ou `ghcr.io/<github-org>/...` ? À confirmer au moment du workflow.
- **Specs VPS** (RAM, CPU, disk) ? À vérifier au premier SSH avec `free -h`, `nproc`, `df -h`.
- **OAUTH2_REDIRECT_URI** côté Google Console : doit être mis à jour vers `https://luxpretty.lu/...` (action manuelle hors scope code).

## Files Touched

- `docker-compose.prod.yml` (nouveau)
- `.github/workflows/deploy.yml` (nouveau)
- `.env.example` (ajouter clés VPS si manquantes — déjà présentes selon scan)
- `docs/superpowers/specs/2026-05-16-vps-prod-setup-design.md` (ce doc)

**Aucune modif du code applicatif (frontend/, backend/) prévue dans ce chantier.**

## Next Steps

1. User review de ce spec
2. Invoquer `writing-plans` skill → générer plan d'implémentation découpé par phase
3. Exécution incrémentale phase par phase, vérification après chaque
