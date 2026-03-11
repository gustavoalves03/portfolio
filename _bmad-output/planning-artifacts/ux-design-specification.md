---
stepsCompleted: [1, 2]
inputDocuments:
  - path: '_bmad-output/planning-artifacts/prd.md'
    type: 'prd'
    description: 'PRD complet Pretty Face — SaaS beauté multi-tenant, 56 FRs, 11 NFRs'
  - path: '_bmad-output/planning-artifacts/prd-validation-report.md'
    type: 'validation'
    description: 'Rapport de validation du PRD'
---

# UX Design Specification - Pretty Face

**Author:** Gustavo.alves
**Date:** 2026-03-05

---

<!-- UX design content will be appended sequentially through collaborative workflow steps -->

## Executive Summary

### Project Vision

Pretty Face est un SaaS multi-tenant qui donne aux professionnels de la beauté indépendants une vitrine en ligne professionnelle et un système de réservation 24h/24 — sans le coût prohibitif des solutions existantes. Le produit évolue à partir du code existant FleurDeCoquillage (Angular 20 + Spring Boot + Oracle), qui constitue une base technique solide à faire évoluer plutôt qu'une contrainte.

**Insight clé :** Le moment où un pro de la beauté se lance seul, c'est le moment où il a le plus besoin d'outils — et le moins de moyens pour les payer. Pretty Face résout ce paradoxe.

### Target Users

| Persona | Profil | Besoin principal |
|---------|--------|-----------------|
| **Sophie** (Pro) | Esthéticienne indépendante, gère tout seule via Instagram + carnet | Vitrine pro + réservations automatiques → plus de temps pour les soins |
| **Clara** (Cliente) | 32 ans, découvre les salons et réserve sur recommandation | Trouver un salon par type de soin, réserver en 30 sec depuis mobile |
| **Gustavo** (Admin) | Développeur/créateur de la plateforme | Surveiller la santé de la plateforme, supporter les pros |

### Information Architecture & Navigation

**Principe fondateur : Home client-first, une seule entrée pour tous.**

La home est partagée entre clients et pros. Les pros accèdent à leur espace via connexion depuis cette même page. Pas de deux sites séparés — une seule identité Pretty Face, deux espaces post-connexion.

**Structure de navigation :**
- `/` → Home publique (vitrine Pretty Face + découverte salons)
- `/[salon-slug]/` → Vitrine publique d'un salon spécifique
- `/login` → Connexion (clients et pros)
- `/pro/...` → Espace backoffice pro (route gardée)
- `/pour-les-pros` → Landing page dédiée au pitch B2B (séparée)

**Home publique — structure en scroll :**
1. **Hero** — "La beauté près de chez toi" + barre de recherche/localisation
2. **Catégories de soins** — illustrées, cliquables (Soin visage · Ongles · Épilation · Coiffure…)
3. **Salons en vedette** — feed des créations récentes, "Découvre les artistes près de toi"
4. **CTA pro discret** — "Tu es pro ? Crée ta vitrine gratuitement →" (bas de page)

**Séparation des messages :** La home ne mélange pas l'audience client et l'audience pro. Le pitch B2B a sa propre page `/pour-les-pros`, accessible via le CTA discret. Les pros arrivent via bouche-à-oreille ou liens directs — pas via le feed client.

### Key Design Challenges

1. **Double audience, interface unifiée** — Home partagée avec espaces radicalement différents post-connexion : backoffice de gestion (Sophie) vs vitrine publique de réservation (Clara). Deux émotions, deux "feelings" à concevoir.

2. **Onboarding Pro critique** — Sophie doit être opérationnelle en moins de 20 minutes. La vitrine reste en "draft" (privée) jusqu'à une configuration minimale — protège la crédibilité du pro dès le départ. Mode "setup" vs mode "opérationnel" à distinguer clairement.

3. **Mobile-first pour Clara** — Réservation depuis mobile à 23h. Vitrine publique et tunnel de réservation doivent être impeccables sur petit écran. Performance P95 < 2s sur 4G (NFR3).

4. **Clarté des statuts** — Bookings (PENDING/CONFIRMED/CANCELLED), cares (ACTIVE/INACTIVE), Post to Play (actif/suspendu). Sophie doit comprendre l'état de son activité d'un coup d'œil.

### Design Opportunities

1. **Dashboard "moment wow"** — Après 1 mois, Sophie voit "+15% de réservations". Ce moment doit être visuellement fort, presque émotionnel — une preuve tangible qu'elle construit quelque chose, pas juste un tableau de chiffres.

2. **Vitrine publique distincte** — La page salon (ce que voit Clara) peut avoir une esthétique "beauté & élégance" différente du backoffice. Opportunité de différenciation forte.

3. **Catégories comme portes d'entrée** — Les catégories de soins illustrées sur la home sont le premier point de contact de Clara avec l'écosystème. Bien conçues, elles créent un parcours de découverte naturel et engageant vers les salons.
