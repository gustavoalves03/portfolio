---
stepsCompleted: ['step-01-document-discovery', 'step-02-prd-analysis', 'step-03-epic-coverage-validation', 'step-04-ux-alignment', 'step-05-epic-quality-review', 'step-06-final-assessment']
status: 'complete'
documentsUsed:
  prd: "_bmad-output/planning-artifacts/prd.md"
  architecture: "_bmad-output/planning-artifacts/architecture.md"
  epics: "_bmad-output/planning-artifacts/epics.md"
  ux: "_bmad-output/planning-artifacts/ux-design-specification.md"
---

# Implementation Readiness Assessment Report

**Date:** 2026-03-05
**Project:** Pretty Face (SaaS B2B Beauté)

---

## Document Inventory

| Type | Fichier | Taille | Date |
|------|---------|--------|------|
| PRD | `prd.md` | 27 KB | 05 mars 2026 |
| Architecture | `architecture.md` | 38 KB | 23 fév 2026 |
| Epics & Stories | `epics.md` | 45 KB | 05 mars 2026 |
| UX Design | `ux-design-specification.md` | 4,6 KB | 05 mars 2026 |
| PRD Validation | `prd-validation-report.md` | 18 KB | 05 mars 2026 (référence) |

Aucun doublon détecté. Tous les documents requis présents.

---

## PRD Analysis

### Functional Requirements

FR1: Un professionnel peut créer un compte avec email/mot de passe
FR2: Un professionnel peut se connecter via Google OAuth
FR3: Un professionnel peut se connecter via Facebook OAuth
FR4: Un professionnel peut se connecter via Apple OAuth
FR5: Un client peut créer un compte avec email/mot de passe
FR6: Un client peut se connecter via Google OAuth
FR7: Un client peut se connecter via Facebook OAuth
FR8: Un client peut se connecter via Apple OAuth
FR9: Un utilisateur peut réinitialiser son mot de passe par email
FR10: Un utilisateur peut se déconnecter de son compte
FR11: Un professionnel peut configurer les informations de son établissement (nom, logo, description)
FR12: Un professionnel peut créer des catégories de prestations
FR13: Un professionnel peut créer des prestations avec nom, description, prix et durée
FR14: Un professionnel peut associer une prestation à une catégorie
FR15: Un professionnel peut ajouter des photos à une prestation
FR16: Un professionnel peut modifier une prestation existante
FR17: Un professionnel peut supprimer une prestation
FR18: Un professionnel peut réorganiser l'ordre d'affichage des prestations
FR19: Un professionnel peut activer/désactiver une prestation (sans la supprimer)
FR20: Un professionnel peut définir ses horaires d'ouverture par jour de la semaine
FR21: Un professionnel peut bloquer un créneau spécifique (indisponibilité ponctuelle)
FR22: Un professionnel peut débloquer un créneau précédemment bloqué
FR23: Un professionnel peut annuler tous les RDV d'une journée (maladie, imprévu)
FR24: Le système notifie automatiquement les clients affectés par une annulation groupée
FR25: Un visiteur peut consulter la vitrine d'un salon sans être connecté
FR26: Un visiteur peut voir la liste des prestations avec prix et durées
FR27: Un visiteur peut voir les photos associées aux prestations
FR28: Un visiteur peut voir les informations de l'établissement (nom, description)
FR29: Un client peut voir les créneaux disponibles pour une prestation donnée
FR30: Un client peut sélectionner un créneau et confirmer sa réservation
FR31: Un client doit être connecté pour finaliser une réservation
FR32: Le système envoie une confirmation de réservation par email au client
FR33: Le système envoie une notification de nouvelle réservation au professionnel
FR34: Le système envoie un rappel par email au client la veille du RDV
FR35: Un client peut voir la liste de ses RDV à venir
FR36: Un client peut voir la liste de ses RDV passés
FR37: Un client peut annuler un RDV à venir
FR38: Un client peut reporter un RDV vers un autre créneau disponible
FR39: Le système notifie le professionnel lors d'une annulation ou d'un report
FR40: Un professionnel peut voir la liste de ses RDV du jour
FR41: Un professionnel peut voir son planning sur une vue calendrier (semaine/mois)
FR42: Un professionnel peut voir les détails d'un RDV (client, prestation, heure)
FR43: Un professionnel peut annuler un RDV existant
FR44: Le système notifie automatiquement le client en cas d'annulation par le pro
FR45: Un professionnel peut connecter son compte Google Calendar
FR46: Un professionnel peut exporter ses RDV au format iCal
FR47: Les nouveaux RDV sont automatiquement synchronisés avec le calendrier connecté
FR48: Un professionnel peut voir son chiffre d'affaires du mois en cours
FR49: Un professionnel peut voir le nombre de réservations du mois en cours
FR50: Un professionnel peut voir l'évolution de son CA mois par mois
FR51: Un professionnel peut voir l'évolution de ses réservations mois par mois
FR52: Un professionnel peut voir son taux de progression en pourcentage (CA et réservations) vs mois précédent
FR53: Le système isole les données de chaque salon dans un schéma séparé
FR54: Un professionnel ne peut accéder qu'aux données de son propre salon
FR55: Un client peut réserver auprès de plusieurs salons différents
FR56: Le système crée automatiquement un nouveau schéma lors de l'inscription d'un salon

