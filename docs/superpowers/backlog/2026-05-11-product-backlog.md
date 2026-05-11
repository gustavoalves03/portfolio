# Backlog produit LuxPretty — brain-dump 2026-05-11

> Notes vrac structurées pour reprise demain. Pas encore des specs — juste un inventaire trié. Chaque item devra passer par `brainstorming` → spec → plan avant exécution.

---

## 🐛 Bugs à corriger

### B1. Réservation depuis un post quand le client est connecté
**Symptôme :** logué en tant qu'utilisateur, le bouton "réserver" sur un post ne déclenche plus la modale de booking.
**À investiguer :** vraisemblablement le flow `salon-posts-viewer` → `bookCare.emit(careId)` ne remonte plus correctement vers la page parent quand un user est authentifié.
**Priorité :** 🔴 P0 — bloque une feature critique (conversion post → booking).

### B2. Création de RDV par le pro — lier à un client existant
**Symptôme :** quand le pro crée un RDV pour un de ses clients, il faut pouvoir associer ce RDV à un client **déjà créé par le pro** (dans son CRM interne) plutôt que de toujours saisir un nouveau client à la main.
**À ajouter :** dans le formulaire de création de RDV (côté pro), un sélecteur "client existant" qui pioche dans la liste des clients du pro.
**Priorité :** 🟠 P1.

### B3. Mails de notification
**Symptôme :** mentionné comme TODO, à clarifier demain.
**À investiguer :** quels emails sont déjà envoyés (booking confirmation, welcome pro, password reset existent côté backend), lesquels manquent (booking reminder J-1 ? annulation ? RDV modifié ?).
**Priorité :** à scoper — dépend de la liste exacte des emails manquants.

### B4. Tester les transactions concurrentes (prise de RDV au même moment)
**Symptôme :** deux clients qui réservent le même créneau au même millième de seconde — quel comportement ? Tests à écrire pour s'assurer qu'un seul gagne et l'autre reçoit un 409.
**À ajouter :** tests d'intégration sur `CareBookingService` avec scénario concurrent (deux threads, même slot, vérifier qu'un seul commit). Vérifier le locking JPA (`@Version` optimistic lock sur les `BlockedSlot` ou unique constraint DB sur `(employee_id, date, time)`).
**Priorité :** 🟠 P1 — risque réel en prod multi-clients.

---

## 🏠 Page d'accueil — différenciation concurrence

### H1. Section "Pourquoi LuxPretty est différent"
Ajouter sur la home (probablement entre la search bar et le hero discover) un bloc qui liste les **différenciateurs vs concurrence** :

- 📋 **Suivi client** : historique complet, notes de soin, photos avant/après
- 📱 **Posts en scroll infini** : vitrine type Instagram, plus engageant qu'une grille
- 💳 **Paiement en ligne sans marge supplémentaire pour le client** : le client paie le même prix qu'en salon, la commission est sur le pro
- 📊 **Dashboard pro** : KPIs en temps réel (CA semaine, taux occupation, etc.)
- 📅 **Prise de rendez-vous facile** : 2 clics depuis n'importe quel post ou la home
- 👥 **Gestion créneaux + employés** : planning des employés, congés, blocages
- 📝 **Gestion RH simplifiée** : demandes de congé, PDF maladie, contrats stockés
- 💰 **Commerciaux récompensés** : possibilité de gagner une commission en référant le service (lien vers la page commerciaux H2)

**Format :** liste icônes + titre + 1 phrase. 8 items en grille 4×2 ou 2×4. Style cohérent avec le footer F2 (fond clair, accent or sur les icônes).

**À cadrer en brainstorm :** copy précise FR/EN, ordre des items, design (carte simple ou avec illustration).

### H2. Page dédiée "Devenir commercial"
Nouvelle route `/commerciaux` (ou `/devenir-partenaire`) — page marketing dédiée aux gens qui veulent vendre LuxPretty et toucher une commission via code promo.

**Contenu cible :**
- Hero "Gagnez en référant des salons à LuxPretty"
- Comment ça marche en 3 étapes (candidater → être validé → générer codes)
- Système de commission (voir section 💼 commerciaux ci-dessous)
- FAQ
- CTA "Postuler" → formulaire avec questions de qualification

**Dépendances :** section 💼 (commerciaux) doit être implémentée backend avant que cette page soit fonctionnelle.

---

## 👔 Système commercial / affiliation

