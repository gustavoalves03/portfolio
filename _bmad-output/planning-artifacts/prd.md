---
stepsCompleted: ['step-01-init', 'step-02-discovery', 'step-02b-vision', 'step-02c-executive-summary', 'step-03-success', 'step-04-journeys', 'step-05-domain-skipped', 'step-06-innovation', 'step-07-project-type', 'step-08-scoping', 'step-09-functional']
vision:
  statement: "Donner aux professionnels de la beauté indépendants les outils pro qu'ils méritent, sans le prix pro qui les étouffe."
  keyInsight: "Le moment où un pro de la beauté se lance seul, c'est le moment où il a le PLUS besoin d'outils — et le MOINS de moyens pour les payer."
  differentiatorNow: "Prix accessible (gratuit/abordable) pour ceux qui démarrent"
  differentiatorLater: "Réseau social beauté — communauté, visibilité organique, interactions"
  userDelight:
    - "Réservation 24h/24 = liberté (plus esclave du téléphone)"
    - "Site professionnel = crédibilité immédiate"
    - "Créations partagées = visibilité et acquisition"
    - "Statistiques = piloter son business, voir sa progression"
  whyNow: "Sœur vient de lancer son propre établissement indépendant — besoin de ses propres outils"
classification:
  projectType: 'SaaS B2B + Web App'
  domain: 'Services / Beauté'
  complexity: 'low-medium'
  projectContext: 'brownfield'
  multiTenant: true
  permissionModel: 'Propriétaire salon / Employés / Clients'
  subscriptionModel: 'Post to Play (gratuit) + Premium (payant)'
  techStack: 'Angular 20 + Spring Boot 3.5 + Oracle'
inputDocuments:
  - path: '_bmad-output/brainstorming/brainstorming-session-2026-02-20.md'
    type: 'brainstorming'
    description: 'Session complète avec 42 idées, 5 thèmes, roadmap 18 mois'
workflowType: 'prd'
projectType: 'brownfield'
existingCode:
  location: 'portfolio repo'
  currentApp: 'FleurDeCoquillage'
  features: ['prestations', 'catégories', 'booking partiel']
  evolution: 'FleurDeCoquillage → Pretty Face (SaaS multi-tenant)'
userContext:
  pilotUser: 'Sœur de Gustavo (esthéticienne, début activité)'
  currentProcess: 'Instagram + téléphone'
  painPoints: ['jongler entre plateformes', 'manque de professionnalisme perçu']
  keyNeed: 'Site web = crédibilité + visibilité prestations'
documentCounts:
  briefs: 0
  research: 0
  brainstorming: 1
  projectDocs: 0
---

# Product Requirements Document - Pretty Face

**Author:** Gustavo.alves
**Date:** 2026-02-23

## Executive Summary

Pretty Face est une plateforme SaaS multi-tenant qui permet aux professionnels de la beauté indépendants de créer leur vitrine en ligne, gérer leurs prestations et recevoir des réservations 24h/24. Le produit cible principalement les esthéticiennes, coiffeuses et autres pros de la beauté qui lancent leur propre établissement et ont besoin d'outils professionnels sans le coût prohibitif des solutions existantes comme SalonKey.