**Total FRs : 56**

### Non-Functional Requirements

NFR1: Disponibilité ≥ 99,5% pendant les heures de réservation (8h–20h)
NFR2: RTO < 30 minutes pour les incidents critiques
NFR3: Pages vitrine < 2s au P95 sur 4G mobile
NFR4: API réservation < 500ms au P95 (≤ 50 req/min)
NFR5: Dashboard pro < 3s au P95 avec 500 réservations historiques
NFR6: Données chiffrées au repos (AES-256) et en transit (TLS 1.2+)
NFR7: OAuth2 tokens durée limitée (access ≤ 1h, refresh ≤ 30j) avec rotation
NFR8: Rate limiting connexion : blocage 15min après 5 tentatives échouées
NFR9: Zéro fuite de données entre tenants, vérifiée par tests automatisés
NFR10: Création nouveau schéma tenant < 10 secondes
NFR11: Architecture supporte 100 salons actifs simultanément sans dégradation SLA

**Total NFRs : 11**

### Additional Requirements

- **RGPD :** Consentement explicite, droit d'accès (< 30j), droit à l'effacement, rétention 3 ans (données RDV), cookies opt-in
- **Multi-tenancy :** Provisioning automatique de schéma Oracle à l'inscription
- **Auth progressif :** Google OAuth en premier, Facebook/Apple ensuite
- **Modèle freemium :** Post to Play (3 posts/semaine, grâce 7 jours) — Phase Growth uniquement
- **Calendrier :** Google Calendar + iCal export

### PRD Completeness Assessment

Le PRD est **bien structuré et complet** pour un MVP. Les 56 FRs couvrent les 5 parcours utilisateurs documentés. Les NFRs sont mesurables et précis. Points forts : isolation multi-tenant clairement définie, RGPD traité sérieusement, périmètre MVP/Growth/Vision clairement délimité.

---

## Epic Coverage Validation

### Coverage Matrix