### C1. Onboarding commercial
**Flow :**
1. Un visiteur clique "Devenir commercial" sur la home ou `/commerciaux`.
2. Il remplit un formulaire avec des **questions de qualification** :
   - Pourquoi pensez-vous pouvoir vendre LuxPretty ?
   - Votre réseau actuel dans la beauté/wellness
   - Expérience commerciale précédente
   - Combien de salons pensez-vous pouvoir contacter par mois ?
   - Autres questions à définir
3. Submission → entry "commercial_applications" en base avec statut PENDING.
4. **Admin reçoit notif + voit la candidature dans son dashboard.**
5. Admin valide ou rejette manuellement.
6. Si validé → user role passe à `COMMERCIAL`, accès aux outils de génération de code promo.

### C2. Génération de codes promo
Chaque commercial peut générer **un code promo unique par client (salon pro)** qu'il a démarché.

**Deux types de codes :**

| Type | Réduction | Quand | Limite |
|---|---|---|---|
| **CODE_25** | 25% | À récupérer au bout de **1 an** (cashback différé) | Valable **3 ans** max |
| **CODE_15** | 15% | **Immédiat** sur l'abonnement | Valable **2 ans** max |

(NB user a dit "limite de temps pour chaque client de 2 pour le 15% et 3 ans pour le 25%" — à confirmer demain : ce sont les durées de validité du code ? ou la durée de cashback ?)

**Modèle data envisagé** (à valider en brainstorm) :
```
PromoCode {
  id, code (unique), commercialId, salonId (nullable jusqu'à utilisation),
  type (CODE_25 | CODE_15), createdAt, usedAt, expiresAt
}
```

### C3. Saisie du coupon par le nouveau client (= nouveau salon pro)
À l'inscription d'un **salon pro**, ajouter un champ optionnel "Code promo" sur le formulaire de souscription. Au moment du paiement Stripe, appliquer la réduction. Mettre à jour `usedAt` + `salonId` sur le `PromoCode`.

