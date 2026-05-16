# VPS Prod Setup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Déployer LuxPretty en prod sur un VPS Ubuntu LTS avec Caddy + Docker Compose (Oracle + backend Spring + frontend Nginx) + CI/CD via GitHub Actions.

**Architecture:** Caddy host (TLS Let's Encrypt auto) en frontal HTTPS → reverse-proxy vers backend Spring (`:8080`) et frontend Nginx (`:8081`) tournant dans Docker Compose ; Oracle Free conteneurisé sur network interne ; deploy auto par push `main` via GHCR + SSH.

**Tech Stack:** Ubuntu 22.04/24.04, Caddy 2, Docker Engine + Compose v2, Spring Boot 3.5/Java 21, Angular 20 (build statique Nginx), Oracle Free, GitHub Actions, GHCR, Cloudflare R2.

**Note méthodologique :** ce plan est majoritairement de l'infra (shell, conf serveur). Pas de TDD au sens unitaire. Chaque tâche = action concrète + commande de vérification + output attendu. Les commits concernent les fichiers du repo (compose prod, workflow GH Actions). La conf VPS (sshd, ufw, caddy) ne se commit pas — elle se documente dans ce plan.

**Référence spec :** `docs/superpowers/specs/2026-05-16-vps-prod-setup-design.md`

---

## File Structure

**Fichiers créés dans le repo :**
- `docker-compose.prod.yml` — compose prod (oracle + backend + frontend, network interne, ports bind localhost only)
- `.github/workflows/deploy.yml` — pipeline CI/CD : build images → push GHCR → SSH deploy

**Fichiers créés sur le VPS (hors repo) :**
- `/home/deploy/luxpretty/docker-compose.prod.yml` — copie du fichier ci-dessus
- `/home/deploy/luxpretty/.env` — secrets prod (chmod 600, jamais commité)
- `/etc/caddy/Caddyfile` — conf Caddy
- `~deploy/.ssh/authorized_keys` — clés SSH autorisées

**Fichiers non touchés :** code applicatif `frontend/`, `backend/`, `docker-compose.yml` dev existant.

---

## Phase 1 — Durcissement VPS

### Task 1.1 : Premier accès root + maj système

**Files:** aucun (actions VPS)

- [ ] **Step 1: Lire identifiants VPS depuis .env local**

Run (local) :
```bash
grep -E '^VPS_' /Users/Gustavo.alves/Documents/personal/portfolio/.env
```
Expected : voir `VPS_HOST`, `VPS_USER`, `VPS_PASSWORD`, `VPS_SSH_KEY_PATH`.

- [ ] **Step 2: Premier SSH root**

Run (local) :
```bash
ssh root@$VPS_HOST
# (password from .env)
```
Expected : prompt root@... connecté.

- [ ] **Step 3: Identifier l'OS et ressources**

Run (VPS, root) :
```bash
lsb_release -a && uname -r && free -h && df -h / && nproc
```
Expected : Ubuntu 22.04 ou 24.04, RAM ≥ 4GB (sinon flag risque Oracle), disk libre ≥ 20GB.

- [ ] **Step 4: Mise à jour système**

Run (VPS, root) :
```bash
apt update && apt upgrade -y && apt autoremove -y
```
Expected : pas d'erreur, reboot pas requis (sinon `reboot` puis attendre).

### Task 1.2 : Créer user deploy + SSH key-only

**Files:** `~deploy/.ssh/authorized_keys` (VPS), `/etc/ssh/sshd_config` (VPS)

- [ ] **Step 1: Créer user deploy avec sudo**

Run (VPS, root) :
```bash
adduser --disabled-password --gecos "" deploy
usermod -aG sudo deploy
echo "deploy ALL=(ALL) NOPASSWD:ALL" > /etc/sudoers.d/deploy
chmod 440 /etc/sudoers.d/deploy
```
Expected : user créé, sudoers fichier en place.

- [ ] **Step 2: Préparer dossier SSH**

Run (VPS, root) :
```bash
mkdir -p /home/deploy/.ssh
chmod 700 /home/deploy/.ssh
touch /home/deploy/.ssh/authorized_keys
chmod 600 /home/deploy/.ssh/authorized_keys
chown -R deploy:deploy /home/deploy/.ssh
```
Expected : structure prête.

- [ ] **Step 3: Récupérer la clé publique locale**

Run (local, autre terminal) :
```bash
cat ~/.ssh/id_ed25519.pub 2>/dev/null || cat ~/.ssh/id_rsa.pub
```
Expected : clé publique affichée (format `ssh-ed25519 AAA...` ou `ssh-rsa AAA...`). Si aucune, en générer une :
```bash
ssh-keygen -t ed25519 -C "deploy@luxpretty"
```

- [ ] **Step 4: Installer la clé sur le VPS**

Run (VPS, root) :
```bash
echo "<COLLER_LA_CLE_PUBLIQUE>" >> /home/deploy/.ssh/authorized_keys
```
Expected : clé ajoutée.

- [ ] **Step 5: Tester SSH deploy**

Run (local) :
```bash
ssh deploy@$VPS_HOST
```
Expected : connecté en tant que `deploy` sans password.

- [ ] **Step 6: Désactiver login root + password**

Run (VPS, en tant que deploy) :
```bash
sudo sed -i 's/^#*PermitRootLogin.*/PermitRootLogin no/' /etc/ssh/sshd_config
sudo sed -i 's/^#*PasswordAuthentication.*/PasswordAuthentication no/' /etc/ssh/sshd_config
sudo sed -i 's/^#*PubkeyAuthentication.*/PubkeyAuthentication yes/' /etc/ssh/sshd_config
sudo grep -E '^(PermitRootLogin|PasswordAuthentication|PubkeyAuthentication)' /etc/ssh/sshd_config
sudo systemctl restart ssh
```
Expected : les 3 lignes affichent `no`, `no`, `yes`.

- [ ] **Step 7: Vérifier que root/password sont refusés**

Run (local, depuis un autre terminal — ne pas fermer la session deploy ouverte !) :
```bash
ssh root@$VPS_HOST  # doit échouer
ssh -o PreferredAuthentications=password -o PubkeyAuthentication=no deploy@$VPS_HOST  # doit échouer
```
Expected : "Permission denied" sur les deux.

### Task 1.3 : Firewall UFW + fail2ban + unattended-upgrades

- [ ] **Step 1: Installer + configurer UFW**

Run (VPS, deploy) :
```bash
sudo ufw default deny incoming
sudo ufw default allow outgoing
sudo ufw allow OpenSSH
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw --force enable
sudo ufw status
```
Expected : `Status: active`, règles 22, 80, 443 listées.

- [ ] **Step 2: Installer fail2ban**

Run (VPS, deploy) :
```bash
sudo apt install -y fail2ban
sudo systemctl enable --now fail2ban
sudo systemctl status fail2ban --no-pager
```
Expected : `active (running)`.

- [ ] **Step 3: Installer unattended-upgrades**

Run (VPS, deploy) :
```bash
sudo apt install -y unattended-upgrades
echo 'APT::Periodic::Update-Package-Lists "1";
APT::Periodic::Unattended-Upgrade "1";' | sudo tee /etc/apt/apt.conf.d/20auto-upgrades
sudo systemctl status unattended-upgrades --no-pager
```
Expected : actif.

---

## Phase 2 — Docker Engine + Compose

### Task 2.1 : Installation Docker via repo officiel

- [ ] **Step 1: Préparer le repo Docker**

Run (VPS, deploy) :
```bash
sudo apt install -y ca-certificates curl gnupg
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | sudo tee /etc/apt/sources.list.d/docker.list
sudo apt update
```
Expected : pas d'erreur, repo Docker ajouté.

- [ ] **Step 2: Installer Docker Engine + Compose plugin**

Run (VPS, deploy) :
```bash
sudo apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
docker --version
docker compose version
```
Expected : versions affichées (Docker ≥ 24, Compose ≥ 2.20).

- [ ] **Step 3: Permettre à deploy d'utiliser Docker sans sudo**

Run (VPS, deploy) :
```bash
sudo usermod -aG docker deploy
exit
```
Puis reconnecter :
```bash
ssh deploy@$VPS_HOST
docker run --rm hello-world
```
Expected : "Hello from Docker!" affiché.

---

## Phase 3 — Stack app (premier déploiement manuel)

### Task 3.1 : Écrire docker-compose.prod.yml

**Files:**
- Create (local) : `/Users/Gustavo.alves/Documents/personal/portfolio/docker-compose.prod.yml`

- [ ] **Step 1: Créer le fichier compose prod**

Contenu :
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
    image: ${BACKEND_IMAGE:-ghcr.io/REPLACE_OWNER/luxpretty-backend:latest}
    container_name: luxpretty-backend
    ports:
      - "127.0.0.1:8080:8080"
    environment:
      SPRING_PROFILES_ACTIVE: prod
      SPRING_DATASOURCE_URL: jdbc:oracle:thin:@oracle:1521/FREEPDB1
      SPRING_DATASOURCE_USERNAME: ${APP_USER}
      SPRING_DATASOURCE_PASSWORD: ${APP_USER_PASSWORD}
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
    image: ${FRONTEND_IMAGE:-ghcr.io/REPLACE_OWNER/luxpretty-frontend:latest}
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

> **Note :** `REPLACE_OWNER` sera remplacé par l'owner GitHub réel à la Task 5.1 (ex : `gustavoalves` ou nom d'org). Pour le bootstrap manuel (Task 3.3), on builde localement et on tag manuellement, donc cette valeur n'est pas critique au début.

