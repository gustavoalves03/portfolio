# VPS — Accès & infos de connexion

> Mémo perso, **NE PAS COMMIT ÉLÉMENTS SENSIBLES**. Tout fichier ici référence `.env` plutôt que d'exposer des secrets.

## Identifiants

| Quoi | Valeur |
|---|---|
| **IP VPS** | `51.255.194.40` |
| **Hostname** | `vps-e70bbc64` |
| **OS** | Ubuntu 24 (kernel 6.14) |
| **Ressources** | 11 GB RAM, 96 GB disque, 6 cores |
| **User d'admin/déploiement** | `deploy` |
| **User cloud par défaut** | `ubuntu` (toujours actif, accès SSH par clé OK) |
| **User `root`** | login direct **désactivé** (utiliser `sudo` via `deploy` ou `ubuntu`) |

## Comment se connecter

### Depuis ma machine locale (Mac)

**Prérequis (à faire 1x par redémarrage Mac) :** charger la clé dans l'agent SSH

```bash
ssh-add ~/.ssh/id_rsa
# tape le passphrase de la clé une fois
```

**Connexion (user `deploy`, recommandé) :**

```bash
ssh deploy@51.255.194.40
```

**Ou en tant que `ubuntu` (compte cloud original) :**

```bash
ssh ubuntu@51.255.194.40
```

Les deux passent par la **même clé SSH** : `~/.ssh/id_rsa`.

### Sudo

`deploy` a `sudo NOPASSWD` : aucune saisie de mot de passe nécessaire.

```bash
sudo apt update           # marche direct
sudo systemctl status ssh # idem
```

## Ce qui est verrouillé

- ❌ Login `root` direct (`PermitRootLogin no`)
- ❌ Auth par password (`PasswordAuthentication no`) — clé SSH **obligatoire**
- ✅ UFW actif : ports ouverts **22 (SSH)**, **80 (HTTP)**, **443 (HTTPS)** uniquement
- ✅ fail2ban actif : bannit toute IP qui rate 5 logins SSH en 10 min (bantime 1h)
- ✅ unattended-upgrades : MAJ sécurité Ubuntu auto

## Si je perds l'accès SSH

Le password VPS d'origine est dans `.env` (`VPS_PASSWORD`) mais **il ne sert plus à grand chose** car l'auth password est désactivée.

→ Utiliser la **console KVM/VNC du fournisseur** (interface web) pour récupérer un accès, puis :

```bash
# Console fournisseur, login ubuntu (password VPS d'origine si console permet)
sudo nano /etc/ssh/sshd_config.d/99-luxpretty-hardening.conf
# Remettre temporairement PasswordAuthentication yes si besoin
sudo systemctl reload ssh
```

Ou ré-ajouter une clé SSH manuellement dans `/home/deploy/.ssh/authorized_keys` ou `/home/ubuntu/.ssh/authorized_keys`.

## Inventaire fichiers importants sur le VPS

| Chemin | Quoi |
|---|---|
| `/etc/ssh/sshd_config.d/99-luxpretty-hardening.conf` | Durcissement SSH (root/password no) |
| `/etc/ssh/sshd_config.d/50-cloud-init.conf.disabled` | Drop-in cloud-init désactivé (mettait `PasswordAuthentication yes`) |
| `/etc/sudoers.d/deploy` | sudo NOPASSWD pour `deploy` |
| `/home/deploy/.ssh/authorized_keys` | Ma clé publique |
| `/etc/fail2ban/jail.local` | Conf fail2ban custom |
| `/etc/apt/apt.conf.d/20auto-upgrades` | Auto-updates activés |

## URL prod

- 🌐 **https://luxpretty.lu** — site live
- 🌐 **https://www.luxpretty.lu** — alias
- API : `https://luxpretty.lu/api/*`
- OAuth callback : `https://luxpretty.lu/login/oauth2/code/google`
- Cert Let's Encrypt valide jusqu'au 14 août 2026 (renouvellement auto par Caddy)

## Statut phases

- ✅ **Phase 1** (2026-05-16) — durcissement VPS (deploy user, sshd, UFW, fail2ban)
- ✅ **Phase 2** (2026-05-16) — Docker Engine 29.2 + Compose v5 (deploy dans groupe docker)
- ✅ **Phase 3** (2026-05-16) — stack app sur VPS (Oracle Free + backend Spring + frontend Nginx)
- ✅ **Phase 4** (2026-05-16) — Caddy 2.11 + DNS + TLS Let's Encrypt
- ⏳ **Phase 5** — CI/CD GitHub Actions (à faire)

## Commandes utiles VPS

```bash
# SSH (depuis local Mac, après ssh-add une fois)
ssh deploy@51.255.194.40

# Voir l'état de la stack
docker compose -f /home/deploy/luxpretty/docker-compose.prod.yml ps

# Logs backend
docker logs luxpretty-backend --tail 100 -f

# Logs Caddy
sudo journalctl -u caddy -f

# Restart un service
docker compose -f /home/deploy/luxpretty/docker-compose.prod.yml restart backend

# Reload Caddy (après modif Caddyfile)
sudo systemctl reload caddy

# Vérifier UFW
sudo ufw status

# Vérifier fail2ban
sudo fail2ban-client status sshd
```

## Dettes techniques (post-Phase 3)

1. **Flyway désactivé** sur le VPS (`SPRING_FLYWAY_ENABLED=false`) — V9/V10/V12 plantent sur fresh DB car pas de garde idempotente. À fixer : ajouter même pattern que V2 (skip si table inexistante).
2. **MailWorker ORA-02014** (`SELECT FOR UPDATE` sur vue avec DISTINCT) — erreur runtime sur background job, app continue de tourner.
3. **`application-prodtest.properties` contient password Hostinger en clair** dans le git — à externaliser en env var + purger l'historique.

Voir : `docs/superpowers/plans/2026-05-16-vps-prod-setup-plan.md`
Voir : `docs/superpowers/specs/2026-05-16-vps-prod-setup-design.md`
