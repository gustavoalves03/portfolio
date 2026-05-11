# Backlog hiérarchisé par urgence — LuxPretty

> **Document autoporteur** : tu peux charger ce fichier dans un nouveau contexte Claude Code et reprendre sans aucun historique. Tout ce qu'il faut savoir est ici.

**Date :** 2026-05-11
**Source :** trie le brain-dump du soir 2026-05-11 (`docs/superpowers/backlog/2026-05-11-product-backlog.md`) par **ordre de bataille**, pas par catégorie thématique.

**Règle de hiérarchisation :**
1. Bugs bloquants en premier (chaque jour qui passe = perte de conversion).
2. Décisions architecturales qui impactent plein de specs en aval (à trancher tôt).
3. Sécurité (chantier déjà spec-é, prêt à exécuter).
4. Chantiers métier par dépendance + impact business.

---

## Comment utiliser ce doc dans un nouveau contexte

Au début d'une nouvelle session Claude Code, dis simplement :
> "Charge `docs/superpowers/backlog/2026-05-11-priorities.md` et on attaque le N°1 de la liste."

Claude Code lira ce doc, comprendra où on en est, et démarrera le brainstorm/spec/plan du premier item non encore traité.

---

## 🥇 NIVEAU 1 — À FAIRE EN PREMIER (cette semaine)

### 1. Bug B1 — Réservation depuis un post quand le client est logué 🔴

**Symptôme :** logué en tant qu'utilisateur, le bouton "réserver" sur un post (de la page salon ou de la home) ne déclenche plus la modale de booking. Quand on est déconnecté ça marche.

**Pourquoi P0 :** un post est l'**entrée principale** d'acquisition booking (vitrine Instagram-like de chaque salon). Si la conversion `post → booking` est cassée pour les users logués, on perd la conversion des clients fidèles qui voient un nouveau post de leur salon préféré.

**Hypothèse de cause** (à confirmer en investigation) : le flow `salon-posts-viewer.component.ts` → `bookCare.emit(careId)` ne propage plus l'event vers la page parent quand l'auth est active. Peut-être un `if (!isAuthenticated)` mal placé qui court-circuite, ou la modale de booking qui ne s'ouvre pas si l'utilisateur a déjà un token.

**Effort estimé :** 1-2h de debug + fix + spec.

**Approche suggérée :** demande à Claude de lancer `superpowers:systematic-debugging` directement (pas besoin de brainstorming pour un bug isolé).

---

### 2. Question Q1 — Un user peut-il être à la fois pro ET client ?

**Pourquoi P0 :** **décision architecturale** qui impacte presque tous les autres items en aval (système commercial, admin dashboard, modèle data User). À trancher tôt.

**Constat actuel :** le compte `PRO` peut prendre des RDV (donc agir comme client) mais n'a pas de "suivi client" associé (pas d'historique propre en tant que client).

**3 options possibles :**

| Option | Description | Pour | Contre |
|---|---|---|---|
| **A — Rôles cumulables** | Un user a un set de roles (`USER + PRO`). Le côté client de son profil contient l'historique de ses bookings personnels (où il est client) ; le côté pro contient le salon qu'il opère. | Reflète la réalité (un esthéticien prend aussi RDV ailleurs) | Complexité UX : "tu es sur quelle interface aujourd'hui ?" |
| **B — Comptes séparés** | Un pro qui veut prendre des RDV crée un 2e compte client avec un autre email. | Simple, sans ambiguïté | Friction utilisateur, mauvaise UX |
| **C — PRO inclut implicitement USER** | Tout PRO est aussi USER pour les fonctions client (historique, notes), avec un toggle UI pour basculer. | Pas de double-compte, cohérent avec l'usage réel | Complexité backend (séparer données pro vs client) |

**Recommandation pré-mâchée :** Option C, à valider en brainstorm. Cohérent avec ce qu'on voit chez Treatwell / Planity (un même compte peut réserver et opérer).

**Effort estimé :** 30 min de discussion + 1-2h de refactor sur le modèle User côté backend si Option A ou C choisie.