- [ ] **Step 2: Commit le fichier compose prod**

Run (local) :
```bash
cd /Users/Gustavo.alves/Documents/personal/portfolio
git add docker-compose.prod.yml
git commit -m "feat(infra): add docker-compose.prod.yml for VPS deployment"
```
Expected : commit créé.

### Task 3.2 : Préparer le VPS pour la stack

- [ ] **Step 1: Créer le dossier app sur le VPS**

Run (VPS, deploy) :
```bash
mkdir -p /home/deploy/luxpretty
cd /home/deploy/luxpretty
```

- [ ] **Step 2: Copier compose prod depuis local**

Run (local) :
```bash
scp /Users/Gustavo.alves/Documents/personal/portfolio/docker-compose.prod.yml deploy@$VPS_HOST:/home/deploy/luxpretty/
```
Expected : transfert OK.

- [ ] **Step 3: Créer le fichier .env sur le VPS**

Run (local) — copier .env vers VPS, puis ajuster pour prod :
```bash
scp /Users/Gustavo.alves/Documents/personal/portfolio/.env deploy@$VPS_HOST:/home/deploy/luxpretty/.env
```

Puis (VPS, deploy) :
```bash
chmod 600 /home/deploy/luxpretty/.env
# Éditer pour adapter les valeurs prod :
nano /home/deploy/luxpretty/.env
```