### C4. Tableau de bord commercial
Le commercial doit voir :
- Ses codes générés (avec leur état : non utilisé / utilisé / expiré)
- Les salons qui ont utilisé son code (avec leur statut d'abonnement, pour calculer sa commission)
- Sa commission cumulée (différée pour CODE_25, immédiate pour CODE_15)

---

## 👥 Gestion des employés (pro)

### E1. Création employé via invitation (au lieu de création directe)
**Aujourd'hui :** le pro crée le compte employé en saisissant son email/mot de passe → l'employé reçoit ses credentials.

**Cible :** flow type Slack/Notion :
1. Pro saisit l'email de l'employé dans son back-office.
2. Backend génère un token d'invitation, envoie un email avec un lien `/invite/<token>`.
3. L'employé clique le lien → page "Vous avez été invité chez `<salon>` par `<pro>`" → set son mot de passe (ou login Google).
4. Le compte est créé directement avec rôle `EMPLOYEE` et lié au tenant du salon.
5. Le pro voit l'employé apparaître dans sa liste une fois l'invitation acceptée.

**Avantages :**
- Pas de fuite de mot de passe (le pro ne tape jamais le password de l'employé).
- L'employé choisit son propre mot de passe.
- Compatible OAuth Google (l'employé peut se connecter via Google directement).

**Dépendances :** modèle data `EmployeeInvitation { token, salonId, email, expiresAt, acceptedAt }`. Email template à créer.

**Priorité :** 🟠 P1.

---

## 📅 Booking — politiques métier

### P1. Validation manuelle des RDV pris à moins de 24h
**Règle :** si un client tente de prendre RDV moins de 24h avant le créneau, le RDV passe en **PENDING** (au lieu de CONFIRMED) et nécessite l'acceptation du pro.

**Paramétrable par le pro :** dans les settings du salon, un champ "Délai au-delà duquel les RDV sont auto-confirmés" (en heures, valeur par défaut : 24). Le pro peut mettre 0 (= jamais de validation manuelle, tout auto) ou 48 (= validation manuelle si moins de 48h avant).

**Status :** modèle `CareBooking` a déjà `PENDING` (vérifier — il me semble que oui d'après le code OAuth user qu'on a vu).

**Existing memory :** `project_pending_review_booking.md` mentionne déjà la feature future "modales approve/reject booking". Cette règle s'inscrit dedans.

**Priorité :** 🟠 P1.

### P2. Anti-abus annulations en chaîne
**Symptôme :** un client malveillant prend des RDV puis les annule à répétition → bloque les créneaux d'autres clients potentiels, fait perdre du temps au pro.

**Garde-fou possibles** (à brainstormer demain) :
- Limite N annulations / mois par client → après N, ses bookings passent automatiquement en PENDING (validation manuelle pro)
- Annulation < 2h avant le RDV = considérée comme NO_SHOW (pénalité dans la stat anti-abus)
- Email de prévention quand le client approche le seuil
- Affichage côté pro : badge "client à risque" sur les clients qui annulent souvent

**Modèle :** `ClientReliabilityScore` (computed) basé sur historique. À spec.

**Priorité :** 🟡 P2 (pas urgent en V1 mais important avant scale).

### P3. Paiement client : choix dans l'app vs en salon
À la fin du booking, **demander au client** :
- 💳 "Payer maintenant en ligne" (carte → Stripe, RDV CONFIRMED instantanément)
- 🏪 "Payer chez le pro" (avertissement : "Le pro peut demander un acompte ou une carte d'empreinte selon ses règles")

**Settings côté pro :** option "Accepter les paiements en salon ?" (toggle). Si désactivé, seul le paiement en ligne est proposé.

**Existing memory :** `project_pending_payments.md` couvre déjà Stripe — cette feature s'intègre là-dedans, à voir si déjà couverte ou à compléter.

**Priorité :** 🟠 P1.

---

## 🛠️ Admin dashboard

### A1. Table de tous les utilisateurs
Un super-admin (`Role.ADMIN`) doit voir un tableau de **tous les users** avec :
- Email, nom, date d'inscription, dernière connexion
- Rôle (USER, PRO, EMPLOYEE, COMMERCIAL, ADMIN)
- Pour les pros : salon associé, statut abonnement, CA mensuel
- Pour les commerciaux : nb de codes générés, commission cumulée
- Pour les clients : nb de bookings, taux d'annulation
- Actions : voir détail, désactiver, forcer reset password

**Filtres :** par rôle, par statut, par date d'inscription, search par email/nom.

**Priorité :** 🟠 P1 (utile dès qu'il y a > 50 users).

### A2. Validation des candidatures commerciales (cf. C1)
Page dédiée dans l'admin pour voir les candidatures commerciales en attente, accepter/rejeter avec justification.

---

## ❓ Question architecturale ouverte

### Q1. Un user peut-il être à la fois pro et client ?
**Constat actuel :** le compte PRO peut prendre des RDV (donc agir comme client) mais n'a pas de "suivi client" associé (pas d'historique propre en tant que client).

**3 approches possibles :**

**A. Rôles cumulables** : un user a un set de roles (`USER + PRO`). Le côté client de son profil contient l'historique de ses bookings personnels (où il est client), le côté pro contient le salon qu'il opère.
- ✅ Reflète la réalité (un esthéticien peut aussi prendre RDV ailleurs)
- ❌ Complexité UX : "tu es sur quelle interface aujourd'hui ?"

**B. Comptes séparés** : si un pro veut prendre des RDV, il crée un 2e compte client avec un autre email.
- ✅ Simple, sans ambiguïté
- ❌ Friction utilisateur

**C. PRO inclut implicitement USER** : tout PRO est aussi USER pour les fonctions client (historique, notes), avec un toggle UI pour basculer.
- ✅ Pas de double-compte
- ✅ Cohérent avec l'usage réel
- ❌ Complexité côté backend (séparer ses données pro vs client)

**À trancher avant** : tout dépend de qui prend les RDV depuis le compte PRO en pratique. À investiguer.

**Priorité :** 🟡 P2 — pas bloquant, mais à clarifier avant V1 commerciale.

---

## 📋 Ordre suggéré pour demain

1. **Discuter Q1** (5 min) — l'architectural impacte tout le reste
2. **Spec B1** (bug réservation depuis post) — un PR isolé rapide
3. **Spec C1+C2+C3** (système commercial) — chantier majeur, mais c'est ton positionnement marketing différenciant
4. **Spec E1** (invitation employé) — UX critique
5. **Spec H1+H2** (différenciation home + page commerciaux) — marketing front
6. **Spec P1+P2+P3** (booking policies) — peut attendre PR Stripe live
7. **Spec A1+A2** (admin dashboard) — quand tu en as vraiment besoin

---

## 🔗 Liens vers les chantiers en cours

- **Sécurité PR1** (Cloudflare + Turnstile) : spec + plan déjà écrits, à exécuter — `docs/superpowers/specs/2026-05-11-cloudflare-turnstile-design.md` + plan `2026-05-11-cloudflare-turnstile.md`
- **Security roadmap** (6 PRs) : `docs/superpowers/specs/2026-05-11-security-roadmap.md`
- **Memory backlog produit pré-existant** : `memory/project_prod_readiness.md` (items 7-25 à croiser avec ce doc)