**Approche suggérée :** brainstorm avec Claude (`superpowers:brainstorming`) — 3 questions max pour trancher.

---

### 3. PR1 Sécurité — Cloudflare proxy + Turnstile captcha 🔴

**Statut :** **spec + plan d'implémentation déjà rédigés.** Prêt à exécuter.

**Pourquoi P0 :** ton site est publiquement exposé. Sans rate-limit ni captcha, des bots peuvent créer des centaines de faux comptes pro/client, faire tomber le service par DDoS, ou brute-forcer le login.

**Liens :**
- Spec : `docs/superpowers/specs/2026-05-11-cloudflare-turnstile-design.md`
- Plan : `docs/superpowers/plans/2026-05-11-cloudflare-turnstile.md` (18 tasks task-par-task)
- Roadmap globale 6 chantiers : `docs/superpowers/specs/2026-05-11-security-roadmap.md`

**Effort estimé :** ~5h30 de code + 30 min config Cloudflare dashboard manuel = 1 journée.

**Approche suggérée :** `superpowers:subagent-driven-development` sur le plan. Démarrer par le pre-flight (Tasks 1-18).

**Actions manuelles post-merge** (à NE PAS oublier) :
1. Cloudflare dashboard → Turnstile → "Add site" → récupérer Site Key + Secret Key
2. Set env vars prod : `TURNSTILE_ENABLED=true`, `TURNSTILE_SECRET_KEY=<secret>`
3. Wire le proxy Cloudflare (11 étapes documentées dans `docs/OPS_CLOUDFLARE.md`)

---

## 🥈 NIVEAU 2 — APRÈS LE NIVEAU 1 (cette semaine ou la suivante)

### 4. Bug B4 — Tester les transactions concurrentes (double booking) 🟠

**Symptôme :** deux clients qui réservent le **même créneau au même millième de seconde** — quel comportement ? Aujourd'hui non testé. Risque réel en prod multi-clients : un seul slot, deux confirmations.

**Pourquoi P1 :** **risque silencieux** mais gravé. La première fois que ça arrive en prod, c'est un mail au pro qui dit "j'ai 2 RDV à 14h, pour qui je le tiens ?" — perte de confiance.

**À faire :**
- Tests d'intégration sur `CareBookingService` avec scénario concurrent (2 threads, même slot, vérifier qu'un seul commit, l'autre reçoit 409 Conflict)
- Vérifier qu'il y a un mécanisme de locking : soit `@Version` optimistic lock JPA sur l'entité Slot, soit **unique constraint DB** sur `(employee_id, appointment_date, appointment_time)` qui throw `DataIntegrityViolationException` à catcher en 409