Adapter notamment :
- `OAUTH2_REDIRECT_URI=https://luxpretty.lu/api/auth/oauth2/callback`
- `CORS_ALLOWED_ORIGINS=https://luxpretty.lu,https://www.luxpretty.lu`
- `FRONTEND_BASE_URL=https://luxpretty.lu`
- Retirer `VPS_*` (inutiles côté VPS lui-même)

Expected : fichier en place, chmod 600.

### Task 3.3 : Premier build + démarrage manuel

> **Bootstrap :** on builde les images directement sur le VPS pour ce premier essai (avant GHCR). Phase 5 basculera sur GHCR.

- [ ] **Step 1: Cloner le repo sur le VPS**

Run (VPS, deploy) :
```bash
sudo apt install -y git
mkdir -p /home/deploy/repo
git clone https://github.com/<OWNER>/<REPO>.git /home/deploy/repo/luxpretty
cd /home/deploy/repo/luxpretty
```
Expected : repo cloné. Si repo privé : utiliser un PAT ou clé deploy GitHub.

- [ ] **Step 2: Builder les images localement sur le VPS**

Run (VPS, deploy, dans `/home/deploy/repo/luxpretty`) :
```bash
docker build -t luxpretty-backend:bootstrap ./backend
docker build -t luxpretty-frontend:bootstrap ./frontend
docker images | grep luxpretty
```
Expected : 2 images listées.

- [ ] **Step 3: Override les images dans compose pour bootstrap**

Run (VPS, deploy) :
```bash
cd /home/deploy/luxpretty
cat >> .env <<'EOF'
BACKEND_IMAGE=luxpretty-backend:bootstrap
FRONTEND_IMAGE=luxpretty-frontend:bootstrap
EOF
```

- [ ] **Step 4: Démarrer Oracle d'abord (attendre healthy)**