| FR | Exigence PRD (résumé) | Couverture Epic | Statut |
|----|----------------------|-----------------|--------|
| FR1 | Compte pro email/mdp | Epic 1 | ✅ Couvert |
| FR2 | Pro login Google OAuth | Epic 1 | ✅ Couvert |
| FR3 | Pro login Facebook OAuth | Epic 1 | ✅ Couvert |
| FR4 | Pro login Apple OAuth | Epic 1 | ✅ Couvert |
| FR5 | Compte client email/mdp | Epic 5 | ✅ Couvert |
| FR6 | Client login Google OAuth | Epic 5 | ✅ Couvert |
| FR7 | Client login Facebook OAuth | Epic 5 | ✅ Couvert |
| FR8 | Client login Apple OAuth | Epic 5 | ✅ Couvert |
| FR9 | Réinitialisation mot de passe | Epic 1 | ✅ Couvert |
| FR10 | Déconnexion | Epic 1 | ✅ Couvert |
| FR11 | Config établissement (nom, logo, desc) | Epic 2 | ✅ Couvert |
| FR12 | Créer catégories prestations | Epic 2 | ✅ Couvert |
| FR13 | Créer prestations (nom, prix, durée) | Epic 2 | ✅ Couvert |
| FR14 | Associer prestation à catégorie | Epic 2 | ✅ Couvert |
| FR15 | Ajouter photos à prestation | Epic 2 | ✅ Couvert |
| FR16 | Modifier prestation existante | Epic 2 | ✅ Couvert |
| FR17 | Supprimer prestation | Epic 2 | ✅ Couvert |
| FR18 | Réorganiser ordre affichage | Epic 2 | ✅ Couvert |
| FR19 | Activer/désactiver prestation | Epic 2 | ✅ Couvert |
| FR20 | Définir horaires d'ouverture | Epic 4 | ✅ Couvert |
| FR21 | Bloquer créneau spécifique | Epic 4 | ✅ Couvert |
| FR22 | Débloquer créneau | Epic 4 | ✅ Couvert |
| FR23 | Annuler tous les RDV d'une journée | Epic 4 | ✅ Couvert |
| FR24 | Notifier clients annulation groupée | Epic 4 | ✅ Couvert |
| FR25 | Visiteur consulte vitrine sans login | Epic 3 | ✅ Couvert |
| FR26 | Visiteur voit prestations, prix, durées | Epic 3 | ✅ Couvert |
| FR27 | Visiteur voit photos prestations | Epic 3 | ✅ Couvert |
| FR28 | Visiteur voit infos établissement | Epic 3 | ✅ Couvert |
| FR29 | Client voit créneaux disponibles | Epic 5 | ✅ Couvert |
| FR30 | Client sélectionne et confirme RDV | Epic 5 | ✅ Couvert |
| FR31 | Client doit être connecté pour réserver | Epic 5 | ✅ Couvert |
| FR32 | Email confirmation au client | Epic 5 | ✅ Couvert |
| FR33 | Notification nouvelle réservation au pro | Epic 5 | ✅ Couvert |
| FR34 | Email rappel client la veille | Epic 5 | ✅ Couvert |
| FR35 | Client voit RDV à venir | Epic 6 | ✅ Couvert |
| FR36 | Client voit RDV passés | Epic 6 | ✅ Couvert |
| FR37 | Client annule RDV | Epic 6 | ✅ Couvert |
| FR38 | Client reporte vers autre créneau | Epic 6 | ✅ Couvert |
| FR39 | Pro notifié annulation/report client | Epic 6 | ✅ Couvert |
| FR40 | Pro voit RDV du jour | Epic 6 | ✅ Couvert |
| FR41 | Pro vue calendrier semaine/mois | Epic 6 | ✅ Couvert |
| FR42 | Pro voit détails RDV | Epic 6 | ✅ Couvert |
| FR43 | Pro annule RDV | Epic 6 | ✅ Couvert |
| FR44 | Client notifié annulation par pro | Epic 6 | ✅ Couvert |
| FR45 | Pro connecte Google Calendar | Epic 7 | ✅ Couvert |
| FR46 | Pro exporte RDV en iCal | Epic 7 | ✅ Couvert |
| FR47 | Sync nouveaux RDV avec calendrier | Epic 7 | ✅ Couvert |
| FR48 | Pro voit CA mois en cours | Epic 8 | ✅ Couvert |
| FR49 | Pro voit nb réservations mois en cours | Epic 8 | ✅ Couvert |
| FR50 | Pro voit évolution CA mois/mois | Epic 8 | ✅ Couvert |
| FR51 | Pro voit évolution réservations mois/mois | Epic 8 | ✅ Couvert |
| FR52 | Pro voit taux de progression vs mois précédent | Epic 8 | ✅ Couvert |
| FR53 | Isolation schéma par salon | Epic 1 | ✅ Couvert |
| FR54 | Pro accès uniquement à son propre salon | Epic 1 | ✅ Couvert |
| FR55 | Client réserve auprès de plusieurs salons | Epic 3 | ✅ Couvert |
| FR56 | Création automatique schéma à l'inscription | Epic 1 | ✅ Couvert |

### Missing Requirements

Aucun FR manquant détecté.

### Coverage Statistics

- Total FRs PRD : **56**
- FRs couverts dans les epics : **56**
- Taux de couverture : **100%**

