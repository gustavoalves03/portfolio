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

## Statut Phase 1

✅ Phase 1 terminée (2026-05-16) — durcissement OK.

Prochaine étape : Phase 2 (Docker), Phase 3 (stack app), Phase 4 (Caddy + DNS + TLS), Phase 5 (GH Actions CI/CD).
Voir : `docs/superpowers/plans/2026-05-16-vps-prod-setup-plan.md`