Run (VPS, deploy) :
```bash
cd /home/deploy/luxpretty
docker compose -f docker-compose.prod.yml --env-file .env up -d oracle
docker compose -f docker-compose.prod.yml ps
```
Expected : oracle en `starting` puis `healthy` (environ 5-10 min). Suivre :
```bash
docker logs -f oracle-db
```
Attendre le message `DATABASE IS READY TO USE!`.

- [ ] **Step 5: Démarrer backend + frontend**

Run (VPS, deploy) :
```bash
docker compose -f docker-compose.prod.yml --env-file .env up -d backend frontend
docker compose -f docker-compose.prod.yml ps
```
Expected : 3 conteneurs `running`/`healthy`.

- [ ] **Step 6: Vérifier les endpoints localement sur le VPS**

Run (VPS, deploy) :
```bash
curl -sI http://127.0.0.1:8080/actuator/health || curl -sI http://127.0.0.1:8080/
curl -sI http://127.0.0.1:8081/
```
Expected : 200 ou 404 (mais réponse HTTP), pas de connexion refusée.

Si KO :
```bash
docker compose -f docker-compose.prod.yml logs backend --tail 50
docker compose -f docker-compose.prod.yml logs frontend --tail 50
```

---

## Phase 4 — Caddy + DNS + TLS

### Task 4.1 : Configurer DNS chez le registrar

- [ ] **Step 1: Récupérer l'IP publique du VPS**

Run (VPS, deploy) :
```bash
curl -s https://api.ipify.org
echo
```
Expected : IP publique affichée.

- [ ] **Step 2: Créer les records DNS chez le registrar `.lu`**

Action manuelle dans l'interface du registrar :
- Record A : `luxpretty.lu` → `<IP_VPS>` (TTL 300s pour test, remettre 3600+ après)
- Record CNAME : `www.luxpretty.lu` → `luxpretty.lu` (ou record A identique)

- [ ] **Step 3: Vérifier propagation DNS**

Run (local) :
```bash
dig +short luxpretty.lu
dig +short www.luxpretty.lu
```
Expected : retourne l'IP du VPS. Si pas encore propagé, attendre (jusqu'à plusieurs heures pour `.lu`). Continuer Task 4.2 en attendant — Caddy retentera tant que DNS pas prêt.

### Task 4.2 : Installer Caddy

- [ ] **Step 1: Ajouter le repo Caddy officiel**

Run (VPS, deploy) :
```bash
sudo apt install -y debian-keyring debian-archive-keyring apt-transport-https
curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/gpg.key' | sudo gpg --dearmor -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt' | sudo tee /etc/apt/sources.list.d/caddy-stable.list
sudo apt update
sudo apt install -y caddy
caddy version
```
Expected : version Caddy 2.x affichée.

- [ ] **Step 2: Écrire le Caddyfile**

Run (VPS, deploy) :
```bash
sudo tee /etc/caddy/Caddyfile > /dev/null <<'EOF'
luxpretty.lu, www.luxpretty.lu {
    encode gzip zstd

    handle /api/* {
        reverse_proxy 127.0.0.1:8080
    }

    handle {
        reverse_proxy 127.0.0.1:8081
    }

    log {
        output file /var/log/caddy/luxpretty.log
        format json
    }
}
EOF
sudo mkdir -p /var/log/caddy
sudo chown caddy:caddy /var/log/caddy
```

- [ ] **Step 3: Valider la syntaxe**

Run (VPS, deploy) :
```bash
sudo caddy validate --config /etc/caddy/Caddyfile
```
Expected : `Valid configuration`.

- [ ] **Step 4: Recharger Caddy**

Run (VPS, deploy) :
```bash
sudo systemctl reload caddy
sudo systemctl status caddy --no-pager
```
Expected : `active (running)`, pas d'erreur dans les logs récents.

- [ ] **Step 5: Vérifier émission certificat Let's Encrypt**

Run (VPS, deploy) :
```bash
sudo journalctl -u caddy --since "5 minutes ago" | grep -iE "certificate|tls"
```
Expected : ligne du type `certificate obtained successfully`. Si erreur (DNS pas propagé, port 80 bloqué), attendre/diagnostiquer.

- [ ] **Step 6: Tester HTTPS depuis local**