---

## UX Alignment Assessment

### UX Document Status

✅ **Trouvé** — `ux-design-specification.md` (4,6 KB, 05 mars 2026)

### UX ↔ PRD Alignment

| Élément UX | Alignement PRD |
|------------|---------------|
| Home client-first `/` avec barre de recherche | Couvert par FR25-FR28 (vitrine publique) + architecture `/` |
| Vitrine salon `/[salon-slug]/` | Couvert par FR25-FR28 + architecture `/salon/:slug` |
| Onboarding pro en < 20 min, vitrine en "draft" | Couvert par FR11-FR19 (config vitrine) + Epic 2 |
| Mobile-first tunnel réservation | Couvert par FR29-FR34 (réservation) + NFR3 (< 2s P95 4G) |
| Dashboard "moment wow" stats | Couvert par FR48-FR52 (dashboard stats) + Epic 8 |
| CTA pro discret vers `/pour-les-pros` | ⚠️ Page `/pour-les-pros` non couverte par un FR ou un epic |
| Double audience, espaces post-connexion différenciés | Couvert par RBAC (PRD) + architecture routes `/pro/...` vs public |
| Clarté des statuts (PENDING/CONFIRMED/CANCELLED) | Couvert par Epic 6 (gestion RDV) |

### UX ↔ Architecture Alignment

| Besoin UX | Support Architecture |
|-----------|---------------------|
| Performance vitrine < 2s sur 4G (NFR3) | ✅ SSR Angular + Caffeine cache + HTTP Cache-Control |
| Route `/salon/:slug` pour vitrine | ✅ Définie dans les routes Angular + `@PathVariable slug` backend |
| Home publique découverte salons | ✅ API `/api/feed` + public endpoints définis |
| `/pour-les-pros` landing page B2B | ⚠️ Route absente de l'architecture et des epics |
| Mode "draft" vitrine jusqu'à config minimale | ⚠️ Logique non spécifiée dans l'architecture ni les epics |

### Warnings

⚠️ **Warning 1 — Page `/pour-les-pros` non planifiée**
La UX spec mentionne une landing page B2B séparée (`/pour-les-pros`) avec un CTA discret depuis la home. Cette page n'est couverte par aucun FR, aucun epic ni aucune route architecture. Impact faible pour le MVP mais à inclure dans les epics si souhaité.

⚠️ **Warning 2 — Vitrine "draft mode" partiellement couverte**
La UX spec indique que la vitrine reste privée ("draft") jusqu'à une configuration minimale. La Story 3.3 couvre le comportement côté client (retour 404 si vitrine en draft), mais aucune story dans Epic 2 ne couvre la logique de publication/activation du côté pro (comment Sophie passe de draft → publié). Recommandation : ajouter une story 2.6 "Storefront Publication" dans Epic 2.

---

## Epic Quality Review

### Résumé de la validation

| Epic | Titre | Valeur utilisateur | Indépendance | Stories | Qualité ACs |
|------|-------|-------------------|--------------|---------|-------------|
| Epic 1 | Salon Registration & Multi-Tenant Setup | ✅ | ✅ | 4 stories | ✅ Bon |
| Epic 2 | Salon Storefront Configuration | ✅ | ✅ (dépend Epic 1) | 5 stories | ✅ Bon |
| Epic 3 | Public Storefront & Client Discovery | ✅ | ✅ (dépend Epic 2) | 3 stories | ✅ Bon |
| Epic 4 | Availability Management | ✅ | ✅ (dépend Epic 1) | 3 stories | ✅ Bon |
| Epic 5 | Client Account & Online Booking | ✅ | ✅ (dépend Epics 1,4) | 5 stories | ✅ Bon |
| Epic 6 | Appointment Management (Client & Pro) | ✅ | ✅ (dépend Epics 1,5) | 6 stories | ✅ Bon |
| Epic 7 | Calendar Synchronization | ✅ | ✅ (dépend Epics 1,6) | 2 stories | ✅ Bon |
| Epic 8 | Pro Dashboard & Statistics | ✅ | ✅ (dépend Epics 1,5) | 3 stories | ✅ Bon |

### ✅ Conformité Best Practices