**Problème résolu :** Les professionnels qui se lancent seuls jonglent entre Instagram, téléphone et bouche-à-oreille. Ils manquent de crédibilité (pas de site web), de visibilité (dépendance aux réseaux sociaux), et de contrôle sur leur activité (pas de statistiques, pas de vue d'ensemble).

**Utilisateur pilote :** Une esthéticienne qui vient de lancer son propre institut après avoir travaillé en partenariat. Elle représente le moment charnière où le besoin d'outils propres est maximal et les moyens financiers sont limités.

### What Makes This Special

**Aujourd'hui (MVP) :**
- **Accessibilité** — Gratuit ou très abordable, là où SalonKey est perçu comme cher
- **Crédibilité immédiate** — Un site professionnel en quelques minutes
- **Liberté** — Réservations 24h/24, plus besoin de gérer les appels et DMs
- **Contrôle** — Statistiques sur les prestations et revenus pour piloter son business

**Demain (Différenciation) :**
- **Réseau social de la beauté** — Les pros postent leurs créations, les clients découvrent et interagissent
- **Visibilité méritée** — "Sur Pretty Face, la qualité te rend visible, pas ton portefeuille"
- **Communauté** — Acquisition organique de nouvelles clientes via le feed et les partages

**Insight clé :** Le moment où un pro de la beauté se lance seul, c'est le moment où il a le PLUS besoin d'outils — et le MOINS de moyens pour les payer. Pretty Face résout ce paradoxe.

## Project Classification

| Dimension | Valeur |
|-----------|--------|
| **Type de projet** | SaaS B2B + Web App |
| **Domaine** | Services / Beauté |
| **Complexité** | Basse à moyenne |
| **Contexte** | Brownfield (évolution FleurDeCoquillage → Pretty Face) |
| **Multi-tenant** | Oui (chaque salon = un tenant) |
| **Modèle permissions** | Propriétaire / Employés / Clients |
| **Stack technique** | Angular 20 + Spring Boot 3.5 + Oracle |

## Success Criteria

### User Success

**Moment clé de succès :** L'utilisateur consulte son dashboard et voit sa progression — nombre de réservations, CA cumulé, évolution mois par mois. Ce n'est pas juste un outil, c'est une **preuve tangible qu'il construit quelque chose**.

- Le pro passe de "je travaille" → "je pilote mon business"
- Sentiment de progression et d'accomplissement professionnel
- Crédibilité renforcée grâce à un site professionnel

### Business Success

**Objectifs à 6 mois :**
- Application stable, sans bugs critiques
- 5-10 professionnels engagés (au-delà de l'utilisateur pilote)
- Utilisation quotidienne réelle (pas juste des inscriptions)
- Les utilisateurs reviennent parce que ça leur change la vie

### Technical Success

- Disponibilité haute (pas de downtime pendant les heures de réservation)
- Performance fluide sur mobile et desktop
- Données sécurisées (réservations, infos clients)
- Multi-tenant fonctionnel (chaque salon isolé)

### Measurable Outcomes

| Métrique | Cible (6 mois) |
|----------|----------------|
| Professionnels actifs | 5-10 |
| Utilisation quotidienne | Consultation réservations chaque jour |
| Stabilité | Zéro bug critique en production |
| Adoption réservation en ligne | >50% des clients du pro réservent via Pretty Face |

## Product Scope

### MVP - Minimum Viable Product

| Fonctionnalité | Description |
|----------------|-------------|
| Vitrine pro | Prestations, catégories, prix, photos |
| Réservation en ligne | Disponible 24h/24, choix de créneau |
| Dashboard stats | CA, nombre de réservations, progression |
| Notifications | Confirmation réservation (pro + client) |
| Multi-tenant | Chaque salon = son propre espace |

### Growth Features (Post-MVP)

| Fonctionnalité | Description |
|----------------|-------------|
| Post to Play | Obligation de poster pour rester gratuit |
| Feed créations | Découverte des réalisations locales |
| Stats avancées | Analyses détaillées, tendances |
| Gestion employés | Multi-utilisateurs par salon |

### Vision (Future)

| Fonctionnalité | Description |
|----------------|-------------|
| Réseau social beauté | Feed infini, interactions, communauté |
| Paiements intégrés | Payer directement dans l'app |
| Journal beauté client | Historique photos, évolution |
| Marketplace | Vente de produits beauté |

## User Journeys

### Journey 1: Sophie — Pro Indépendante (Happy Path)

> **Sophie**, esthéticienne de 28 ans, vient d'ouvrir son propre institut après 3 ans en partenariat. Elle gère tout seule : prestations, clients, compta. Aujourd'hui, elle jongle entre Instagram, WhatsApp et un carnet pour ses RDV. Elle passe 30 minutes par jour à répondre "Tu es dispo quand ?" alors qu'elle pourrait faire des soins.

**Son parcours avec Pretty Face :**

1. **Inscription (5 min)** — Crée son compte, entre le nom de son institut, upload son logo
2. **Configuration vitrine (15 min)** — Ajoute ses prestations (soin visage 45€/1h, épilation 25€/30min...), configure ses horaires de disponibilité
3. **Partage** — Met le lien Pretty Face sur son Instagram : "Réservez directement ici !"
4. **Quotidien** — Chaque matin, ouvre l'app, voit ses RDV du jour déjà confirmés. Zéro message à envoyer.
5. **Moment "wow"** — Après 1 mois, elle consulte son dashboard : "32 réservations ce mois, +15% vs mois dernier". Elle réalise qu'elle construit quelque chose.

**Transformation :** De "je réponds aux messages" → "je consulte mon planning et mes stats"

---

### Journey 2: Clara — Cliente qui Réserve (Happy Path)

> **Clara**, 32 ans, assistante RH. Sa collègue lui montre ses ongles magnifiques : "C'est Sophie qui m'a fait ça". Clara veut réserver.

**Son parcours avec Pretty Face :**

1. **Découverte** — Sa collègue partage le lien Pretty Face de Sophie
2. **Consultation** — Clara ouvre la vitrine : photos des réalisations, liste des prestations avec prix et durées
3. **Réservation (30 sec)** — Clique "Réserver", voit les créneaux disponibles, sélectionne jeudi 14h
4. **Confirmation** — Reçoit un email/SMS de confirmation immédiat
5. **Rappel** — Notification la veille : "RDV demain 14h avec Sophie"

**Transformation :** De "j'envoie un DM et j'attends 3h" → "je réserve en 30 secondes depuis mon lit à 23h"

---

### Journey 3: Clara — Annulation / Report (Edge Case)

> **Clara** a réservé jeudi 14h. Mercredi soir, sa fille tombe malade. Elle doit annuler.

**Son parcours :**

1. **Accès** — Ouvre l'app ou clique sur le lien dans son email de confirmation
2. **Action** — Voit son RDV → clique "Modifier" ou "Annuler"
3. **Option Annuler** — Confirme l'annulation → Sophie est notifiée, créneau libéré pour d'autres clientes
4. **Option Reporter** — Voit les créneaux dispos → choisit vendredi 10h → nouvelle confirmation envoyée

**Bénéfice :** Sophie n'a pas eu à échanger un seul message. Le créneau est immédiatement disponible pour une autre cliente.

---

### Journey 4: Sophie — Gérer un Imprévu (Edge Case)

> **Sophie** a un RDV médical mardi après-midi, ou se réveille malade un matin.

**Parcours "Bloquer à l'avance" :**

1. Ouvre l'app → "Mon planning"
2. Sélectionne mardi 14h-17h → "Bloquer ce créneau"
3. Ces heures disparaissent des créneaux visibles par les clientes
4. Si un RDV existait déjà → l'app propose de notifier la cliente

**Parcours "Imprévu le jour même" :**

1. Sophie se réveille malade, ouvre l'app
2. Voit ses 3 RDV du jour → clique "Annuler ma journée"
3. Les 3 clientes reçoivent une notification automatique
4. Chaque cliente peut choisir un nouveau créneau directement

**Bénéfice :** L'app ne prévient pas les imprévus, mais réduit le travail manuel quand ils arrivent.

---

### Journey 5: Gustavo — Admin Plateforme

> **Gustavo**, développeur et créateur de Pretty Face. Il surveille la santé de la plateforme et aide les pros en difficulté.

**Son parcours quotidien :**

1. **Dashboard admin** — Vue d'ensemble : pros inscrits/actifs, clients, réservations, erreurs
2. **Monitoring** — Identifie les pros inactifs, peut les recontacter
3. **Support** — Consulte les demandes d'aide, accède aux comptes pros pour diagnostiquer
4. **Action** — Répond aux tickets, corrige les problèmes

**Métriques clés :**
- Pros inscrits / actifs
- Clients inscrits
- Réservations (jour/semaine/mois)
- Erreurs / bugs signalés
- Demandes d'aide en attente

---

### Journey Requirements Summary

| Parcours | Fonctionnalités révélées |
|----------|-------------------------|
| Sophie (inscription) | Onboarding rapide, configuration vitrine, gestion horaires |
| Sophie (quotidien) | Dashboard réservations, dashboard stats/progression |
| Clara (réservation) | Vitrine publique, calendrier disponibilités, réservation en ligne, confirmations |
| Clara (annulation) | Gestion RDV client, notifications automatiques |
| Sophie (imprévus) | Blocage créneaux, annulation groupée, notifications clientes |
| Gustavo (admin) | Dashboard admin, métriques plateforme, support/tickets |

## Innovation & Novel Patterns

### Detected Innovation Areas

**1. Post to Play — Modèle économique innovant**

Concept : Les salons accèdent gratuitement à la plateforme en échange de contenu régulier. Pas d'abonnement classique, mais une "monnaie de contenu".

| Règle | Description |
|-------|-------------|
| Minimum | 3 posts/semaine pour rester actif et visible |
| Maximum | 3 posts/jour sur le feed principal (anti-spam) |
| Page salon | Pas de limite — le client voit toutes les créations |

**Pourquoi c'est innovant :**
- Résout le problème poule/œuf (feed rempli dès le lancement)
- Aligne les intérêts : salon veut visibilité, plateforme veut contenu
- Différent du modèle SaaS classique (abonnement mensuel)

**2. Feed à deux niveaux**

| Niveau | Contenu | Limite |
|--------|---------|--------|
| **Feed principal** | Posts de tous les salons (découverte) | Max 3/jour par salon |
| **Page salon** | Galerie complète du salon | Illimitée |

### Validation Approach

- Tester avec 5-10 salons pilotes le modèle Post to Play
- Mesurer : taux de posting, engagement, satisfaction
- Ajuster les seuils (3/semaine, 3/jour) selon les données réelles

### Risk Mitigation

| Risque | Mitigation |
|--------|-----------|
| Salons ne postent pas assez | Rappels automatiques, gamification |
| Un salon spam quand même | Limite technique de 3/jour sur feed |
| Contenu de mauvaise qualité | Modération, signalement, guidelines |

## SaaS B2B Specific Requirements

### Project-Type Overview

Pretty Face est un SaaS B2B multi-tenant destiné aux professionnels de la beauté. Chaque salon dispose de son propre espace isolé avec ses données, ses clients et ses réservations.

### Multi-Tenancy Architecture

| Aspect | Décision |
|--------|----------|
| **Stratégie d'isolation** | Un schéma Oracle par tenant (salon) |
| **Provisioning** | Script automatique de création de schéma à l'inscription |
| **Routing** | Backend route vers le bon schéma selon le contexte |
| **Migrations** | Appliquées à tous les schémas existants |
| **Avantage** | Isolation forte, backup/restore individuel, scalabilité future |

### Permission Model (RBAC)

**MVP :**

| Rôle | Scope | Permissions |
|------|-------|-------------|
| **Admin plateforme** | Global | Tout voir, gérer tous les salons, support, métriques globales |
| **Pro / Propriétaire** | Son salon | Gérer vitrine, prestations, horaires, voir stats et réservations |
| **Client** | Ses données | Réserver, voir/modifier ses RDV, consulter vitrines publiques |

**Post-MVP :**

| Rôle | Scope | Permissions |
|------|-------|-------------|
| **Employé** | Son salon (limité) | Voir planning, ses RDV assignés, PAS les stats financières |

### Integrations

**MVP :**

| Service | Usage | Priorité |
|---------|-------|----------|
| **Email** (SendGrid/Mailgun) | Confirmations RDV, rappels, notifications | ✅ Critique |
| **Google OAuth** | Connexion client simplifiée | ✅ MVP |
| **Facebook OAuth** | Connexion client simplifiée | ✅ MVP |
| **Apple OAuth** | Connexion client simplifiée | ✅ MVP |
| **Email/Password** | Connexion classique | ✅ MVP |
| **Google Calendar / iCal** | Sync RDV avec agenda personnel | ✅ MVP |

**Post-MVP :**

| Service | Usage | Phase |
|---------|-------|-------|
| **Stripe** | Paiements intégrés | Growth |
| **SMS (Twilio)** | Rappels SMS | Optionnel |

### Technical Architecture Considerations

- **Backend :** Spring Boot 3.5 avec Spring Security OAuth2
- **Frontend :** Angular 20 standalone, SSR-ready
- **Database :** Oracle avec schémas multiples (multi-tenant)
- **Auth :** OAuth2 (Google, Facebook, Apple) + credentials classiques
- **Notifications :** Service email pour confirmations et rappels

### Implementation Considerations

- Le provisioning de nouveaux tenants doit être automatisé (création schéma + données initiales)
- Les migrations de base de données doivent s'appliquer à tous les schémas
- L'OAuth2 est déjà en cours d'implémentation dans le code existant
- La synchronisation calendrier nécessite l'API Google Calendar et le format iCal standard

## Project Scoping & Phased Development

### MVP Strategy & Philosophy

**MVP Approach :** MVP Problème — Résoudre le problème de réservation pour les pros indépendants avant d'ajouter les features "réseau social".

**Ressources :** Développeur solo (Gustavo), utilisateur pilote (sœur esthéticienne).

### MVP Feature Set (Phase 1)

**Parcours utilisateurs supportés :**
- ✅ Sophie — Inscription et configuration vitrine
- ✅ Sophie — Quotidien (voir réservations, stats basiques)
- ✅ Sophie — Gérer imprévus (bloquer créneaux, annuler journée)
- ✅ Clara — Réserver en ligne
- ✅ Clara — Annuler/reporter RDV
- ❌ Gustavo admin — Consulte directement en base (pas de dashboard MVP)

**Fonctionnalités Must-Have :**

| Fonctionnalité | Statut |
|----------------|--------|
| Inscription pro + onboarding | ✅ MVP |
| Configuration vitrine (prestations, catégories, prix, photos) | ✅ MVP |
| Gestion horaires de disponibilité | ✅ MVP |
| Réservation en ligne 24h/24 | ✅ MVP |
| Blocage créneaux + annulation journée | ✅ MVP |
| Dashboard stats basique (CA, réservations, progression) | ✅ MVP |
| Notifications email (confirmation, rappel) | ✅ MVP |
| Auth OAuth2 (Google, Facebook, Apple, email/password) | ✅ MVP |
| Sync calendrier (Google Calendar, iCal) | ✅ MVP |
| Gestion RDV client (voir, annuler, reporter) | ✅ MVP |
| Multi-tenant par schéma Oracle | ✅ MVP |

### Post-MVP Features

**Phase 2 (Growth) :**

| Fonctionnalité | Description |
|----------------|-------------|
| Post to Play | Modèle économique contenu contre accès |
| Feed principal | Découverte des créations locales |
| Galerie posts page salon | Affichage des créations du salon |
| Dashboard admin | Métriques plateforme, support |
| Gestion employés | Multi-utilisateurs par salon |
| Stats avancées | Tendances, analyses détaillées |

**Phase 3 (Expansion) :**

| Fonctionnalité | Description |
|----------------|-------------|
| Paiements Stripe | Paiement intégré dans l'app |
| Réseau social complet | Interactions, communauté |
| Journal beauté client | Historique photos évolution |
| SMS (Twilio) | Rappels SMS |
| Marketplace | Vente de produits beauté |

### Risk Mitigation Strategy

| Type de risque | Risque identifié | Mitigation |
|----------------|------------------|------------|
| **Technique** | Multi-tenant par schéma = complexité | Code existant FleurDeCoquillage comme base, schéma template réutilisable |
| **Technique** | 4 providers OAuth = intégration lourde | Commencer par Google (le plus utilisé), ajouter les autres progressivement |
| **Marché** | Les pros n'adoptent pas | Utilisateur pilote (sœur) pour validation immédiate |
| **Ressources** | Développeur solo | MVP lean, pas de dashboard admin, features post-MVP clairement définies |

## Functional Requirements

### Gestion de Compte & Authentification

- **FR1:** Un professionnel peut créer un compte avec email/mot de passe
- **FR2:** Un professionnel peut se connecter via Google OAuth
- **FR3:** Un professionnel peut se connecter via Facebook OAuth
- **FR4:** Un professionnel peut se connecter via Apple OAuth
- **FR5:** Un client peut créer un compte avec email/mot de passe
- **FR6:** Un client peut se connecter via Google OAuth
- **FR7:** Un client peut se connecter via Facebook OAuth
- **FR8:** Un client peut se connecter via Apple OAuth
- **FR9:** Un utilisateur peut réinitialiser son mot de passe par email
- **FR10:** Un utilisateur peut se déconnecter de son compte

### Configuration Vitrine (Pro)

- **FR11:** Un professionnel peut configurer les informations de son établissement (nom, logo, description)
- **FR12:** Un professionnel peut créer des catégories de prestations
- **FR13:** Un professionnel peut créer des prestations avec nom, description, prix et durée
- **FR14:** Un professionnel peut associer une prestation à une catégorie
- **FR15:** Un professionnel peut ajouter des photos à une prestation
- **FR16:** Un professionnel peut modifier une prestation existante
- **FR17:** Un professionnel peut supprimer une prestation
- **FR18:** Un professionnel peut réorganiser l'ordre d'affichage des prestations
- **FR19:** Un professionnel peut activer/désactiver une prestation (sans la supprimer)

### Gestion des Disponibilités (Pro)

- **FR20:** Un professionnel peut définir ses horaires d'ouverture par jour de la semaine
- **FR21:** Un professionnel peut bloquer un créneau spécifique (indisponibilité ponctuelle)
- **FR22:** Un professionnel peut débloquer un créneau précédemment bloqué
- **FR23:** Un professionnel peut annuler tous les RDV d'une journée (maladie, imprévu)
- **FR24:** Le système notifie automatiquement les clients affectés par une annulation groupée

### Vitrine Publique (Client)

- **FR25:** Un visiteur peut consulter la vitrine d'un salon sans être connecté
- **FR26:** Un visiteur peut voir la liste des prestations avec prix et durées
- **FR27:** Un visiteur peut voir les photos associées aux prestations
- **FR28:** Un visiteur peut voir les informations de l'établissement (nom, description)

### Réservation (Client)

- **FR29:** Un client peut voir les créneaux disponibles pour une prestation donnée
- **FR30:** Un client peut sélectionner un créneau et confirmer sa réservation
- **FR31:** Un client doit être connecté pour finaliser une réservation
- **FR32:** Le système envoie une confirmation de réservation par email au client
- **FR33:** Le système envoie une notification de nouvelle réservation au professionnel
- **FR34:** Le système envoie un rappel par email au client la veille du RDV

### Gestion des RDV (Client)

- **FR35:** Un client peut voir la liste de ses RDV à venir
- **FR36:** Un client peut voir la liste de ses RDV passés
- **FR37:** Un client peut annuler un RDV à venir
- **FR38:** Un client peut reporter un RDV vers un autre créneau disponible
- **FR39:** Le système notifie le professionnel lors d'une annulation ou d'un report

### Gestion des RDV (Pro)

- **FR40:** Un professionnel peut voir la liste de ses RDV du jour
- **FR41:** Un professionnel peut voir son planning sur une vue calendrier (semaine/mois)
- **FR42:** Un professionnel peut voir les détails d'un RDV (client, prestation, heure)
- **FR43:** Un professionnel peut annuler un RDV existant
- **FR44:** Le système notifie automatiquement le client en cas d'annulation par le pro

### Synchronisation Calendrier

- **FR45:** Un professionnel peut connecter son compte Google Calendar
- **FR46:** Un professionnel peut exporter ses RDV au format iCal
- **FR47:** Les nouveaux RDV sont automatiquement synchronisés avec le calendrier connecté

### Dashboard & Statistiques (Pro)

- **FR48:** Un professionnel peut voir son chiffre d'affaires du mois en cours
- **FR49:** Un professionnel peut voir le nombre de réservations du mois en cours
- **FR50:** Un professionnel peut voir l'évolution de son CA mois par mois
- **FR51:** Un professionnel peut voir l'évolution de ses réservations mois par mois
- **FR52:** Un professionnel peut voir un indicateur de progression par rapport au mois précédent

### Multi-Tenancy

- **FR53:** Le système isole les données de chaque salon dans un schéma séparé
- **FR54:** Un professionnel ne peut accéder qu'aux données de son propre salon
- **FR55:** Un client peut réserver auprès de plusieurs salons différents
- **FR56:** Le système crée automatiquement un nouveau schéma lors de l'inscription d'un salon