Run (local) :
```bash
curl -sI https://luxpretty.lu
curl -sI https://www.luxpretty.lu
curl -sI http://luxpretty.lu
```
Expected :
- HTTPS : 200 (frontend home) ou réponse de l'app
- HTTP : 308/301 (redirect automatique de Caddy vers HTTPS)

---

## Phase 5 — GitHub Actions CI/CD

### Task 5.1 : Clé SSH dédiée pour deploy

- [ ] **Step 1: Générer une paire de clés dédiée**

Run (local) :
```bash
ssh-keygen -t ed25519 -f ~/.ssh/luxpretty_deploy -N "" -C "github-actions-luxpretty"
ls -la ~/.ssh/luxpretty_deploy*
```
Expected : 2 fichiers générés (`luxpretty_deploy` privée, `luxpretty_deploy.pub` publique).

- [ ] **Step 2: Installer la clé publique sur le VPS**

Run (local) :
```bash
cat ~/.ssh/luxpretty_deploy.pub | ssh deploy@$VPS_HOST "cat >> ~/.ssh/authorized_keys"
```
Expected : clé ajoutée.

- [ ] **Step 3: Tester depuis local**

Run (local) :
```bash
ssh -i ~/.ssh/luxpretty_deploy deploy@$VPS_HOST "echo OK"
```
Expected : "OK" affiché.

### Task 5.2 : Configurer secrets GitHub

> **Action manuelle UI GitHub :** repo Settings → Secrets and variables → Actions → New repository secret

- [ ] **Step 1: Ajouter les secrets**

Créer les secrets suivants :
- `VPS_HOST` : valeur de `VPS_HOST` du `.env`
- `VPS_USER` : `deploy`
- `VPS_SSH_KEY` : contenu **complet** de `~/.ssh/luxpretty_deploy` (la clé privée, headers inclus `-----BEGIN... -----END...`)

- [ ] **Step 2: Vérifier sur GHCR que le repo peut publier**

Action manuelle UI GitHub : Settings → Actions → General → Workflow permissions → cocher "Read and write permissions".

### Task 5.3 : Écrire le workflow GH Actions

**Files:**
- Create (local) : `/Users/Gustavo.alves/Documents/personal/portfolio/.github/workflows/deploy.yml`

- [ ] **Step 1: Créer le workflow**

Contenu :
```yaml
name: Deploy to VPS

on:
  push:
    branches: [main]
  workflow_dispatch:

env:
  REGISTRY: ghcr.io
  BACKEND_IMAGE: ${{ github.repository_owner }}/luxpretty-backend
  FRONTEND_IMAGE: ${{ github.repository_owner }}/luxpretty-frontend

jobs:
  build-backend:
    runs-on: ubuntu-latest
    permissions:
      contents: read
      packages: write
    steps:
      - uses: actions/checkout@v4
      - uses: docker/setup-buildx-action@v3
      - uses: docker/login-action@v3
        with:
          registry: ${{ env.REGISTRY }}
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}
      - uses: docker/build-push-action@v5
        with:
          context: ./backend
          push: true
          tags: |
            ${{ env.REGISTRY }}/${{ env.BACKEND_IMAGE }}:latest
            ${{ env.REGISTRY }}/${{ env.BACKEND_IMAGE }}:${{ github.sha }}
          cache-from: type=gha,scope=backend
          cache-to: type=gha,mode=max,scope=backend

  build-frontend:
    runs-on: ubuntu-latest
    permissions:
      contents: read
      packages: write
    steps:
      - uses: actions/checkout@v4
      - uses: docker/setup-buildx-action@v3
      - uses: docker/login-action@v3
        with:
          registry: ${{ env.REGISTRY }}
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}
      - uses: docker/build-push-action@v5
        with:
          context: ./frontend
          push: true
          tags: |
            ${{ env.REGISTRY }}/${{ env.FRONTEND_IMAGE }}:latest
            ${{ env.REGISTRY }}/${{ env.FRONTEND_IMAGE }}:${{ github.sha }}
          cache-from: type=gha,scope=frontend
          cache-to: type=gha,mode=max,scope=frontend

  deploy:
    needs: [build-backend, build-frontend]
    runs-on: ubuntu-latest
    steps:
      - name: Deploy via SSH
        uses: appleboy/ssh-action@v1.0.3
        with:
          host: ${{ secrets.VPS_HOST }}
          username: ${{ secrets.VPS_USER }}
          key: ${{ secrets.VPS_SSH_KEY }}
          script: |
            set -e
            cd /home/deploy/luxpretty
            echo "${{ secrets.GITHUB_TOKEN }}" | docker login ghcr.io -u ${{ github.actor }} --password-stdin
            export BACKEND_IMAGE=ghcr.io/${{ github.repository_owner }}/luxpretty-backend:latest
            export FRONTEND_IMAGE=ghcr.io/${{ github.repository_owner }}/luxpretty-frontend:latest
            docker compose -f docker-compose.prod.yml --env-file .env pull backend frontend
            docker compose -f docker-compose.prod.yml --env-file .env up -d
            docker image prune -f
      - name: Health check
        run: |
          sleep 30
          curl -fsS https://luxpretty.lu/ -o /dev/null || (echo "Frontend KO" && exit 1)
          echo "Frontend OK"
```