**Valeur utilisateur :** Tous les epics sont orientés utilisateur, aucun epic "technique" pur (pas de "Setup Database", "Create Models", etc.). Les titres décrivent ce que l'utilisateur peut accomplir.

**Indépendance des epics :** Les dépendances sont en cascade naturelle (Epic N utilise les outputs d'epics précédents, jamais de dépendances inverses). Epic 1 est entièrement autonome.

**Format des stories :** Toutes les stories suivent le format "As a [role], I want [action], So that [outcome]". Structure BDD Given/When/Then respectée systématiquement.

**Critères d'acceptation :** Bien structurés, testables, couvrant happy path + edge cases + cas d'erreur. Les NFRs de performance sont intégrés dans les ACs concernés (ex. Story 5.4 : 500ms P95, Story 8.1 : 3s P95).

**Contexte brownfield :** Les epics reconnaissent le code existant (FR comme prestations, catégories, booking partiel, OAuth2 Google) sans le récrire inutilement.

**Gestion DB :** Pas de "créer toutes les tables" en Epic 1. Chaque story implique la création de ses propres entités.

### 🟠 Issues Majeures

**Issue 1 — OAuth2 Facebook et Apple absents des stories**
Les FRs 3, 4, 7, 8 (Facebook OAuth + Apple OAuth pour pro et client) sont couverts dans la coverage map (Epic 1 et Epic 5), mais il n'existe pas de stories dédiées pour ces providers. Seul Google OAuth dispose d'une story (1.2 et 5.2). Les autres providers sont implicitement inclus, mais sans story explicite, il n'y a pas de critères d'acceptation vérifiables pour Facebook et Apple.

**Recommandation :** Soit ajouter des stories 1.2b (Facebook OAuth) et 1.2c (Apple OAuth) côté pro, et 5.2b/5.2c côté client — soit étendre les ACs de 1.2 et 5.2 pour couvrir explicitement les 3 providers avec leurs spécificités (notamment Apple qui impose des contraintes sur la visibilité de l'email).

**Issue 2 — Story manquante : Publication de la vitrine (draft → publié)**
La Story 3.3 fait référence au comportement "draft mode" (AC : si slug en draft → 404), mais aucune story côté pro dans Epic 2 ne couvre l'action de publier/activer sa vitrine. Sophie n'a pas de chemin clair pour passer de "je configure" à "ma vitrine est en ligne".

**Recommandation :** Ajouter Story 2.6 : "Storefront Publication" — AS a pro, I want to publish my storefront when I'm ready, so that clients can find and book me.

### 🟡 Concerns Mineurs

**Concern 1 — Story 7.1 : sécurité URL iCal**
L'AC indique "the endpoint is publicly accessible (no auth required — URL itself acts as the token via the unique slug)". Le slug est prévisible/devinable (nom du salon). Pour un MVP c'est acceptable, mais à noter comme risque sécurité futur (passage à un token UUID privé).

**Concern 2 — Story 5.5 : timing du rappel email**
L'AC précise "le jour avant à 9h" mais ce comportement de scheduling (job cron) n'est pas mentionné dans l'architecture. À confirmer que le scheduler Spring (`@Scheduled`) est inclus dans l'architecture.

**Concern 3 — Epic 3 manque une story de recherche**
Story 3.1 mentionne une barre de recherche/localisation sur la home, et Story 3.2 couvre la découverte par catégorie, mais il n'y a pas de story dédiée à la recherche par localisation géographique. Si ce feature est souhaité pour le MVP, il faut une story explicite.

### Checklist de conformité par epic

| Critère | E1 | E2 | E3 | E4 | E5 | E6 | E7 | E8 |
|---------|----|----|----|----|----|----|----|----|
| Valeur utilisateur | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Indépendance | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Taille stories appropriée | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Pas de dépendances forward | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| ACs en Given/When/Then | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Traçabilité FRs | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Stories complètes pour tous les FRs | ⚠️ | ⚠️ | ✅ | ✅ | ⚠️ | ✅ | ✅ | ✅ |

---

## Summary and Recommendations

### Overall Readiness Status

## ✅ READY — avec corrections recommandées avant sprint planning

Le projet est **prêt pour l'implémentation**. Les fondations de planification sont solides : 56 FRs documentés, 100% couverts dans les epics, architecture complète, ACs de qualité BDD, pas de dépendances circulaires. Les problèmes identifiés sont réparables rapidement et n'empêchent pas le démarrage.

---

### Problèmes Critiques — À Résoudre Avant Sprint Planning

#### 🔴 Critique 1 — OAuth2 Facebook & Apple sans stories ni ACs

**Problème :** FRs 3, 4, 7, 8 (Facebook OAuth + Apple OAuth pro et client) sont dans la coverage map mais sans stories dédiées ni critères d'acceptation vérifiables. Les spécificités Apple (restriction email, "Sign in with Apple" UX) sont particulièrement complexes.

**Action :** Choisir une des deux options avant le sprint planning :
- **Option A (recommandée)** : Étendre les ACs des stories 1.2 et 5.2 pour couvrir explicitement Facebook et Apple avec leurs spécificités
- **Option B** : Ajouter stories 1.2b, 1.2c, 5.2b, 5.2c pour chaque provider

**Impact si ignoré :** Les développeurs implémenteront Google seulement, les autres providers seront oubliés ou mal implémentés.

#### 🔴 Critique 2 — Story manquante : Publication de la vitrine (draft → publié)

**Problème :** La Story 3.3 référence le "draft mode" côté client (404 si vitrine non publiée) mais côté pro, Sophie n'a aucun chemin pour publier sa vitrine. L'action de passer en "live" est absente.

**Action :** Ajouter **Story 2.6 : Storefront Publication**
```
As a beauty professional,
I want to publish my storefront when it's ready,
So that clients can find and book me online.

ACs :
- Given I have completed minimum setup (name + 1 service),
  When I click "Publish my storefront",
  Then my storefront is accessible at /salon/{slug} and visible to clients

- Given my storefront is published,
  When I navigate to settings,
  Then I can unpublish it (returns to draft, slug returns 404)

- Given I haven't completed minimum setup,
  When I try to publish,
  Then I see a checklist of required items before publishing
```

---

### Recommandations — À Considérer

#### 🟠 Important — iCal URL sécurité

Le slug comme "token" de sécurité pour l'endpoint iCal (`/api/salon/{slug}/calendar.ics`) est prévisible. Pour le MVP c'est acceptable, mais ajouter une note dans les tech notes de Story 7.1 pour un futur upgrade vers un token UUID privé.

#### 🟡 Optionnel — Recherche géographique

La home UX mentionne une barre de recherche/localisation. Si ce feature est souhaité pour le MVP, il faut une story explicite. Sinon, le supprimer de la UX spec pour éviter la confusion.

#### 🟡 Optionnel — Page `/pour-les-pros`

Mentionnée dans la UX spec mais absente des FRs, epics et routes architecture. Décision à prendre : inclure dans Epic 3 comme story 3.0 "Pro Landing Page", ou le reporter explicitement en post-MVP.

---

### Recommended Next Steps

1. **Corriger les 2 issues critiques** dans `epics.md` : étendre les ACs OAuth2 multi-providers, ajouter Story 2.6 (draft → publication)
2. **Décider** sur la recherche géographique et `/pour-les-pros` (MVP ou post-MVP) — mettre à jour epics + UX spec en conséquence
3. **Lancer le sprint planning** avec `/bmad-bmm-sprint-planning` — Bob (Scrum Master) génère le plan de sprint séquentiel

---

### Final Note

Cette évaluation a identifié **5 issues** réparties en **3 catégories** :
- 2 issues critiques (OAuth2 multi-providers, publication vitrine) → à corriger avant sprint planning
- 1 issue majeure (sécurité iCal) → à noter pour roadmap
- 2 concerns mineurs (recherche géo, page pro) → décision de scope

La couverture FR est à **100%** (56/56). L'architecture est solide. Les epics sont bien structurés. **Le projet peut démarrer l'implémentation après résolution des 2 critiques.**

---

**Rapport généré :** `_bmad-output/planning-artifacts/implementation-readiness-report-2026-03-05.md`
**Assesseur :** Winston (Architect / PM & Scrum Master)
**Date :** 2026-03-05