**Effort estimé :** 2-3h (peut-être 1 jour si on découvre qu'il faut ajouter un index ou changer le modèle).

**Approche suggérée :** `superpowers:systematic-debugging` puis `superpowers:test-driven-development` pour les tests.

---

### 5. E1 — Création employé via invitation (au lieu de création directe)

**Aujourd'hui :** le pro tape lui-même l'email + mot de passe de l'employé → l'employé reçoit ses credentials.

**Cible :** flow type Slack/Notion :
1. Pro saisit juste l'email de l'employé dans son back-office
2. Backend génère un token d'invitation, envoie un email avec un lien `/invite/<token>`
3. L'employé clique → page "Vous avez été invité chez `<salon>`" → set son propre mot de passe (ou login Google)
4. Compte créé avec rôle `EMPLOYEE`, lié au tenant du salon
5. Pro voit l'employé apparaître dans sa liste une fois l'invitation acceptée

**Pourquoi P1 :**
- **Sécurité** : le pro ne tape jamais le password de l'employé (fuite possible si pro compromis)
- **UX** : l'employé choisit son mot de passe (RGPD friendlier)
- **Compat OAuth** : l'employé peut se connecter via Google directement

**Dépendances :** modèle `EmployeeInvitation { token, salonId, email, expiresAt, acceptedAt }`, nouveau template email, route `/invite/<token>` côté frontend.

**Effort estimé :** ~1 jour.

**Approche suggérée :** `superpowers:brainstorming` (30 min) puis spec + plan.

---

### 6. B2 — Lier un RDV créé par le pro à un client existant

**Symptôme :** quand le pro crée un RDV manuellement pour un de ses clients fidèles, il doit re-saisir nom/téléphone/email à la main, créant des doublons dans son CRM.

**Cible :** dans le formulaire de création de RDV (côté pro), un sélecteur **"client existant"** qui pioche dans la liste des `SalonClient` du tenant. Avec recherche par nom/email. Si le client n'existe pas, fallback sur la saisie manuelle qui crée un nouveau client.

**Pourquoi P1 :** **frustration quotidienne** pour les pros qui ont déjà 10+ clients. Plus le pro a de clients, plus c'est pénible.

**Effort estimé :** 4-6h (composant sélecteur frontend + endpoint search côté backend si pas déjà là).

**Approche suggérée :** brainstorming court (15 min) puis spec.

---

## 🥉 NIVEAU 3 — POSITIONNEMENT MARKETING (chantier majeur, semaine 2-3)

### 7. C1-C4 — Système commercial / affiliation 🟠

**Vision :** des gens externes peuvent **devenir commerciaux LuxPretty**, démarcher des salons, et toucher une commission via code promo unique.

**C1 — Onboarding commercial :**
1. Visiteur clique "Devenir commercial" sur la home/page dédiée
2. Remplit un formulaire avec **questions de qualification** :
   - Pourquoi pensez-vous pouvoir vendre LuxPretty ?
   - Votre réseau actuel dans la beauté/wellness
   - Expérience commerciale précédente
   - Combien de salons pensez-vous pouvoir contacter par mois ?
3. Submission → statut PENDING en base
4. Admin reçoit notif + voit la candidature dans son dashboard
5. Admin valide ou rejette manuellement
6. Si validé → role passe à `COMMERCIAL`, accès aux outils de génération de code promo

**C2 — Codes promo, 2 types :**

| Type | Réduction | Mécanisme | Validité |
|---|---|---|---|
| **CODE_25** | 25% | Cashback différé : remboursement au bout d'1 an d'abonnement actif | Code valide 3 ans |
| **CODE_15** | 15% | Immédiat sur l'abonnement | Code valide 2 ans |

⚠️ **À CLARIFIER** demain : la durée "2 ans / 3 ans" — c'est la validité du code (jusqu'à ce qu'il puisse être utilisé) ? OU la durée pendant laquelle la réduction s'applique sur l'abonnement du salon ? OU la durée de la commission du commercial ? Tu m'as donné une formulation ambiguë hier ("limite de temps pour chaque client de 2 pour le 15% et 3 ans pour le 25%").

**C3 — Saisie du coupon par le nouveau client (= nouveau salon pro) :**
À l'inscription du salon pro, champ optionnel "Code promo". Au moment du paiement Stripe, appliquer la réduction. Tracer l'usage côté `PromoCode` (qui l'a utilisé).

**C4 — Tableau de bord commercial :**
Le commercial voit ses codes générés, les salons qui ont utilisé chacun, sa commission cumulée (différée pour 25%, immédiate pour 15%).

**Pourquoi P1 :** **différenciateur marketing fort**. Si tu arrives à mobiliser 5 commerciaux qui ramènent chacun 10 salons en 6 mois, tu doubles ta croissance gratis. Mais c'est un gros chantier — pas avant que les bases (sécurité, bugs P0) soient solides.

**Dépendances :**
- Stripe doit être opérationnel (chantier `project_pending_payments.md` en mémoire)
- Mail outbox doit être opérationnel (chantier `project_pending_mail_outbox.md`)
- Décision Q1 (un user = combien de rôles cumulables) doit être tranchée

**Effort estimé :** 1-2 semaines (gros chantier, à découper en 3-4 PRs).

**Approche suggérée :** session brainstorming dédiée d'1h pour cadrer, puis spec → plan → exécution.

---

### 8. H1-H2 — Différenciation home + page commerciaux

**H1 — Section "Pourquoi LuxPretty est différent" sur la home :**

Bloc de 8 différenciateurs en grille (4×2 ou 2×4) :
- 📋 Suivi client (historique, notes, photos)
- 📱 Posts en scroll infini
- 💳 Paiement en ligne sans marge supplémentaire pour le client
- 📊 Dashboard pro temps réel
- 📅 Prise de RDV 2 clics
- 👥 Gestion créneaux + employés
- 📝 Gestion RH (congés, PDF maladie, contrats)
- 💰 Commerciaux récompensés (lien vers H2)

**H2 — Page dédiée `/commerciaux` :**
Hero "Gagnez en référant des salons" + 3 étapes (candidater → être validé → générer codes) + système de commission + FAQ + CTA "Postuler".

**Pourquoi P1 (et pas P0) :** marketing pur, vient **après** que le système commercial existe (C1-C4). Sinon on parle d'un truc inexistant.

**Effort estimé :** 1 jour (2 sections frontend + i18n + assets).

**Approche suggérée :** brainstorming visuel (`brainstorming` + visual companion) pour le design, puis spec.

---

## 🟡 NIVEAU 4 — Métier qui mature (mois 2)

### 9. P1 — Validation manuelle des RDV pris à moins de 24h

**Règle :** si un client tente de prendre RDV moins de 24h avant le créneau, le RDV passe en `PENDING` (au lieu de `CONFIRMED`) et nécessite l'acceptation du pro.

**Paramétrable par le pro :** dans les settings du salon, un champ "Délai au-delà duquel les RDV sont auto-confirmés" (en heures, valeur par défaut : 24). Le pro peut mettre 0 (jamais de validation) ou 48 (validation si moins de 48h).

**Pourquoi P2 :** protège le pro contre les RDV de dernière minute non souhaités. Pas critique en V1 (peu de volume), critique au scale.

**Existing memory :** déjà mentionné dans `project_pending_review_booking.md` (modales approve/reject).

**Effort estimé :** ~1 jour.

---

### 10. P2 — Anti-abus annulations en chaîne (garde-fous)

**Symptôme :** un client malveillant prend des RDV puis les annule à répétition → bloque les créneaux d'autres clients, fait perdre du temps au pro.

**Garde-fous possibles** (à brainstormer) :
- Limite N annulations/mois par client → après N, ses bookings passent automatiquement en PENDING
- Annulation < 2h avant le RDV = comptée comme NO_SHOW
- Email de prévention quand le client approche le seuil
- Côté pro : badge "client à risque" sur les profils qui annulent souvent
- Score `ClientReliabilityScore` computed à partir de l'historique

**Pourquoi P2 :** mineur en V1, indispensable avant scale.

**Effort estimé :** ~1 jour (logique + UI badge + emails).

---

### 11. P3 — Choix paiement online vs au salon (côté client)

À la fin du booking, **demander au client** :
- 💳 "Payer maintenant en ligne" (carte → Stripe, RDV CONFIRMED instantanément)
- 🏪 "Payer chez le pro" (avertir : "Le pro peut demander un acompte ou empreinte CB selon ses règles")

**Settings côté pro :** toggle "Accepter paiements en salon ?". Si désactivé, seul l'online est proposé.

**Dépendances :** Stripe doit être live (`project_pending_payments.md`). Cette feature s'intègre probablement DANS ce chantier — à vérifier au moment d'aborder Stripe.

**Effort estimé :** ~0.5 jour (intégré au chantier Stripe).

---

## 🔵 NIVEAU 5 — Outils internes (quand >50 users)

### 12. A1 — Tableau admin de tous les utilisateurs

Un super-admin (`Role.ADMIN`) doit voir un tableau de **tous les users** avec :
- Email, nom, date inscription, dernière connexion
- Rôle (USER, PRO, EMPLOYEE, COMMERCIAL, ADMIN)
- Pour les pros : salon associé, statut abonnement, CA mensuel
- Pour les commerciaux : nb codes générés, commission cumulée
- Pour les clients : nb bookings, taux annulation
- Actions : voir détail, désactiver, forcer reset password

**Filtres :** par rôle, statut, date d'inscription, search par email/nom.

**Pourquoi P3 :** utile dès qu'il y a >50 users à gérer. Pas avant.

**Effort estimé :** ~1-2 jours.

---

### 13. A2 — Validation des candidatures commerciales (dépend de C1)

Page admin pour voir les candidatures commerciales PENDING (cf. C1), accepter/rejeter avec justification.

**Dépendance :** C1 doit être implémenté avant.

**Effort estimé :** ~0.5 jour (s'intègre dans A1).

---

## ⚪ À SCOPER AVANT DE TRIER

### 14. B3 — Mails de notification (à scoper)

**Statut :** tu as dit "mails de notification" sans préciser lesquels. Liste actuelle envoyée par le backend (vérifiée hier) :
- ✅ Booking confirmation
- ✅ Booking notification pro
- ✅ Welcome pro
- ✅ Password reset

**Probablement manquants :**
- Booking reminder J-1 (rappel au client la veille du RDV)
- Booking annulé (notif au pro et au client)
- Booking modifié (notif au client)
- Nouveau message dans une conversation (si messagerie ajoutée plus tard)
- Welcome client (s'inscrit, pas reçu d'email aujourd'hui ?)
- Invitation employé (cf. E1)
- Notification commerciale (candidature reçue par admin, candidature acceptée)

**À faire demain (10 min) :** trancher quels emails sont vraiment importants pour V1, puis créer une mini-spec.

---

## Ordre d'attaque recommandé

```
Jour 1 (urgent) :
  ├─ B1 (bug post→booking quand logué)            ~2h
  ├─ Q1 (décision pro+client cumulés)              ~30 min discussion
  └─ Start PR1 sécurité (Cloudflare+Turnstile)     ~6h

Jour 2 :
  ├─ Finish PR1 sécurité
  ├─ B4 (tests transactions concurrentes)          ~3h
  └─ B2 (lier RDV à client existant)               ~4h

Jour 3 :
  ├─ E1 (invitation employé)                       ~1 jour

Semaine 2 :
  ├─ Brainstorm + spec + plan C1-C4 commercial    ~2 jours
  └─ Exécution C1-C4                                ~1 semaine

Semaine 3 :
  ├─ H1-H2 home + page commerciaux                 ~1 jour
  └─ P1+P2 booking policies                        ~2 jours

Mois 2 :
  ├─ P3 paiement (avec Stripe)                     ~0.5 jour
  ├─ A1+A2 admin                                   ~2 jours
  └─ B3 emails restants                            ~1 jour
```

---

## Hors backlog mais à garder à l'œil

D'autres chantiers déjà en attente dans la mémoire (mentionnés à titre informatif, **pas dans ce tri**) :

- **PR2-PR6 sécurité** (rate limit Bucket4j, account hardening, headers, upload, audit) — après PR1
- **Stripe payments** (3 PRs, ~5j) — dépend de mail outbox
- **Mail outbox + Postmark** (2 PRs) — prérequis Stripe + emails restants
- **Refonte page salon PC** — branche reset, idées à conserver
- **Refonte home** ("search-first raffiné" validée 2026-05-06)
- **Pro déclare ses catégories** — nécessaire pour filtre `/discover`
- **Migration images R2** — PR2 du chantier storage
- **Flyway legacy tenant baselining** — passer ddl-auto à validate

Voir le `MEMORY.md` global et `project_prod_readiness.md` pour la liste complète.

---

## Comment démarrer demain dans un nouveau contexte

1. Ouvre une nouvelle session Claude Code
2. Dis :
   > "Charge `docs/superpowers/backlog/2026-05-11-priorities.md`. On attaque l'item N°1 (bug B1)."
3. Claude lira ce doc, comprendra l'urgence, et lancera directement `superpowers:systematic-debugging` sur B1.
4. Une fois B1 fixé, passer à Q1 (discussion architecturale 30 min), puis à PR1 sécurité (déjà spec-é, juste à exécuter via subagent-driven-development).