> **Note sur `BACKEND_IMAGE` / `FRONTEND_IMAGE` :** le compose lit ces variables depuis l'environnement (export inline dans le script SSH). Le `.env` du VPS contient toujours les valeurs de bootstrap mais ces `export` les override pour le temps de la commande.

- [ ] **Step 2: Commit le workflow**

Run (local) :
```bash
cd /Users/Gustavo.alves/Documents/personal/portfolio
git add .github/workflows/deploy.yml
git commit -m "ci: add GitHub Actions workflow for VPS deployment via GHCR + SSH"
```

### Task 5.4 : Tester le pipeline end-to-end

- [ ] **Step 1: Push sur main**

Run (local) :
```bash
git push origin main
```

- [ ] **Step 2: Suivre le workflow**

Action manuelle UI GitHub : onglet Actions → suivre le run "Deploy to VPS". Attendre les 3 jobs verts.

Expected : `build-backend` ✅, `build-frontend` ✅, `deploy` ✅.

Si échec :
- Build : lire logs du job, ajuster Dockerfile si besoin
- Deploy SSH : vérifier secrets, vérifier permissions GHCR (image publique vs privée — si privée, le `docker login` côté VPS doit utiliser le bon token)

- [ ] **Step 3: Vérifier que les images GHCR sont publiées**

Action manuelle UI GitHub : profil / repo → Packages → vérifier présence de `luxpretty-backend` et `luxpretty-frontend` avec tags `latest` et `<sha>`.

- [ ] **Step 4: Vérifier déploiement effectif sur VPS**

Run (VPS, deploy) :
```bash
docker compose -f /home/deploy/luxpretty/docker-compose.prod.yml ps
docker images | grep ghcr.io
```
Expected : images ghcr.io en place, conteneurs `running`.

- [ ] **Step 5: Test final HTTPS**

Run (local) :
```bash
curl -sI https://luxpretty.lu
curl -sI https://luxpretty.lu/api/
```
Expected : 200 (front), 200/401/404 (back, mais réponse Spring).

- [ ] **Step 6: Test deploy auto avec commit factice**

Run (local) :
```bash
cd /Users/Gustavo.alves/Documents/personal/portfolio
echo "" >> README.md
git add README.md
git commit -m "chore: trigger deploy test"
git push origin main
```

Vérifier que le workflow se redéclenche, build et deploy → curl sur le site → réponse mise à jour.

---

## Vérifications finales

- [ ] `ssh root@VPS` → refusé
- [ ] `ssh deploy@VPS` (password) → refusé
- [ ] `ssh -i ~/.ssh/luxpretty_deploy deploy@VPS` → OK
- [ ] `ufw status` → 22, 80, 443 only
- [ ] `https://luxpretty.lu` → 200, certificat Let's Encrypt valide
- [ ] `https://luxpretty.lu/api/...` → réponse backend
- [ ] Push `main` → deploy auto fonctionnel
- [ ] Images publiées sur GHCR

## Risques / suivi post-implem

- Backup Oracle : à planifier (cron `expdp` + upload R2)
- Monitoring : pas en place (suivi manuel via `docker logs` / journalctl)
- Rollback auto : non implémenté ; en cas de bad deploy, taguer manuellement une SHA précédente et redéployer
- RAM VPS : surveiller `free -h` après quelques jours d'usage Oracle
- OAuth Google : penser à ajouter `https://luxpretty.lu/...` dans la console Google Cloud
